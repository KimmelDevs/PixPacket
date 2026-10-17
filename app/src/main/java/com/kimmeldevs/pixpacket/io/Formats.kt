package com.kimmeldevs.pixpacket.io

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import com.kimmeldevs.pixpacket.model.Layer
import com.kimmeldevs.pixpacket.model.Sprite
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.zip.Deflater
import java.util.zip.Inflater
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

private fun bitmapPixels(b: Bitmap): IntArray = IntArray(b.width * b.height).also { b.getPixels(it, 0, b.width, 0, 0, b.width, b.height) }

private fun pngBytes(px: IntArray, w: Int, h: Int): ByteArray {
    val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    b.setPixels(px, 0, w, 0, 0, w, h)
    return ByteArrayOutputStream().also { b.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
}

/** Normalises fully transparent pixels to 0 so fills and comparisons behave. */
private fun clean(px: IntArray) { for (i in px.indices) if ((px[i] ushr 24) == 0) px[i] = 0 }

// region Piskel (.piskel) — JSON with one base64 PNG strip per layer

object PiskelFormat {
    fun write(out: OutputStream, name: String, s: Sprite) {
        val layers = JSONArray()
        for (l in s.layers) {
            val n = s.frameCount
            val strip = IntArray(s.width * n * s.height)
            for (f in 0 until n) for (y in 0 until s.height) System.arraycopy(l.cels[f], y * s.width, strip, y * s.width * n + f * s.width, s.width)
            val png = Base64.encodeToString(pngBytes(strip, s.width * n, s.height), Base64.NO_WRAP)
            val layout = JSONArray().apply { for (f in 0 until n) put(JSONArray().put(f)) }
            val lj = JSONObject()
                .put("name", l.name).put("opacity", l.opacity.toDouble()).put("frameCount", n)
                .put("chunks", JSONArray().put(JSONObject().put("layout", layout).put("base64PNG", "data:image/png;base64,$png")))
            layers.put(lj.toString())
        }
        val piskel = JSONObject()
            .put("name", name).put("description", "").put("fps", s.fps)
            .put("height", s.height).put("width", s.width).put("layers", layers).put("hiddenFrames", JSONArray())
        out.write(JSONObject().put("modelVersion", 2).put("piskel", piskel).toString().toByteArray())
    }

    fun read(bytes: ByteArray): Pair<String, Sprite> {
        val root = JSONObject(String(bytes))
        val p = root.getJSONObject("piskel")
        val w = p.getInt("width"); val h = p.getInt("height")
        val fps = p.optInt("fps", 12).coerceIn(1, 30)
        val la = p.getJSONArray("layers")
        val layers = mutableListOf<Layer>()
        var frameCount = 0
        for (i in 0 until la.length()) {
            val lj = la.get(i).let { if (it is String) JSONObject(it) else it as JSONObject }
            val n = lj.getInt("frameCount")
            frameCount = maxOf(frameCount, n)
            val cels = MutableList(n) { IntArray(w * h) }
            fun blit(dataUrl: String, layout: List<Pair<Int, Pair<Int, Int>>>) {
                val raw = Base64.decode(dataUrl.substringAfter(","), Base64.DEFAULT)
                val bmp = BitmapFactory.decodeByteArray(raw, 0, raw.size) ?: return
                val px = bitmapPixels(bmp)
                for ((frame, pos) in layout) {
                    if (frame !in 0 until n) continue
                    val (cx, cy) = pos
                    for (y in 0 until h) for (x in 0 until w) {
                        val sx = cx * w + x; val sy = cy * h + y
                        if (sx < bmp.width && sy < bmp.height) cels[frame][y * w + x] = px[sy * bmp.width + sx]
                    }
                }
            }
            if (lj.has("chunks")) {
                val chunks = lj.getJSONArray("chunks")
                for (c in 0 until chunks.length()) {
                    val ch = chunks.getJSONObject(c)
                    val layout = ch.getJSONArray("layout")
                    val list = mutableListOf<Pair<Int, Pair<Int, Int>>>()
                    for (col in 0 until layout.length()) {
                        val column = layout.getJSONArray(col)
                        for (row in 0 until column.length()) list.add(column.getInt(row) to (col to row))
                    }
                    blit(ch.getString("base64PNG"), list)
                }
            } else if (lj.has("base64PNG")) { // modelVersion 1: one horizontal strip
                blit(lj.getString("base64PNG"), List(n) { it to (it to 0) })
            }
            cels.forEach { clean(it) }
            layers.add(Layer(lj.optString("name", "Layer ${i + 1}"), cels, opacity = lj.optDouble("opacity", 1.0).toFloat()))
        }
        // Pad layers to a common frame count.
        for (l in layers) while (l.cels.size < frameCount) l.cels.add(IntArray(w * h))
        if (layers.isEmpty()) layers.add(Layer("Layer 1", mutableListOf(IntArray(w * h))))
        return p.optString("name", "Piskel") to Sprite(w, h, layers, fps)
    }
}

// endregion

// region Aseprite (.ase / .aseprite) — https://github.com/aseprite/aseprite/blob/main/docs/ase-file-specs.md

object AsepriteFormat {
    private const val HEADER_MAGIC = 0xA5E0
    private const val FRAME_MAGIC = 0xF1FA

    fun read(bytes: ByteArray): Pair<String, Sprite> {
        val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        b.int // file size
        require(b.short.toInt() and 0xFFFF == HEADER_MAGIC) { "Not an Aseprite file" }
        val frames = b.short.toInt() and 0xFFFF
        val w = b.short.toInt() and 0xFFFF
        val h = b.short.toInt() and 0xFFFF
        val depth = b.short.toInt() and 0xFFFF
        val flags = b.int
        val speed = b.short.toInt() and 0xFFFF
        b.position(28)
        val transparentIndex = b.get().toInt() and 0xFF
        b.position(128)

        val palette = IntArray(256)
        // Aseprite layer index -> our layer (null for groups/tilemaps)
        val layerMap = mutableListOf<Layer?>()
        val ourLayers = mutableListOf<Layer>()
        val durations = mutableListOf<Int>()
        val celsPerFrame = MutableList(frames) { mutableListOf<Triple<Int, IntArray, IntArray>>() } // (layer, rect[x,y,w,h], pixels)
        val linked = mutableListOf<Triple<Int, Int, Int>>() // frame, layer, linked frame

        fun string(): String { val n = b.short.toInt() and 0xFFFF; val a = ByteArray(n); b.get(a); return String(a) }

        for (f in 0 until frames) {
            val frameStart = b.position()
            val frameBytes = b.int
            require(b.short.toInt() and 0xFFFF == FRAME_MAGIC) { "Corrupt frame" }
            val oldChunks = b.short.toInt() and 0xFFFF
            durations.add(b.short.toInt() and 0xFFFF)
            b.position(b.position() + 2)
            val newChunks = b.int
            val chunks = if (newChunks == 0) oldChunks else newChunks
            repeat(chunks) {
                val cStart = b.position()
                val size = b.int
                val type = b.short.toInt() and 0xFFFF
                when (type) {
                    0x2004 -> { // layer
                        val lflags = b.short.toInt() and 0xFFFF
                        val ltype = b.short.toInt() and 0xFFFF
                        b.short; b.short; b.short; b.short // child level, default w/h, blend
                        val opacity = b.get().toInt() and 0xFF
                        b.position(b.position() + 3)
                        val name = string()
                        if (ltype == 0) {
                            val l = Layer(name, mutableListOf(), visible = lflags and 1 != 0, locked = lflags and 2 == 0,
                                opacity = if (flags and 1 != 0) opacity / 255f else 1f)
                            ourLayers.add(l); layerMap.add(l)
                        } else layerMap.add(null)
                    }
                    0x2005 -> { // cel
                        val li = b.short.toInt() and 0xFFFF
                        val x = b.short.toInt(); val y = b.short.toInt()
                        val opacity = b.get().toInt() and 0xFF
                        val celType = b.short.toInt() and 0xFFFF
                        b.short // z-index
                        b.position(b.position() + 5)
                        when (celType) {
                            0, 2 -> {
                                val cw = b.short.toInt() and 0xFFFF; val chh = b.short.toInt() and 0xFFFF
                                val bpp = depth / 8
                                val raw = ByteArray(cw * chh * bpp)
                                if (celType == 0) b.get(raw) else {
                                    val comp = ByteArray(cStart + size - b.position()); b.get(comp)
                                    Inflater().apply { setInput(comp); inflate(raw); end() }
                                }
                                val px = IntArray(cw * chh) { i ->
                                    when (depth) {
                                        32 -> {
                                            val r = raw[i * 4].toInt() and 0xFF; val g = raw[i * 4 + 1].toInt() and 0xFF
                                            val bl = raw[i * 4 + 2].toInt() and 0xFF; val a = raw[i * 4 + 3].toInt() and 0xFF
                                            val aa = a * opacity / 255
                                            if (aa == 0) 0 else (aa shl 24) or (r shl 16) or (g shl 8) or bl
                                        }
                                        16 -> {
                                            val v = raw[i * 2].toInt() and 0xFF; val a = (raw[i * 2 + 1].toInt() and 0xFF) * opacity / 255
                                            if (a == 0) 0 else (a shl 24) or (v shl 16) or (v shl 8) or v
                                        }
                                        else -> {
                                            val idx = raw[i].toInt() and 0xFF
                                            if (idx == transparentIndex) 0 else palette[idx]
                                        }
                                    }
                                }
                                celsPerFrame[f].add(Triple(li, intArrayOf(x, y, cw, chh), px))
                            }
                            1 -> linked.add(Triple(f, li, b.short.toInt() and 0xFFFF))
                        }
                    }
                    0x2019 -> { // palette
                        b.int
                        val first = b.int; val last = b.int
                        b.position(b.position() + 8)
                        for (i in first..last) {
                            val ef = b.short.toInt() and 0xFFFF
                            val r = b.get().toInt() and 0xFF; val g = b.get().toInt() and 0xFF
                            val bl = b.get().toInt() and 0xFF; val a = b.get().toInt() and 0xFF
                            if (i < 256) palette[i] = (a shl 24) or (r shl 16) or (g shl 8) or bl
                            if (ef and 1 != 0) string()
                        }
                    }
                    0x0004, 0x0011 -> { // old palettes
                        val packets = b.short.toInt() and 0xFFFF
                        var idx = 0
                        repeat(packets) {
                            idx += b.get().toInt() and 0xFF
                            var n = b.get().toInt() and 0xFF
                            if (n == 0) n = 256
                            repeat(n) {
                                val k = if (type == 0x0011) 4 else 1
                                val r = (b.get().toInt() and 0xFF) * k; val g = (b.get().toInt() and 0xFF) * k; val bl = (b.get().toInt() and 0xFF) * k
                                if (idx < 256 && palette[idx] == 0) palette[idx] = (0xFF shl 24) or (minOf(r, 255) shl 16) or (minOf(g, 255) shl 8) or minOf(bl, 255)
                                idx++
                            }
                        }
                    }
                }
                b.position(cStart + size)
            }
            b.position(frameStart + frameBytes)
        }

        if (ourLayers.isEmpty()) ourLayers.add(Layer("Layer 1", mutableListOf())).also { layerMap.add(ourLayers[0]) }
        for (l in ourLayers) repeat(frames) { l.cels.add(IntArray(w * h)) }
        fun place(frame: Int, li: Int, rect: IntArray, px: IntArray) {
            val layer = layerMap.getOrNull(li) ?: return
            val dst = layer.cels[frame]
            for (y in 0 until rect[3]) for (x in 0 until rect[2]) {
                val dx = rect[0] + x; val dy = rect[1] + y
                if (dx in 0 until w && dy in 0 until h) dst[dy * w + dx] = px[y * rect[2] + x]
            }
        }
        for (f in 0 until frames) for ((li, rect, px) in celsPerFrame[f]) place(f, li, rect, px)
        for ((f, li, from) in linked) {
            val layer = layerMap.getOrNull(li) ?: continue
            if (from in 0 until frames) layer.cels[from].copyInto(layer.cels[f])
        }
        val avg = durations.filter { it > 0 }.average().takeIf { !it.isNaN() } ?: speed.toDouble()
        val fps = (1000.0 / avg.coerceAtLeast(1.0)).toInt().coerceIn(1, 30)
        return "Aseprite" to Sprite(w, h, ourLayers, fps)
    }

    fun write(out: OutputStream, s: Sprite) {
        val body = ByteArrayOutputStream()
        val duration = 1000 / s.fps
        for (f in 0 until s.frameCount) {
            val chunks = mutableListOf<ByteArray>()
            if (f == 0) for (l in s.layers) {
                val name = l.name.toByteArray()
                val c = le(6 + 16 + 2 + name.size).apply {
                    int(6 + 16 + 2 + name.size); short(0x2004)
                    short((if (l.visible) 1 else 0) or (if (l.locked) 0 else 2))
                    short(0); short(0); short(0); short(0); short(0)
                    byte((l.opacity * 255).toInt()); byte(0); byte(0); byte(0)
                    short(name.size); bytes(name)
                }
                chunks.add(c.array())
            }
            s.layers.forEachIndexed { li, l ->
                val px = l.cels[f]
                val bounds = bounds(px, s.width, s.height) ?: return@forEachIndexed
                val (bx, by, bw, bh) = bounds
                val raw = ByteArray(bw * bh * 4)
                var k = 0
                for (y in by until by + bh) for (x in bx until bx + bw) {
                    val p = px[y * s.width + x]
                    raw[k++] = (p shr 16).toByte(); raw[k++] = (p shr 8).toByte(); raw[k++] = p.toByte(); raw[k++] = (p ushr 24).toByte()
                }
                val comp = deflate(raw)
                val size = 6 + 16 + 4 + comp.size
                chunks.add(le(size).apply {
                    int(size); short(0x2005)
                    short(li); short(bx); short(by); byte(255); short(2); short(0); bytes(ByteArray(5))
                    short(bw); short(bh); bytes(comp)
                }.array())
            }
            val total = 16 + chunks.sumOf { it.size }
            body.write(le(16).apply {
                int(total); short(FRAME_MAGIC); short(minOf(chunks.size, 0xFFFF)); short(duration); short(0); int(chunks.size)
            }.array())
            chunks.forEach { body.write(it) }
        }
        val header = le(128).apply {
            int(128 + body.size()); short(HEADER_MAGIC); short(s.frameCount); short(s.width); short(s.height)
            short(32); int(1); short(duration); int(0); int(0)
            byte(0); bytes(ByteArray(3)); short(0); byte(1); byte(1)
            short(0); short(0); short(s.grid.tileWidth); short(s.grid.tileHeight)
            bytes(ByteArray(84))
        }
        out.write(header.array())
        body.writeTo(out)
    }

    private fun bounds(px: IntArray, w: Int, h: Int): IntArray? {
        var l = w; var t = h; var r = -1; var b = -1
        for (y in 0 until h) for (x in 0 until w) if (px[y * w + x] != 0) {
            if (x < l) l = x; if (x > r) r = x; if (y < t) t = y; if (y > b) b = y
        }
        return if (r < 0) null else intArrayOf(l, t, r - l + 1, b - t + 1)
    }

    private fun deflate(data: ByteArray): ByteArray {
        val d = Deflater(Deflater.BEST_COMPRESSION).apply { setInput(data); finish() }
        val out = ByteArrayOutputStream()
        val buf = ByteArray(8192)
        while (!d.finished()) out.write(buf, 0, d.deflate(buf))
        d.end()
        return out.toByteArray()
    }

    private class Le(size: Int) {
        private val b = ByteBuffer.allocate(size).order(ByteOrder.LITTLE_ENDIAN)
        fun int(v: Int) { b.putInt(v) }
        fun short(v: Int) { b.putShort(v.toShort()) }
        fun byte(v: Int) { b.put(v.toByte()) }
        fun bytes(a: ByteArray) { b.put(a) }
        fun array(): ByteArray = b.array()
    }

    private fun le(size: Int) = Le(size)
}

// endregion

// region GIF decoding (for importing animations)

class DecodedGif(val width: Int, val height: Int, val frames: List<IntArray>, val delaysMs: List<Int>)

object GifDecoder {
    fun decode(data: ByteArray): DecodedGif {
        var p = 0
        fun u8() = data[p++].toInt() and 0xFF
        fun u16() = u8() or (u8() shl 8)
        require(String(data, 0, 3) == "GIF") { "Not a GIF" }
        p = 6
        val w = u16(); val h = u16()
        val packed = u8(); u8(); u8()
        var globalTable: IntArray? = null
        if (packed and 0x80 != 0) globalTable = readTable(data, p, 2 shl (packed and 7)).also { p += it.size * 3 }

        val canvas = IntArray(w * h)
        val frames = mutableListOf<IntArray>()
        val delays = mutableListOf<Int>()
        var delay = 100; var transparent = -1; var disposal = 0

        while (p < data.size) {
            when (u8()) {
                0x21 -> {
                    val label = u8()
                    if (label == 0xF9) {
                        u8(); val pk = u8(); delay = u16() * 10; val ti = u8(); u8()
                        disposal = (pk shr 2) and 7
                        transparent = if (pk and 1 != 0) ti else -1
                    } else { var n = u8(); while (n > 0) { p += n; n = u8() } }
                }
                0x2C -> {
                    val fx = u16(); val fy = u16(); val fw = u16(); val fh = u16()
                    val ip = u8()
                    var table = globalTable
                    if (ip and 0x80 != 0) table = readTable(data, p, 2 shl (ip and 7)).also { p += it.size * 3 }
                    val interlaced = ip and 0x40 != 0
                    val minCode = u8()
                    val lzw = ByteArrayOutputStream()
                    var n = u8(); while (n > 0) { lzw.write(data, p, n); p += n; n = u8() }
                    val idx = lzwDecode(lzw.toByteArray(), minCode, fw * fh)
                    val before = if (disposal == 3) canvas.copyOf() else null
                    val rows = if (interlaced) interlaceRows(fh) else IntArray(fh) { it }
                    for (r in 0 until fh) {
                        val y = fy + rows[r]
                        if (y !in 0 until h) continue
                        for (x in 0 until fw) {
                            val c = idx[r * fw + x].toInt() and 0xFF
                            if (c == transparent || table == null || c >= table.size) continue
                            val cx = fx + x
                            if (cx in 0 until w) canvas[y * w + cx] = table[c]
                        }
                    }
                    frames.add(canvas.copyOf()); delays.add(if (delay <= 0) 100 else delay)
                    when (disposal) {
                        2 -> for (y in fy until minOf(h, fy + fh)) for (x in fx until minOf(w, fx + fw)) canvas[y * w + x] = 0
                        3 -> before?.copyInto(canvas)
                    }
                    transparent = -1; disposal = 0; delay = 100
                }
                0x3B -> break
                else -> break
            }
        }
        return DecodedGif(w, h, frames, delays)
    }

    private fun readTable(d: ByteArray, at: Int, n: Int) = IntArray(n) {
        val r = d[at + it * 3].toInt() and 0xFF; val g = d[at + it * 3 + 1].toInt() and 0xFF; val b = d[at + it * 3 + 2].toInt() and 0xFF
        (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    private fun interlaceRows(h: Int): IntArray {
        val out = IntArray(h); var i = 0
        for ((start, step) in listOf(0 to 8, 4 to 8, 2 to 4, 1 to 2)) { var y = start; while (y < h) { out[i++] = y; y += step } }
        return out
    }

    private fun lzwDecode(src: ByteArray, minCode: Int, count: Int): ByteArray {
        val out = ByteArray(count)
        val clear = 1 shl minCode; val eoi = clear + 1
        val prefix = IntArray(4096); val suffix = ByteArray(4096); val stack = ByteArray(4097)
        var codeSize = minCode + 1; var next = eoi + 1
        var old = -1; var first = 0
        var bits = 0; var nbits = 0; var sp = 0; var op = 0
        for (i in 0 until clear) suffix[i] = i.toByte()
        loop@ for (byte in src) {
            bits = bits or ((byte.toInt() and 0xFF) shl nbits); nbits += 8
            while (nbits >= codeSize) {
                val code = bits and ((1 shl codeSize) - 1)
                bits = bits ushr codeSize; nbits -= codeSize
                if (code == clear) { codeSize = minCode + 1; next = eoi + 1; old = -1; continue }
                if (code == eoi) break@loop
                if (old == -1) { if (op < count) out[op++] = suffix[code]; old = code; first = code; continue }
                var c = code
                if (code >= next) { stack[sp++] = first.toByte(); c = old }
                while (c >= clear) { stack[sp++] = suffix[c]; c = prefix[c] }
                first = suffix[c].toInt() and 0xFF
                stack[sp++] = first.toByte()
                while (sp > 0 && op < count) out[op++] = stack[--sp]
                sp = 0
                if (next < 4096) {
                    prefix[next] = old; suffix[next] = first.toByte(); next++
                    if (next == (1 shl codeSize) && codeSize < 12) codeSize++
                }
                old = code
            }
        }
        return out
    }
}

// endregion

// region Sprite sheets & frame archives

object SheetFormat {
    /** Slices an image into frames of fw*fh, reading left-to-right, top-to-bottom; skips fully empty tiles at the end. */
    fun slice(px: IntArray, iw: Int, ih: Int, fw: Int, fh: Int): List<IntArray> {
        val cols = maxOf(1, iw / fw); val rows = maxOf(1, ih / fh)
        val frames = mutableListOf<IntArray>()
        for (r in 0 until rows) for (c in 0 until cols) {
            val f = IntArray(fw * fh)
            for (y in 0 until fh) for (x in 0 until fw) {
                val sx = c * fw + x; val sy = r * fh + y
                if (sx < iw && sy < ih) f[y * fw + x] = px[sy * iw + sx]
            }
            clean(f)
            frames.add(f)
        }
        while (frames.size > 1 && frames.last().all { it == 0 }) frames.removeAt(frames.lastIndex)
        return frames
    }

    /** ZIP of one PNG per frame (and, optionally, per layer). */
    fun writeFramesZip(out: OutputStream, name: String, s: Sprite, scale: Int, perLayer: Boolean) {
        ZipOutputStream(out).use { zip ->
            fun put(entry: String, px: IntArray) {
                val big = if (scale == 1) px else IntArray(px.size * scale * scale) { i ->
                    val sw = s.width * scale; px[(i / sw / scale) * s.width + (i % sw) / scale]
                }
                zip.putNextEntry(ZipEntry(entry))
                zip.write(pngBytes(big, s.width * scale, s.height * scale))
                zip.closeEntry()
            }
            val digits = s.frameCount.toString().length
            for (f in 0 until s.frameCount) {
                val n = (f + 1).toString().padStart(digits, '0')
                put("${name}_$n.png", s.composite(f))
                if (perLayer) s.layers.forEach { l -> put("layers/${l.name.replace('/', '_')}/${name}_$n.png", l.cels[f]) }
            }
        }
    }

    fun decodeImage(bytes: ByteArray): Triple<IntArray, Int, Int>? {
        val opts = BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888; inScaled = false }
        val b = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return null
        val px = bitmapPixels(b); clean(px)
        return Triple(px, b.width, b.height)
    }
}

// endregion
