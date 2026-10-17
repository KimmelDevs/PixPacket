package com.kimmeldevs.pixpacket.io

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** A palette the user made. No size limit; shown 16 per swipeable page. */
data class UserPalette(val name: String, val colors: List<Int>)

object PaletteStore {
    private fun sp(ctx: Context) = ctx.getSharedPreferences("pixpacket", Context.MODE_PRIVATE)

    fun load(ctx: Context): List<UserPalette> {
        val raw = sp(ctx).getString("user_palettes", null)
        if (raw == null) {
            // Migrate the single "My palette" from earlier versions.
            val old = Prefs.customPalette(ctx)
            return if (old.isEmpty()) emptyList() else listOf(UserPalette("My palette", old)).also { save(ctx, it) }
        }
        return try {
            val a = JSONArray(raw)
            List(a.length()) { i ->
                val o = a.getJSONObject(i)
                val c = o.getJSONArray("colors")
                UserPalette(o.getString("name"), List(c.length()) { c.getLong(it).toInt() })
            }
        } catch (_: Exception) { emptyList() }
    }

    fun save(ctx: Context, palettes: List<UserPalette>) {
        val a = JSONArray()
        palettes.forEach { p ->
            a.put(JSONObject().put("name", p.name).put("colors", JSONArray().apply { p.colors.forEach { put(it.toLong() and 0xFFFFFFFFL) } }))
        }
        sp(ctx).edit().putString("user_palettes", a.toString()).apply()
    }

    /** Picks a name like "Palette 3" that isn't taken yet. */
    fun freshName(existing: List<UserPalette>, base: String = "Palette"): String {
        val names = existing.map { it.name }.toSet()
        if (base !in names) return base
        var n = 2
        while ("$base $n" in names) n++
        return "$base $n"
    }
}
