package com.soumik.stark.core.util

import com.soumik.stark.data.entity.Confidence
import com.soumik.stark.data.entity.Point
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * Compact point packing (design §4B, §5): scaled-int coords → delta between consecutive → zigzag
 * → varint, one blob per leg (same idea as an encoded polyline). Lossless and ~3–5× smaller than
 * rows. Time is delta-encoded too; speed/accuracy are quantised losslessly (0.01 m/s, 0.1 m).
 */
object PointCodec {

    fun encode(points: List<Point>): ByteArray {
        val out = ByteArrayOutputStream()
        writeVarLong(out, points.size.toLong())
        var lat = 0L; var lng = 0L; var t = 0L; var off = 0L
        for (p in points) {
            writeZig(out, p.latE7 - lat); lat = p.latE7.toLong()
            writeZig(out, p.lngE7 - lng); lng = p.lngE7.toLong()
            writeZig(out, p.tUtc - t); t = p.tUtc
            writeZig(out, p.offsetMin - off); off = p.offsetMin.toLong()
            writeVarLong(out, Math.round(p.speedMps * 100.0))
            writeVarLong(out, Math.round(p.accuracyM * 10.0))
            out.write(p.confidence.ordinal)
        }
        return out.toByteArray()
    }

    fun decode(bytes: ByteArray, legId: Long): List<Point> {
        val inp = ByteArrayInputStream(bytes)
        val n = readVarLong(inp).toInt()
        val list = ArrayList<Point>(n)
        var lat = 0L; var lng = 0L; var t = 0L; var off = 0L
        repeat(n) {
            lat += readZig(inp); lng += readZig(inp); t += readZig(inp); off += readZig(inp)
            val speed = readVarLong(inp) / 100.0
            val acc = readVarLong(inp) / 10.0
            val conf = Confidence.entries[inp.read()]
            list.add(
                Point(
                    legId = legId, tUtc = t, offsetMin = off.toInt(),
                    latE7 = lat.toInt(), lngE7 = lng.toInt(),
                    accuracyM = acc.toFloat(), speedMps = speed.toFloat(), confidence = conf,
                )
            )
        }
        return list
    }

    private fun writeZig(o: ByteArrayOutputStream, v: Long) = writeVarLong(o, (v shl 1) xor (v shr 63))
    private fun readZig(i: ByteArrayInputStream): Long { val u = readVarLong(i); return (u ushr 1) xor -(u and 1) }

    private fun writeVarLong(o: ByteArrayOutputStream, value: Long) {
        var v = value
        while (true) {
            val b = (v and 0x7F).toInt()
            v = v ushr 7
            if (v == 0L) { o.write(b); return } else o.write(b or 0x80)
        }
    }

    private fun readVarLong(i: ByteArrayInputStream): Long {
        var result = 0L; var shift = 0
        while (true) {
            val b = i.read()
            if (b < 0) return result
            result = result or ((b.toLong() and 0x7F) shl shift)
            if (b and 0x80 == 0) return result
            shift += 7
        }
    }
}
