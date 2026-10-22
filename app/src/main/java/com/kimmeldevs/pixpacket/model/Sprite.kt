package com.kimmeldevs.pixpacket.model

/** A single layer. Each cel is an ARGB pixel array of size width*height, one per frame. */
class Layer(
    var name: String,
    val cels: MutableList<IntArray>,
    var visible: Boolean = true,
    var locked: Boolean = false,
    var opacity: Float = 1f,
) {
    fun deepCopy(): Layer = Layer(name, cels.map { it.copyOf() }.toMutableList(), visible, locked, opacity)
}

/**
 * Grid overlay settings, stored with each sprite. Color: 0 = dark, 1 = light, 2 = accent.
 * When [iso] is on, the tile grid is drawn as isometric diamonds [tileWidth] wide and [tileHeight] tall.
 */
data class GridSettings(
    val pixelLines: Boolean = true,
    val tileLines: Boolean = true,
    val tileWidth: Int = 8,
    val tileHeight: Int = 8,
    val color: Int = 0,
    val offsetX: Int = 0,
    val offsetY: Int = 0,
    val iso: Boolean = false,
)

/** The whole document: a stack of layers sharing the same frame count. Index 0 is the bottom layer. */
class Sprite(
    var width: Int,
    var height: Int,
    val layers: MutableList<Layer>,
    var fps: Int = 8,
    var grid: GridSettings = GridSettings(),
) {
    val frameCount: Int get() = layers.firstOrNull()?.cels?.size ?: 0

    fun deepCopy(): Sprite = Sprite(width, height, layers.map { it.deepCopy() }.toMutableList(), fps, grid)

    fun newCel() = IntArray(width * height)

    /** Composites every visible layer of [frame] into [out] (source-over blending). */
    fun composite(frame: Int, out: IntArray = IntArray(width * height), onlyVisible: Boolean = true): IntArray {
        out.fill(0)
        for (layer in layers) {
            if (onlyVisible && !layer.visible) continue
            val cel = layer.cels[frame]
            val op = layer.opacity
            for (i in out.indices) {
                val src = cel[i]
                if (src == 0) continue
                out[i] = blend(out[i], src, op)
            }
        }
        return out
    }

    companion object {
        fun blank(width: Int, height: Int, background: Int = 0, fps: Int = 8, tile: Int = 8): Sprite {
            val cel = IntArray(width * height) { background }
            return Sprite(width, height, mutableListOf(Layer("Layer 1", mutableListOf(cel))), fps,
                GridSettings(tileWidth = tile, tileHeight = tile))
        }

        /** Builds a one-layer sprite from already-composited frames. */
        fun fromFrames(width: Int, height: Int, frames: List<IntArray>, fps: Int = 8, layerName: String = "Layer 1") =
            Sprite(width, height, mutableListOf(Layer(layerName, frames.map { it.copyOf() }.toMutableList())), fps)

        /** Source-over blend of [src] (scaled by [opacity]) onto [dst]. */
        fun blend(dst: Int, src: Int, opacity: Float = 1f): Int {
            val sa = ((src ushr 24) * opacity).toInt()
            if (sa <= 0) return dst
            val da = dst ushr 24
            if (sa >= 255 || da == 0) return (sa shl 24) or (src and 0xFFFFFF)
            val outA = sa + da * (255 - sa) / 255
            if (outA == 0) return 0
            fun ch(s: Int, d: Int) = (s * sa + d * da * (255 - sa) / 255) / outA
            val r = ch((src shr 16) and 0xFF, (dst shr 16) and 0xFF)
            val g = ch((src shr 8) and 0xFF, (dst shr 8) and 0xFF)
            val b = ch(src and 0xFF, dst and 0xFF)
            return (outA shl 24) or (r shl 16) or (g shl 8) or b
        }
    }
}
