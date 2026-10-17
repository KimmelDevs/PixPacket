package com.kimmeldevs.pixpacket.model

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** Pure pixel algorithms. Coordinates are in sprite pixels; [plot] receives every covered pixel. */
object Raster {

    /** Bresenham line. */
    inline fun line(x0: Int, y0: Int, x1: Int, y1: Int, plot: (Int, Int) -> Unit) {
        var x = x0
        var y = y0
        val dx = abs(x1 - x0)
        val dy = -abs(y1 - y0)
        val sx = if (x0 < x1) 1 else -1
        val sy = if (y0 < y1) 1 else -1
        var err = dx + dy
        while (true) {
            plot(x, y)
            if (x == x1 && y == y1) break
            val e2 = 2 * err
            if (e2 >= dy) { err += dy; x += sx }
            if (e2 <= dx) { err += dx; y += sy }
        }
    }

    inline fun rect(x0: Int, y0: Int, x1: Int, y1: Int, filled: Boolean, plot: (Int, Int) -> Unit) {
        val l = min(x0, x1); val r = max(x0, x1)
        val t = min(y0, y1); val b = max(y0, y1)
        for (y in t..b) for (x in l..r) {
            if (filled || x == l || x == r || y == t || y == b) plot(x, y)
        }
    }

    /** Ellipse inscribed in a rectangle (Alois Zingl's algorithm). */
    fun ellipse(ax0: Int, ay0: Int, ax1: Int, ay1: Int, filled: Boolean, plot: (Int, Int) -> Unit) {
        var x0 = ax0.toLong(); var y0 = ay0.toLong(); var x1 = ax1.toLong(); var y1 = ay1.toLong()
        var a = abs(x1 - x0); val b = abs(y1 - y0); var b1 = b and 1
        var dx = 4 * (1 - a) * b * b
        var dy = 4 * (b1 + 1) * a * a
        var err = dx + dy + b1 * a * a
        if (x0 > x1) { x0 = x1; x1 += a }
        if (y0 > y1) y0 = y1
        y0 += (b + 1) / 2; y1 = y0 - b1
        a *= 8 * a; b1 = 8 * b * b
        fun h(xa: Long, xb: Long, y: Long) {
            if (filled) for (x in xa..xb) plot(x.toInt(), y.toInt())
            else { plot(xa.toInt(), y.toInt()); plot(xb.toInt(), y.toInt()) }
        }
        do {
            h(x0, x1, y0); h(x0, x1, y1)
            val e2 = 2 * err
            if (e2 <= dy) { y0++; y1--; dy += a; err += dy }
            if (e2 >= dx || 2 * err > dy) { x0++; x1--; dx += b1; err += dx }
        } while (x0 <= x1)
        while (y0 - y1 <= b) {
            h(x0 - 1, x1 + 1, y0++)
            h(x0 - 1, x1 + 1, y1--)
        }
    }

    /** Pixels matching the color at (sx,sy). Contiguous = 4-way flood fill, otherwise every same-colored pixel. */
    fun matchRegion(pixels: IntArray, w: Int, h: Int, sx: Int, sy: Int, contiguous: Boolean): BooleanArray {
        val out = BooleanArray(w * h)
        if (sx !in 0 until w || sy !in 0 until h) return out
        val target = pixels[sy * w + sx]
        if (!contiguous) {
            for (i in pixels.indices) if (pixels[i] == target) out[i] = true
            return out
        }
        val stack = IntArray(w * h)
        var sp = 0
        stack[sp++] = sy * w + sx
        out[sy * w + sx] = true
        while (sp > 0) {
            val i = stack[--sp]
            val x = i % w; val y = i / w
            fun visit(nx: Int, ny: Int) {
                if (nx !in 0 until w || ny !in 0 until h) return
                val j = ny * w + nx
                if (!out[j] && pixels[j] == target) { out[j] = true; stack[sp++] = j }
            }
            visit(x + 1, y); visit(x - 1, y); visit(x, y + 1); visit(x, y - 1)
        }
        return out
    }

    /** Polygon rasterization (pixel centers inside the polygon), used by the lasso. */
    fun polygonMask(xs: List<Float>, ys: List<Float>, w: Int, h: Int): BooleanArray {
        val out = BooleanArray(w * h)
        val n = xs.size
        if (n < 3) return out
        for (y in 0 until h) {
            val py = y + 0.5f
            for (x in 0 until w) {
                val px = x + 0.5f
                var inside = false
                var j = n - 1
                for (i in 0 until n) {
                    if ((ys[i] > py) != (ys[j] > py) &&
                        px < (xs[j] - xs[i]) * (py - ys[i]) / (ys[j] - ys[i]) + xs[i]
                    ) inside = !inside
                    j = i
                }
                if (inside) out[y * w + x] = true
            }
        }
        return out
    }

    fun flipH(src: IntArray, w: Int, h: Int) = IntArray(w * h) { i -> src[(i / w) * w + (w - 1 - i % w)] }
    fun flipV(src: IntArray, w: Int, h: Int) = IntArray(w * h) { i -> src[(h - 1 - i / w) * w + i % w] }

    /** Rotates 90° clockwise; result is h wide and w tall. */
    fun rotateCW(src: IntArray, w: Int, h: Int): IntArray {
        val out = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            val nx = h - 1 - y; val ny = x
            out[ny * h + nx] = src[y * w + x]
        }
        return out
    }

    /** Copies src into a new canvas of nw*nh, placing src's top-left at (ox,oy). */
    fun resizeCanvas(src: IntArray, w: Int, h: Int, nw: Int, nh: Int, ox: Int, oy: Int): IntArray {
        val out = IntArray(nw * nh)
        for (y in 0 until h) {
            val ny = y + oy
            if (ny !in 0 until nh) continue
            for (x in 0 until w) {
                val nx = x + ox
                if (nx in 0 until nw) out[ny * nw + nx] = src[y * w + x]
            }
        }
        return out
    }

    /** Nearest-neighbour scaling. */
    fun scale(src: IntArray, w: Int, h: Int, nw: Int, nh: Int) =
        IntArray(nw * nh) { i -> src[((i / nw) * h / nh) * w + (i % nw) * w / nw] }

    /** Adds a 1px outline of [color] around opaque pixels (Aseprite "Outline"). */
    fun outline(src: IntArray, w: Int, h: Int, color: Int, mask: BooleanArray?): IntArray {
        val out = src.copyOf()
        for (y in 0 until h) for (x in 0 until w) {
            val i = y * w + x
            if (src[i] != 0 || (mask != null && !mask[i])) continue
            val near = (x > 0 && src[i - 1] != 0) || (x < w - 1 && src[i + 1] != 0) ||
                (y > 0 && src[i - w] != 0) || (y < h - 1 && src[i + w] != 0)
            if (near) out[i] = color
        }
        return out
    }

    fun bounds(mask: BooleanArray, w: Int, h: Int): IntArray? {
        var l = w; var t = h; var r = -1; var b = -1
        for (y in 0 until h) for (x in 0 until w) if (mask[y * w + x]) {
            if (x < l) l = x; if (x > r) r = x; if (y < t) t = y; if (y > b) b = y
        }
        return if (r < 0) null else intArrayOf(l, t, r, b)
    }
}
