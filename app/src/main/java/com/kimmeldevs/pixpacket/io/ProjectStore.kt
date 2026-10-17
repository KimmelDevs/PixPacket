package com.kimmeldevs.pixpacket.io

import android.content.Context
import android.graphics.Bitmap
import com.kimmeldevs.pixpacket.model.GridSettings
import com.kimmeldevs.pixpacket.model.Layer
import com.kimmeldevs.pixpacket.model.Sprite
import org.json.JSONArray
import org.json.JSONObject
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream

class ProjectInfo(
    val id: String, val name: String, val width: Int, val height: Int, val frames: Int,
    val modified: Long, val thumb: Bitmap?, val folder: String?,
    /** When it was moved to Recently deleted (0 if it isn't deleted). */
    val deletedAt: Long = 0,
)

/** Saves projects as gzip'd binary files (*.pxp) in app storage, with a PNG thumbnail beside each. */
object ProjectStore {
    private const val MAGIC_V1 = 0x50585031 // "PXP1"
    private const val MAGIC_V2 = 0x50585032 // "PXP2": adds grid settings

    private fun dir(ctx: Context) = File(ctx.filesDir, "projects").apply { mkdirs() }
    private fun file(ctx: Context, id: String) = File(dir(ctx), "$id.pxp")
    private fun thumbFile(ctx: Context, id: String) = File(dir(ctx), "$id.png")
    private fun trashDir(ctx: Context) = File(ctx.filesDir, "trash").apply { mkdirs() }
    private fun trashIndex(ctx: Context) = File(trashDir(ctx), "index.json")

    /** Sprites stay in Recently deleted this long before they are removed for good. */
    const val TRASH_DAYS = 30

    fun newId(): String = UUID.randomUUID().toString()

    /** Writes the .pxp format (also used for "PixPacket project" export). */
    fun write(out: OutputStream, name: String, s: Sprite) {
        DataOutputStream(GZIPOutputStream(out.buffered())).use { o ->
            o.writeInt(MAGIC_V2)
            o.writeUTF(name)
            o.writeInt(s.width); o.writeInt(s.height); o.writeInt(s.fps)
            val g = s.grid
            o.writeBoolean(g.pixelLines); o.writeBoolean(g.tileLines)
            o.writeInt(g.tileWidth); o.writeInt(g.tileHeight); o.writeInt(g.color); o.writeInt(g.offsetX); o.writeInt(g.offsetY)
            o.writeInt(s.frameCount); o.writeInt(s.layers.size)
            for (l in s.layers) {
                o.writeUTF(l.name); o.writeBoolean(l.visible); o.writeBoolean(l.locked); o.writeFloat(l.opacity)
                for (c in l.cels) for (p in c) o.writeInt(p)
            }
        }
    }

    /** Reads a .pxp stream; returns name and sprite, or null if it isn't one. */
    fun read(input: InputStream): Pair<String, Sprite>? = try {
        DataInputStream(GZIPInputStream(input.buffered())).use { i ->
            val magic = i.readInt()
            require(magic == MAGIC_V1 || magic == MAGIC_V2)
            val name = i.readUTF()
            val w = i.readInt(); val h = i.readInt(); val fps = i.readInt()
            val grid = if (magic == MAGIC_V2) GridSettings(
                i.readBoolean(), i.readBoolean(), i.readInt(), i.readInt(), i.readInt(), i.readInt(), i.readInt(),
            ) else GridSettings()
            val frames = i.readInt(); val layerCount = i.readInt()
            val layers = MutableList(layerCount) {
                val ln = i.readUTF(); val vis = i.readBoolean(); val lock = i.readBoolean(); val op = i.readFloat()
                Layer(ln, MutableList(frames) { IntArray(w * h) { i.readInt() } }, vis, lock, op)
            }
            name to Sprite(w, h, layers, fps, grid)
        }
    } catch (_: Exception) { null }

