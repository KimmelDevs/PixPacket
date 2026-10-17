package com.kimmeldevs.pixpacket.io

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.kimmeldevs.pixpacket.model.Sprite
import java.io.File
import java.io.OutputStream

enum class ExportKind(val label: String, val ext: String, val mime: String) {
    PNG("PNG (current frame)", "png", "image/png"),
    SHEET("Sprite sheet PNG", "png", "image/png"),
    GIF("Animated GIF", "gif", "image/gif"),
}

object Exporter {

    private fun bitmap(px: IntArray, w: Int, h: Int, scale: Int): Bitmap {
        val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        b.setPixels(px, 0, w, 0, 0, w, h)
        return if (scale == 1) b else Bitmap.createScaledBitmap(b, w * scale, h * scale, false)
    }

    /** Lays every frame out in a grid of [columns] (0 = single row). */
    fun spriteSheet(s: Sprite, columns: Int): Triple<IntArray, Int, Int> {
        val n = s.frameCount
        val cols = if (columns <= 0) n else columns.coerceAtMost(n)
        val rows = (n + cols - 1) / cols
        val sw = s.width * cols; val sh = s.height * rows
        val out = IntArray(sw * sh)
        for (f in 0 until n) {
            val px = s.composite(f)
            val ox = (f % cols) * s.width; val oy = (f / cols) * s.height
            for (y in 0 until s.height) System.arraycopy(px, y * s.width, out, (oy + y) * sw + ox, s.width)
        }
        return Triple(out, sw, sh)
    }

    fun write(out: OutputStream, s: Sprite, kind: ExportKind, frame: Int, scale: Int, columns: Int) {
        when (kind) {
            ExportKind.PNG -> bitmap(s.composite(frame), s.width, s.height, scale).compress(Bitmap.CompressFormat.PNG, 100, out)
            ExportKind.SHEET -> {
                val (px, w, h) = spriteSheet(s, columns)
                bitmap(px, w, h, scale).compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            ExportKind.GIF -> GifEncoder.encode(out, List(s.frameCount) { s.composite(it) }, s.width, s.height, 1000 / s.fps, scale)
        }
    }

    /** Saves to Pictures/PixPacket via MediaStore. Returns a user-facing location string. */
    fun saveToGallery(ctx: Context, s: Sprite, name: String, kind: ExportKind, frame: Int, scale: Int, columns: Int): String {
        val fileName = safe(name) + (if (kind == ExportKind.SHEET) "_sheet" else "") + "_${System.currentTimeMillis() / 1000}.${kind.ext}"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, kind.mime)
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/PixPacket")
            }
            val uri = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
                ?: error("Could not create file")
            ctx.contentResolver.openOutputStream(uri)!!.use { write(it, s, kind, frame, scale, columns) }
        } else {
            @Suppress("DEPRECATION")
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "PixPacket").apply { mkdirs() }
            File(dir, fileName).outputStream().use { write(it, s, kind, frame, scale, columns) }
        }
        return "Pictures/PixPacket/$fileName"
    }

    fun share(ctx: Context, s: Sprite, name: String, kind: ExportKind, frame: Int, scale: Int, columns: Int) {
        val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
        val f = File(dir, safe(name) + "." + kind.ext)
        f.outputStream().use { write(it, s, kind, frame, scale, columns) }
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = kind.mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(send, "Share $name").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun safe(name: String) = name.replace(Regex("[^A-Za-z0-9_-]+"), "_").trim('_').ifEmpty { "sprite" }

    /** Decodes an image and fits it (nearest neighbour, keeping aspect) into w*h, centered. */
    fun importImage(ctx: Context, uri: Uri, w: Int, h: Int): IntArray {
        val src: Bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(ctx.contentResolver, uri)) { d, _, _ ->
                d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Images.Media.getBitmap(ctx.contentResolver, uri)
        }
        val k = minOf(w.toFloat() / src.width, h.toFloat() / src.height)
        val nw = (src.width * k).toInt().coerceAtLeast(1); val nh = (src.height * k).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(src, nw, nh, false)
        val px = IntArray(nw * nh)
        scaled.getPixels(px, 0, nw, 0, 0, nw, nh)
        val out = IntArray(w * h)
        val ox = (w - nw) / 2; val oy = (h - nh) / 2
        for (y in 0 until nh) for (x in 0 until nw) {
            val p = px[y * nw + x]
            out[(y + oy) * w + x + ox] = if ((p ushr 24) < 128) 0 else p or (0xFF shl 24)
        }
        return out
    }
}
