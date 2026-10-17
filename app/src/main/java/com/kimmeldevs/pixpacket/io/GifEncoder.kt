package com.kimmeldevs.pixpacket.io

import java.io.ByteArrayOutputStream
import java.io.OutputStream

/**
 * Minimal animated GIF89a writer. Frames are ARGB arrays; pixels with alpha < 128 become transparent.
 * Uses a global palette; if the sprite has more than 255 colors they are reduced by bit-truncation.
 */
object GifEncoder {

    fun encode(out: OutputStream, frames: List<IntArray>, w: Int, h: Int, delayMs: Int, scale: Int = 1) {
        val sw = w * scale; val sh = h * scale
        val scaled = frames.map { if (scale == 1) it else upscale(it, w, h, scale) }

        // Build palette (index 0 = transparent).
        var shift = 0
        var palette: List<Int>
        while (true) {
            val set = LinkedHashSet<Int>()
            for (f in scaled) for (p in f) if ((p ushr 24) >= 128) set.add(reduce(p, shift))
            if (set.size <= 255 || shift >= 7) { palette = set.toList().take(255); break }
            shift++
        }
        val index = HashMap<Int, Int>(palette.size * 2)
        palette.forEachIndexed { i, c -> index[c] = i + 1 }
        var bits = 1
        while ((1 shl bits) < palette.size + 1) bits++
        val tableSize = 1 shl bits

        out.write("GIF89a".toByteArray())
        writeShort(out, sw); writeShort(out, sh)
        out.write(0x80 or ((bits - 1) shl 4) or (bits - 1)) // global color table
        out.write(0); out.write(0)
        for (i in 0 until tableSize) {
            val c = if (i in 1..palette.size) palette[i - 1] else 0
            out.write((c shr 16) and 0xFF); out.write((c shr 8) and 0xFF); out.write(c and 0xFF)
        }
        // Loop forever (NETSCAPE2.0)
        out.write(byteArrayOf(0x21, 0xFF.toByte(), 0x0B))
        out.write("NETSCAPE2.0".toByteArray())
        out.write(byteArrayOf(3, 1, 0, 0, 0))

        val delay = (delayMs / 10).coerceAtLeast(2)
        for (f in scaled) {
            out.write(byteArrayOf(0x21, 0xF9.toByte(), 4, 0x09)) // dispose=restore bg, transparent flag
            writeShort(out, delay)
            out.write(0); out.write(0)
            out.write(0x2C); writeShort(out, 0); writeShort(out, 0); writeShort(out, sw); writeShort(out, sh); out.write(0)
            val px = ByteArray(f.size) { i ->
                val p = f[i]
                (if ((p ushr 24) < 128) 0 else index[reduce(p, shift)] ?: 0).toByte()
            }
            lzw(out, px, maxOf(2, bits))
        }
        out.write(0x3B)
    }

    private fun reduce(c: Int, shift: Int): Int {
        if (shift == 0) return c and 0xFFFFFF
        val m = (0xFF shl shift) and 0xFF
        return (((c shr 16) and m) shl 16) or (((c shr 8) and m) shl 8) or (c and m)
    }

    private fun upscale(src: IntArray, w: Int, h: Int, s: Int): IntArray {
        val nw = w * s
        return IntArray(nw * h * s) { i -> src[(i / nw / s) * w + (i % nw) / s] }
    }

    private fun writeShort(out: OutputStream, v: Int) { out.write(v and 0xFF); out.write((v shr 8) and 0xFF) }

    private fun lzw(out: OutputStream, data: ByteArray, minCodeSize: Int) {
        out.write(minCodeSize)
        val clear = 1 shl minCodeSize
        val eoi = clear + 1
        val buf = ByteArrayOutputStream()
        var bitBuf = 0; var bitCount = 0
        var codeSize = minCodeSize + 1
        fun emit(code: Int) {
            bitBuf = bitBuf or (code shl bitCount); bitCount += codeSize
            while (bitCount >= 8) { buf.write(bitBuf and 0xFF); bitBuf = bitBuf ushr 8; bitCount -= 8 }
        }
        val dict = HashMap<Int, Int>()
        var next = eoi + 1
        emit(clear)
        var prefix = -1
        for (b in data) {
            val k = b.toInt() and 0xFF
            if (prefix == -1) { prefix = k; continue }
            val key = (prefix shl 8) or k
            val found = dict[key]
            if (found != null) { prefix = found; continue }
            emit(prefix)
            if (next < 4096) {
                dict[key] = next++
                if (next > (1 shl codeSize) && codeSize < 12) codeSize++
            } else {
                emit(clear); dict.clear(); next = eoi + 1; codeSize = minCodeSize + 1
            }
            prefix = k
        }
        if (prefix != -1) emit(prefix)
        emit(eoi)
        if (bitCount > 0) buf.write(bitBuf and 0xFF)
        val bytes = buf.toByteArray()
        var i = 0
        while (i < bytes.size) {
            val n = minOf(255, bytes.size - i)
            out.write(n); out.write(bytes, i, n); i += n
        }
        out.write(0)
    }
}
