package com.soumik.stark.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.content.FileProvider
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Downloads a release APK and verifies it three ways before install (design §7):
 * (a) SHA-256 checksum from the signed release notes, (b) the APK's signing certificate matches
 * the installed app's, (c) monotonic version (the caller refuses downgrades). Only on all-pass
 * does it hand off to the system installer.
 */
class UpdateInstaller(private val context: Context) {

    sealed interface Result {
        data class Ready(val file: File) : Result
        data class Failed(val reason: String) : Result
    }

    fun downloadAndVerify(apkUrl: String, expectedSha256: String?, onProgress: (Int) -> Unit = {}): Result {
        return try {
            val out = File(context.cacheDir, "stark-update.apk")
            val conn = (URL(apkUrl).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 15000
                readTimeout = 30000
            }
            val total = conn.contentLengthLong
            var read = 0L
            conn.inputStream.use { input ->
                out.outputStream().use { o ->
                    val buf = ByteArray(16384)
                    while (true) {
                        val n = input.read(buf); if (n < 0) break
                        o.write(buf, 0, n); read += n
                        if (total > 0) onProgress(((read * 100) / total).toInt().coerceIn(0, 100))
                    }
                }
            }
            conn.disconnect()

            if (expectedSha256 != null) {
                val actual = sha256(out)
                if (!actual.equals(expectedSha256, ignoreCase = true)) {
                    out.delete(); return Result.Failed("Checksum mismatch — refusing to install")
                }
            }
            if (!signatureMatchesInstalled(out)) {
                out.delete(); return Result.Failed("Signature mismatch — refusing to install")
            }
            Result.Ready(out)
        } catch (e: Exception) {
            Result.Failed(e.message ?: "download failed")
        }
    }

    fun launchInstaller(file: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }

    /** True only if the downloaded APK is signed by the same certificate as the installed app. */
    private fun signatureMatchesInstalled(apk: File): Boolean {
        val pm = context.packageManager
        val flag = PackageManager.GET_SIGNING_CERTIFICATES
        val installed = pm.getPackageInfo(context.packageName, flag).signingInfo ?: return false
        val downloaded = pm.getPackageArchiveInfo(apk.absolutePath, flag)?.signingInfo ?: return false
        val a = installed.apkContentsSigners.map { sha256(it.toByteArray()) }.toSet()
        val b = downloaded.apkContentsSigners.map { sha256(it.toByteArray()) }.toSet()
        return a.isNotEmpty() && a.intersect(b).isNotEmpty()
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { s ->
            val buf = ByteArray(8192)
            while (true) { val n = s.read(buf); if (n < 0) break; md.update(buf, 0, n) }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