    fun save(ctx: Context, id: String, name: String, s: Sprite) {
        val tmp = File(dir(ctx), "$id.tmp")
        tmp.outputStream().use { write(it, name, s) }
        tmp.renameTo(file(ctx, id).also { it.delete() })
        val bmp = Bitmap.createBitmap(s.width, s.height, Bitmap.Config.ARGB_8888)
        bmp.setPixels(s.composite(0), 0, s.width, 0, 0, s.width, s.height)
        thumbFile(ctx, id).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    fun load(ctx: Context, id: String): Pair<String, Sprite>? = try { file(ctx, id).inputStream().use { read(it) } } catch (_: Exception) { null }

    private fun info(f: File, thumbDir: File, folder: String?, deletedAt: Long): ProjectInfo? = try {
        DataInputStream(GZIPInputStream(f.inputStream().buffered())).use { i ->
            val magic = i.readInt()
            if (magic != MAGIC_V1 && magic != MAGIC_V2) return null
            val name = i.readUTF()
            val w = i.readInt(); val h = i.readInt(); i.readInt()
            if (magic == MAGIC_V2) { i.readBoolean(); i.readBoolean(); repeat(5) { i.readInt() } }
            val frames = i.readInt()
            val id = f.nameWithoutExtension
            val thumb = File(thumbDir, "$id.png").takeIf { it.exists() }?.let { android.graphics.BitmapFactory.decodeFile(it.path) }
            ProjectInfo(id, name, w, h, frames, f.lastModified(), thumb, folder, deletedAt)
        }
    } catch (_: Exception) { null }

    fun list(ctx: Context): List<ProjectInfo> {
        val lib = Library.load(ctx)
        return dir(ctx).listFiles { f -> f.extension == "pxp" }.orEmpty()
            .sortedByDescending { it.lastModified() }
            .mapNotNull { info(it, dir(ctx), lib.folderOf(it.nameWithoutExtension), 0) }
    }

    // region Recently deleted

    /** id -> [deletedAt, folder] */
    private fun readTrash(ctx: Context): MutableMap<String, Pair<Long, String?>> = try {
        val o = JSONObject(trashIndex(ctx).readText())
        o.keys().asSequence().associateWith { k ->
            val e = o.getJSONObject(k); e.getLong("at") to e.optString("folder").ifEmpty { null }
        }.toMutableMap()
    } catch (_: Exception) { mutableMapOf() }

    private fun writeTrash(ctx: Context, m: Map<String, Pair<Long, String?>>) {
        val o = JSONObject()
        m.forEach { (k, v) -> o.put(k, JSONObject().put("at", v.first).put("folder", v.second ?: "")) }
        trashIndex(ctx).writeText(o.toString())
    }

    /** Lists Recently deleted (newest first), removing anything older than [TRASH_DAYS]. */
    fun listTrash(ctx: Context): List<ProjectInfo> {
        val m = readTrash(ctx)
        val cutoff = System.currentTimeMillis() - TRASH_DAYS * 24L * 3600 * 1000
        m.filter { it.value.first < cutoff }.keys.forEach { deleteForever(ctx, it) }
        val fresh = readTrash(ctx)
        return fresh.entries.sortedByDescending { it.value.first }.mapNotNull { (id, v) ->
            info(File(trashDir(ctx), "$id.pxp"), trashDir(ctx), v.second, v.first)
        }
    }

    fun restore(ctx: Context, id: String) {
        val m = readTrash(ctx)
        val entry = m.remove(id) ?: return
        File(trashDir(ctx), "$id.pxp").renameTo(file(ctx, id))
        File(trashDir(ctx), "$id.png").renameTo(thumbFile(ctx, id))
        file(ctx, id).setLastModified(System.currentTimeMillis())
        writeTrash(ctx, m)
        val folder = entry.second
        if (folder != null) Library.update(ctx) { if (folder !in it.folders) it.folders.add(folder); it.assign(id, folder) }
    }

    fun deleteForever(ctx: Context, id: String) {
        File(trashDir(ctx), "$id.pxp").delete(); File(trashDir(ctx), "$id.png").delete()
        writeTrash(ctx, readTrash(ctx).also { it.remove(id) })
    }

    fun emptyTrash(ctx: Context) {
        readTrash(ctx).keys.forEach { File(trashDir(ctx), "$it.pxp").delete(); File(trashDir(ctx), "$it.png").delete() }
        writeTrash(ctx, emptyMap())
    }

    // endregion

    /** Moves a sprite to Recently deleted (restorable for [TRASH_DAYS] days). */
    fun delete(ctx: Context, id: String) {
        val folder = Library.load(ctx).folderOf(id)
        file(ctx, id).renameTo(File(trashDir(ctx), "$id.pxp"))
        thumbFile(ctx, id).renameTo(File(trashDir(ctx), "$id.png"))
        writeTrash(ctx, readTrash(ctx).also { it[id] = System.currentTimeMillis() to folder })
        Library.update(ctx) { it.assign(id, null) }
    }

    fun duplicate(ctx: Context, id: String) {
        val (name, s) = load(ctx, id) ?: return
        val newId = newId()
        save(ctx, newId, "$name copy", s)
        val folder = Library.load(ctx).folderOf(id)
        if (folder != null) Library.update(ctx) { it.assign(newId, folder) }
    }

    fun rename(ctx: Context, id: String, newName: String) {
        val (_, s) = load(ctx, id) ?: return
        save(ctx, id, newName, s)
    }
}

/** Folder organisation for the gallery, stored as a small JSON file. */
class Library(val folders: MutableList<String>, private val map: MutableMap<String, String>) {
    fun folderOf(id: String): String? = map[id]?.takeIf { it in folders }
    fun assign(id: String, folder: String?) { if (folder == null) map.remove(id) else map[id] = folder }
    fun countIn(folder: String) = map.values.count { it == folder }

