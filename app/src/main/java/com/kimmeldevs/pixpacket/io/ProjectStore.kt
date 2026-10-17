package com.kimmeldevs.pixpacket.io

import android.content.Context
import android.graphics.Bitmap
import com.kimmeldevs.pixpacket.model.Layer
import com.kimmeldevs.pixpacket.model.Sprite
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.util.UUID
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

class ProjectInfo(val id: String, val name: String, val width: Int, val height: Int, val frames: Int, val modified: Long, val thumb: Bitmap?)

/** Saves projects as gzip'd binary files (*.pxp) in app storage, with a PNG thumbnail beside each. */
object ProjectStore {
    private const val MAGIC = 0x50585031 // "PXP1"

    private fun dir(ctx: Context) = File(ctx.filesDir, "projects").apply { mkdirs() }
    private fun file(ctx: Context, id: String) = File(dir(ctx), "$id.pxp")
    private fun thumbFile(ctx: Context, id: String) = File(dir(ctx), "$id.png")

    fun newId(): String = UUID.randomUUID().toString()

    fun save(ctx: Context, id: String, name: String, s: Sprite) {
        val tmp = File(dir(ctx), "$id.tmp")
        DataOutputStream(GZIPOutputStream(tmp.outputStream().buffered())).use { o ->
            o.writeInt(MAGIC)
            o.writeUTF(name)
            o.writeInt(s.width); o.writeInt(s.height); o.writeInt(s.fps)
            o.writeInt(s.frameCount); o.writeInt(s.layers.size)
            for (l in s.layers) {
                o.writeUTF(l.name); o.writeBoolean(l.visible); o.writeBoolean(l.locked); o.writeFloat(l.opacity)
                for (c in l.cels) for (p in c) o.writeInt(p)
            }
        }
        tmp.renameTo(file(ctx, id).also { it.delete() })
        val bmp = Bitmap.createBitmap(s.width, s.height, Bitmap.Config.ARGB_8888)
        bmp.setPixels(s.composite(0), 0, s.width, 0, 0, s.width, s.height)
        thumbFile(ctx, id).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    fun load(ctx: Context, id: String): Pair<String, Sprite>? = try {
        DataInputStream(GZIPInputStream(file(ctx, id).inputStream().buffered())).use { i ->
            require(i.readInt() == MAGIC)
            val name = i.readUTF()
            val w = i.readInt(); val h = i.readInt(); val fps = i.readInt()
            val frames = i.readInt(); val layerCount = i.readInt()
            val layers = MutableList(layerCount) {
                val ln = i.readUTF(); val vis = i.readBoolean(); val lock = i.readBoolean(); val op = i.readFloat()
                Layer(ln, MutableList(frames) { IntArray(w * h) { i.readInt() } }, vis, lock, op)
            }
            name to Sprite(w, h, layers, fps)
        }
    } catch (_: Exception) { null }

    fun list(ctx: Context): List<ProjectInfo> = dir(ctx).listFiles { f -> f.extension == "pxp" }.orEmpty()
        .sortedByDescending { it.lastModified() }
        .mapNotNull { f ->
            try {
                DataInputStream(GZIPInputStream(f.inputStream().buffered())).use { i ->
                    if (i.readInt() != MAGIC) return@mapNotNull null
                    val name = i.readUTF()
                    val w = i.readInt(); val h = i.readInt(); i.readInt()
                    val frames = i.readInt()
                    val id = f.nameWithoutExtension
                    val thumb = thumbFile(ctx, id).takeIf { it.exists() }?.let { android.graphics.BitmapFactory.decodeFile(it.path) }
                    ProjectInfo(id, name, w, h, frames, f.lastModified(), thumb)
                }
            } catch (_: Exception) { null }
        }

    fun delete(ctx: Context, id: String) { file(ctx, id).delete(); thumbFile(ctx, id).delete() }

    fun duplicate(ctx: Context, id: String) {
        val (name, s) = load(ctx, id) ?: return
        save(ctx, newId(), "$name copy", s)
    }

    fun rename(ctx: Context, id: String, newName: String) {
        val (_, s) = load(ctx, id) ?: return
        save(ctx, id, newName, s)
    }
}

/** Small persisted preferences: the user's custom palette. */
object Prefs {
    private fun sp(ctx: Context) = ctx.getSharedPreferences("pixpacket", Context.MODE_PRIVATE)

    fun customPalette(ctx: Context): List<Int> =
        sp(ctx).getString("custom_palette", "")!!.split(',').filter { it.isNotBlank() }.mapNotNull { it.toLongOrNull()?.toInt() }

    fun setCustomPalette(ctx: Context, colors: List<Int>) =
        sp(ctx).edit().putString("custom_palette", colors.joinToString(",") { (it.toLong() and 0xFFFFFFFFL).toString() }).apply()

    fun seenTips(ctx: Context) = sp(ctx).getBoolean("seen_tips", false)
    fun setSeenTips(ctx: Context) = sp(ctx).edit().putBoolean("seen_tips", true).apply()
}
