package com.kimmeldevs.pixpacket.model

import android.graphics.Color as AColor

object ColorUtil {
    fun toHex(c: Int): String {
        val a = c ushr 24
        val rgb = String.format("%06X", c and 0xFFFFFF)
        return if (a == 255) "#$rgb" else "#$rgb" + String.format("%02X", a)
    }

    /** Parses #RGB, #RRGGBB or #RRGGBBAA. */
    fun parseHex(text: String): Int? {
        val s = text.trim().removePrefix("#")
        return try {
            when (s.length) {
                3 -> (0xFF shl 24) or s.map { it.digitToInt(16) * 17 }.let { (it[0] shl 16) or (it[1] shl 8) or it[2] }
                6 -> (0xFF shl 24) or s.toLong(16).toInt()
                8 -> { val v = s.toLong(16).toInt(); (v shl 24) or (v ushr 8) }
                else -> null
            }
        } catch (_: Exception) { null }
    }

    fun toHsv(c: Int): FloatArray = FloatArray(3).also { AColor.colorToHSV(c, it) }
    fun fromHsv(h: Float, s: Float, v: Float, alpha: Int = 255): Int = AColor.HSVToColor(alpha, floatArrayOf(h, s, v))

    /** Lightens or darkens one step, keeping hue (Piskel's lighten/darken tool, Aseprite's shading). */
    fun shade(c: Int, lighten: Boolean, amount: Float = 0.1f): Int {
        if (c == 0) return 0
        val hsl = FloatArray(3)
        androidx.core.graphics.ColorUtils.colorToHSL(c, hsl)
        hsl[2] = (hsl[2] + if (lighten) amount else -amount).coerceIn(0f, 1f)
        val rgb = androidx.core.graphics.ColorUtils.HSLToColor(hsl)
        return (c and 0xFF000000.toInt()) or (rgb and 0xFFFFFF)
    }

    fun isLight(c: Int): Boolean {
        val r = (c shr 16) and 0xFF; val g = (c shr 8) and 0xFF; val b = c and 0xFF
        return (r * 299 + g * 587 + b * 114) / 1000 > 140 || (c ushr 24) < 100
    }
}

class Palette(val name: String, val colors: List<Int>)

object Palettes {
    private fun p(name: String, vararg hex: String) =
        Palette(name, hex.map { (0xFF000000.toInt()) or it.toInt(16) })

    val presets = listOf(
        p("Default", "000000", "FFFFFF", "9D9D9D", "525252", "BE2633", "E06F8B", "A46422", "EB8931",
            "F7E26B", "2F484E", "44891A", "A3CE27", "1B2632", "005784", "31A2F2", "B2DCEF"),
        p("PICO-8", "000000", "1D2B53", "7E2553", "008751", "AB5236", "5F574F", "C2C3C7", "FFF1E8",
            "FF004D", "FFA300", "FFEC27", "00E436", "29ADFF", "83769C", "FF77A8", "FFCCAA"),
        p("DB16", "140C1C", "442434", "30346D", "4E4A4E", "854C30", "346524", "D04648", "757161",
            "597DCE", "D27D2C", "8595A1", "6DAA2C", "D2AA99", "6DC2CA", "DAD45E", "DEEED6"),
        p("Endesga 32", "BE4A2F", "D77643", "EAD4AA", "E4A672", "B86F50", "733E39", "3E2731", "A22633",
            "E43B44", "F77622", "FEAE34", "FEE761", "63C74D", "3E8948", "265C42", "193C3E",
            "124E89", "0099DB", "2CE8F5", "FFFFFF", "C0CBDC", "8B9BB4", "5A6988", "3A4466",
            "262B44", "181425", "FF0044", "68386C", "B55088", "F6757A", "E8B796", "C28569"),
        p("Game Boy", "0F380F", "306230", "8BAC0F", "9BBC0F"),
        p("NES", "7C7C7C", "0000FC", "0000BC", "4428BC", "940084", "A80020", "A81000", "881400",
            "503000", "007800", "006800", "005800", "004058", "000000", "BCBCBC", "0078F8",
            "0058F8", "6844FC", "D800CC", "E40058", "F83800", "E45C10", "AC7C00", "00B800",
            "00A800", "00A844", "008888", "F8F8F8", "3CBCFC", "6888FC", "9878F8", "F878F8",
            "F85898", "F87858", "FCA044", "F8B800", "B8F818", "58D854", "58F898", "00E8D8", "FCFCFC"),
        p("Grayscale", "000000", "222222", "444444", "666666", "888888", "AAAAAA", "CCCCCC", "FFFFFF"),
        p("Skin & Hair", "2B1B17", "4A2C21", "7A4A32", "A86B4B", "D49A6A", "F2C49B", "FFE3C8",
            "3B2219", "6E3B1F", "B5651D", "E8B04B", "F4E285"),
    )
}