    fun renameFolder(old: String, new: String) {
        val i = folders.indexOf(old)
        if (i < 0 || new.isBlank() || new in folders) return
        folders[i] = new
        map.entries.forEach { if (it.value == old) it.setValue(new) }
    }

    fun deleteFolder(name: String) {
        folders.remove(name)
        map.entries.removeAll { it.value == name }
    }

    companion object {
        private fun file(ctx: Context) = File(ctx.filesDir, "library.json")

        fun load(ctx: Context): Library = try {
            val o = JSONObject(file(ctx).readText())
            val f = o.getJSONArray("folders").let { a -> MutableList(a.length()) { a.getString(it) } }
            val m = o.getJSONObject("map").let { mo -> mo.keys().asSequence().associateWith { mo.getString(it) }.toMutableMap() }
            Library(f, m)
        } catch (_: Exception) { Library(mutableListOf(), mutableMapOf()) }

        fun update(ctx: Context, block: (Library) -> Unit) {
            val lib = load(ctx)
            block(lib)
            val o = JSONObject()
            o.put("folders", JSONArray(lib.folders))
            o.put("map", JSONObject(lib.map as Map<*, *>))
            file(ctx).writeText(o.toString())
        }
    }
}

/** Small persisted bits that aren't user-facing settings. */
object Prefs {
    private fun sp(ctx: Context) = ctx.getSharedPreferences("pixpacket", Context.MODE_PRIVATE)

    fun customPalette(ctx: Context): List<Int> =
        sp(ctx).getString("custom_palette", "")!!.split(',').filter { it.isNotBlank() }.mapNotNull { it.toLongOrNull()?.toInt() }

    fun setCustomPalette(ctx: Context, colors: List<Int>) =
        sp(ctx).edit().putString("custom_palette", colors.joinToString(",") { (it.toLong() and 0xFFFFFFFFL).toString() }).apply()
}
