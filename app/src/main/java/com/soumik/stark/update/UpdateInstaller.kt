package com.soumik.stark.update

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** Downloads a release APK, verifies its checksum, and launches the system installer (design §7). */
class UpdateInstaller(private val context: Context) {

    sealed interface Result {
        data class Ready(val file: File) : Result
        data class Failed(val reason: String) : Result
    }

    fun downloadAndVerify(apkUrl: String, expectedSha256: String?): Result {
        return try {
            val out = File(context.cacheDir, "stark-update.apk")
            val conn = (URL(apkUrl).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                connectTimeout = 15000
                readTimeout = 30000
            }
            conn.inputStream.use { input -> out.outputStream().use { input.copyTo(it) } }
            conn.disconnect()

            if (expectedSha256 != null) {
                val actual = sha256(out)
                if (!actual.equals(expectedSha256, ignoreCase = true)) {
                    out.delete()
                    return Result.Failed("Checksum mismatch — refusing to install")
                }
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

    private fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { s ->
            val buf = ByteArray(8192)
            while (true) { val n = s.read(buf); if (n < 0) break; md.update(buf, 0, n) }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }
}
