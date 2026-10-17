package com.kimmeldevs.pixpacket.io

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import com.kimmeldevs.pixpacket.model.Sprite
import java.io.File
import java.io.OutputStream

enum class ExportKind(val label: String, val detail: String, val ext: String, val mime: String, val isImage: Boolean, val scalable: Boolean) {
    PNG("PNG image", "Current frame", "png", "image/png", true, true),
    GIF("Animated GIF", "All frames, loops forever", "gif", "image/gif", true, true),
    SHEET("Sprite sheet", "All frames in one PNG", "png", "image/png", true, true),
    FRAMES_ZIP("PNG frames (ZIP)", "One PNG per frame, optional per layer", "zip", "application/zip", false, true),
    ASEPRITE("Aseprite", ".aseprite — keeps layers & frames", "aseprite", "application/octet-stream", false, false),
    PISKEL("Piskel", ".piskel — keeps layers & frames", "piskel", "application/json", false, false),
    PROJECT("PixPacket project", ".pxp backup — keeps everything", "pxp", "application/octet-stream", false, false),
}

class ExportOptions(val kind: ExportKind, val scale: Int = 1, val columns: Int = 0, val frame: Int = 0, val perLayer: Boolean = false)

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

    fun write(out: OutputStream, s: Sprite, name: String, o: ExportOptions) {
        when (o.kind) {
            ExportKind.PNG -> bitmap(s.composite(o.frame), s.width, s.height, o.scale).compress(Bitmap.CompressFormat.PNG, 100, out)
            ExportKind.SHEET -> {
                val (px, w, h) = spriteSheet(s, o.columns)
                bitmap(px, w, h, o.scale).compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            ExportKind.GIF -> GifEncoder.encode(out, List(s.frameCount) { s.composite(it) }, s.width, s.height, 1000 / s.fps, o.scale)
            ExportKind.FRAMES_ZIP -> SheetFormat.writeFramesZip(out, safe(name), s, o.scale, o.perLayer)
            ExportKind.ASEPRITE -> AsepriteFormat.write(out, s)
            ExportKind.PISKEL -> PiskelFormat.write(out, name, s)
            ExportKind.PROJECT -> ProjectStore.write(out, name, s)
        }
    }

    fun fileName(name: String, kind: ExportKind) =
        safe(name) + (if (kind == ExportKind.SHEET) "_sheet" else if (kind == ExportKind.FRAMES_ZIP) "_frames" else "") + "." + kind.ext

    /** Saves an image to Pictures/PixPacket via MediaStore. Returns a user-facing location. */
    fun saveToGallery(ctx: Context, s: Sprite, name: String, o: ExportOptions): String {
        val fileName = fileName(name, o.kind).let { it.substringBeforeLast('.') + "_${System.currentTimeMillis() / 1000}." + o.kind.ext }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Images.Media.MIME_TYPE, o.kind.mime)
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/PixPacket")
            }
            val uri = ctx.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: error("Could not create file")
            ctx.contentResolver.openOutputStream(uri)!!.use { write(it, s, name, o) }
        } else {
            @Suppress("DEPRECATION")
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES), "PixPacket").apply { mkdirs() }
            File(dir, fileName).outputStream().use { write(it, s, name, o) }
        }
        return "Pictures/PixPacket/$fileName"
    }

    /** Writes to a document the user picked with the system "Save as" screen. */
    fun saveToUri(ctx: Context, uri: Uri, s: Sprite, name: String, o: ExportOptions) {
        ctx.contentResolver.openOutputStream(uri, "wt")!!.use { write(it, s, name, o) }
    }

    fun share(ctx: Context, s: Sprite, name: String, o: ExportOptions) {
        val dir = File(ctx.cacheDir, "share").apply { mkdirs() }
        val f = File(dir, fileName(name, o.kind))
        f.outputStream().use { write(it, s, name, o) }
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = o.kind.mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        ctx.startActivity(Intent.createChooser(send, "Share $name").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private fun safe(name: String) = name.replace(Regex("[^A-Za-z0-9_-]+"), "_").trim('_').ifEmpty { "sprite" }
}

/** What an opened file turned out to be. Images may still need slicing into frames. */
sealed interface Imported {
    class Document(val name: String, val sprite: Sprite) : Imported
    class Image(val name: String, val pixels: IntArray, val width: Int, val height: Int) : Imported
}

object Importer {
    fun displayName(ctx: Context, uri: Uri): String =
        ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        } ?: uri.lastPathSegment ?: "Imported"

    /** Detects the format from the file contents (falls back to the extension). */
    fun open(ctx: Context, uri: Uri): Imported {
        val bytes = ctx.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
        val fileName = displayName(ctx, uri)
        val base = fileName.substringBeforeLast('.').ifBlank { "Imported" }
        val ext = fileName.substringAfterLast('.', "").lowercase()
        fun u16(i: Int) = (bytes[i].toInt() and 0xFF) or ((bytes[i + 1].toInt() and 0xFF) shl 8)
        return when {
            bytes.size > 6 && String(bytes, 0, 3) == "GIF" -> {
                val g = GifDecoder.decode(bytes)
                val fps = (1000.0 / g.delaysMs.average().coerceAtLeast(10.0)).toInt().coerceIn(1, 30)
                checkSize(g.width, g.height)
                Imported.Document(base, Sprite.fromFrames(g.width, g.height, g.frames, fps))
            }
            bytes.size > 128 && u16(4) == 0xA5E0 -> {
                val (_, s) = AsepriteFormat.read(bytes); checkSize(s.width, s.height); Imported.Document(base, s)
            }
            bytes.size > 2 && bytes[0] == 0x1F.toByte() && bytes[1] == 0x8B.toByte() ->
                ProjectStore.read(bytes.inputStream())?.let { (n, s) -> Imported.Document(n, s) } ?: error("Unreadable project file")
            ext == "piskel" || (bytes.isNotEmpty() && bytes[0] == '{'.code.toByte()) -> {
                val (n, s) = PiskelFormat.read(bytes); checkSize(s.width, s.height); Imported.Document(n.ifBlank { base }, s)
            }
            else -> {
                val (px, w, h) = SheetFormat.decodeImage(bytes) ?: error("Unsupported file type")
                Imported.Image(base, px, w, h)
            }
        }
    }

    private fun checkSize(w: Int, h: Int) = require(w in 1..1024 && h in 1..1024) { "Sprite is too large (${w}×$h, max 1024)" }
}
