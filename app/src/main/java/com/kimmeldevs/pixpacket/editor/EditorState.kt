package com.kimmeldevs.pixpacket.editor

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.kimmeldevs.pixpacket.model.ColorUtil
import com.kimmeldevs.pixpacket.model.Layer
import com.kimmeldevs.pixpacket.model.Raster
import com.kimmeldevs.pixpacket.model.Sprite
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.random.Random

private const val MAX_UNDO = 60
private const val BLACK = 0xFF000000.toInt()
private const val WHITE = 0xFFFFFFFF.toInt()

private sealed interface Edit
private class CelEdit(
    val layer: Int, val frame: Int,
    val before: IntArray, val after: IntArray,
    val selBefore: BooleanArray?, val selAfter: BooleanArray?,
) : Edit
private class DocEdit(
    val before: Sprite, val after: Sprite,
    val frameBefore: Int, val layerBefore: Int, val frameAfter: Int, val layerAfter: Int,
) : Edit

class Clip(val w: Int, val h: Int, val x: Int, val y: Int, val pixels: IntArray)

/** All editor state + every editing operation. UI observes the Compose state fields. */
class EditorState(val projectId: String, initial: Sprite, name: String) {
    var sprite by mutableStateOf(initial); private set
    var name by mutableStateOf(name)

    /** Bumped whenever pixels change; UI re-renders on change. */
    var version by mutableIntStateOf(0); private set
    var currentFrame by mutableIntStateOf(0)
    var currentLayer by mutableIntStateOf(0)

    var tool by mutableStateOf(Tool.PENCIL)
    var primary by mutableIntStateOf(BLACK)
    var secondary by mutableIntStateOf(WHITE)
    val recentColors = mutableStateListOf<Int>()

    // Tool options
    var brushSize by mutableIntStateOf(1)
    var pixelPerfect by mutableStateOf(true)
    var mirrorX by mutableStateOf(false)
    var mirrorY by mutableStateOf(false)
    var filledShapes by mutableStateOf(false)
    var contiguous by mutableStateOf(true)
    var lighten by mutableStateOf(true)

    // View options
    var onionSkin by mutableStateOf(false)
    var showGrid by mutableStateOf(true)
    var tileMode by mutableStateOf(false)
    var precisionMode by mutableStateOf(false)
    var showPreview by mutableStateOf(true)
    var playing by mutableStateOf(false)

    // Selection
    var selection by mutableStateOf<BooleanArray?>(null); private set
    /** Live selection rectangle while dragging (l,t,r,b), or lasso points. */
    var selectionDraftRect by mutableStateOf<IntArray?>(null); private set
    val lassoPoints = mutableStateListOf<Pair<Float, Float>>()
    var selectionOffset by mutableStateOf(0 to 0); private set
    private var clipboard: Clip? = null
    var hasClipboard by mutableStateOf(false); private set

    // Undo
    private val undoStack = ArrayDeque<Edit>()
    private val redoStack = ArrayDeque<Edit>()
    var canUndo by mutableStateOf(false); private set
    var canRedo by mutableStateOf(false); private set
    var dirty = false; private set

    /** One-shot messages for the UI (snackbar). */
    var message by mutableStateOf<String?>(null)

    // Reading `version` makes Compose re-read these after in-place structural edits.
    val width get() = version.let { sprite.width }
    val height get() = version.let { sprite.height }
    val frameCount get() = version.let { sprite.frameCount }
    val layer: Layer get() = version.let { sprite.layers[currentLayer] }
    val cel: IntArray get() = layer.cels[currentFrame]

    private fun changed() { version++; dirty = true }
    fun markSaved() { dirty = false }

    // region Undo / redo

    private fun push(e: Edit) {
        undoStack.addLast(e)
        if (undoStack.size > MAX_UNDO) undoStack.removeFirst()
        redoStack.clear()
        syncUndo()
    }

    private fun syncUndo() { canUndo = undoStack.isNotEmpty(); canRedo = redoStack.isNotEmpty() }

    fun undo() {
        cancelStroke()
        val e = undoStack.removeLastOrNull() ?: return
        apply(e, undo = true)
        redoStack.addLast(e)
        syncUndo()
    }

    fun redo() {
        cancelStroke()
        val e = redoStack.removeLastOrNull() ?: return
        apply(e, undo = false)
        undoStack.addLast(e)
        syncUndo()
    }

    private fun apply(e: Edit, undo: Boolean) {
        when (e) {
            is CelEdit -> {
                val src = if (undo) e.before else e.after
                src.copyInto(sprite.layers[e.layer].cels[e.frame])
                selection = (if (undo) e.selBefore else e.selAfter)?.copyOf()
                currentLayer = e.layer; currentFrame = e.frame
            }
            is DocEdit -> {
                sprite = (if (undo) e.before else e.after).deepCopy()
                currentFrame = (if (undo) e.frameBefore else e.frameAfter).coerceIn(0, sprite.frameCount - 1)
                currentLayer = (if (undo) e.layerBefore else e.layerAfter).coerceIn(0, sprite.layers.size - 1)
                if (selection?.size != sprite.width * sprite.height) selection = null
            }
        }
        changed()
    }

    /** Runs a structural change and records a whole-document undo step. */
    private inline fun structural(block: () -> Unit) {
        cancelStroke()
        val before = sprite.deepCopy()
        val fb = currentFrame; val lb = currentLayer
        block()
        currentFrame = currentFrame.coerceIn(0, sprite.frameCount - 1)
        currentLayer = currentLayer.coerceIn(0, sprite.layers.size - 1)
        push(DocEdit(before, sprite.deepCopy(), fb, lb, currentFrame, currentLayer))
        changed()
    }

    /** Runs a change to the current cel and records a cel undo step. */
    private inline fun celChange(block: (IntArray) -> Unit) {
        if (!canEditLayer()) return
        cancelStroke()
        val before = cel.copyOf()
        val selBefore = selection?.copyOf()
        block(cel)
        if (!before.contentEquals(cel) || !selBefore.contentEqualsNullable(selection)) {
            push(CelEdit(currentLayer, currentFrame, before, cel.copyOf(), selBefore, selection?.copyOf()))
            changed()
        }
    }

    private fun BooleanArray?.contentEqualsNullable(o: BooleanArray?) =
        if (this == null) o == null else o != null && contentEquals(o)

    // endregion

    // region Strokes (touch drawing)

    private var strokeActive = false
    private var pressActive = false
    private var strokeBase: IntArray? = null
    private var strokeSelBefore: BooleanArray? = null
    private var touched: BooleanArray? = null
    private var sx = 0; private var sy = 0
    private var lx = 0; private var ly = 0
    private var sfx = 0f; private var sfy = 0f
    private val ppPoints = ArrayList<Long>()
    private var floating: IntArray? = null
    private var moveMask: BooleanArray? = null
    private var gradientRegion: BooleanArray? = null
    private val random = Random(7)

    private fun canEditLayer(): Boolean {
        if (layer.locked) { message = "Layer \"${layer.name}\" is locked"; return false }
        if (!layer.visible) { message = "Layer \"${layer.name}\" is hidden"; return false }
        return true
    }

    fun beginStroke(fx: Float, fy: Float) {
        if (playing) playing = false
        pressActive = true
        val x = floor(fx).toInt(); val y = floor(fy).toInt()
        sx = x; sy = y; lx = x; ly = y; sfx = fx; sfy = fy
        when (tool) {
            Tool.PICKER -> { pickColor(x, y); return }
            Tool.SELECT_RECT -> { selectionDraftRect = intArrayOf(x, y, x, y); return }
            Tool.LASSO -> { lassoPoints.clear(); lassoPoints.add(fx to fy); return }
            Tool.WAND -> {
                if (x !in 0 until width || y !in 0 until height) { deselect(); return }
                selection = Raster.matchRegion(cel, width, height, x, y, contiguous)
                return
            }
            else -> {}
        }
        if (!canEditLayer()) return
        strokeActive = true
        strokeBase = cel.copyOf()
        strokeSelBefore = selection?.copyOf()
        touched = BooleanArray(width * height)
        ppPoints.clear()
        when (tool) {
            Tool.PENCIL, Tool.ERASER, Tool.SHADE, Tool.DITHER -> {
                ppPoints.add(pack(x, y))
                stamp(x, y)
            }
            Tool.SPRAY -> spray(x, y)
            Tool.BUCKET -> fill(x, y)
            Tool.MOVE -> {
                val sel = selection
                if (sel != null) {
                    val base = strokeBase!!
                    floating = IntArray(base.size) { if (sel[it]) base[it] else 0 }
                    moveMask = sel.copyOf()
                } else { floating = null; moveMask = null }
            }
            Tool.GRADIENT -> {
                gradientRegion = selection?.copyOf()
                    ?: Raster.matchRegion(strokeBase!!, width, height, x.coerceIn(0, width - 1), y.coerceIn(0, height - 1), true)
                drawGradient(fx, fy)
            }
            Tool.LINE, Tool.RECT, Tool.ELLIPSE -> drawShape(x, y)
            else -> {}
        }
        version++
    }

    fun moveStroke(fx: Float, fy: Float) {
        if (!pressActive) return
        val x = floor(fx).toInt(); val y = floor(fy).toInt()
        when (tool) {
            Tool.PICKER -> { pickColor(x, y); return }
            Tool.SELECT_RECT -> { selectionDraftRect = intArrayOf(sx, sy, x, y); return }
            Tool.LASSO -> {
                val last = lassoPoints.lastOrNull()
                if (last == null || (last.first - fx) * (last.first - fx) + (last.second - fy) * (last.second - fy) > 0.25f)
                    lassoPoints.add(fx to fy)
                return
            }
            else -> {}
        }
        if (!strokeActive) return
        when (tool) {
            Tool.PENCIL, Tool.ERASER, Tool.SHADE, Tool.DITHER -> {
                if (x == lx && y == ly) return
                val usePP = pixelPerfect && brushSize == 1 && tool != Tool.SHADE
                Raster.line(lx, ly, x, y) { px, py ->
                    if (px == lx && py == ly) return@line
                    stamp(px, py)
                    if (usePP) pixelPerfectFix(px, py)
                }
            }
            Tool.SPRAY -> spray(x, y)
            Tool.LINE, Tool.RECT, Tool.ELLIPSE -> {
                if (x == lx && y == ly) return
                strokeBase!!.copyInto(cel); drawShape(x, y)
            }
            Tool.MOVE -> {
                val dx = (fx - sfx).roundToInt(); val dy = (fy - sfy).roundToInt()
                if (dx == lx - sx && dy == ly - sy && version > 0) { lx = sx + dx; ly = sy + dy; return }
                applyMove(dx, dy)
                lx = sx + dx; ly = sy + dy
                version++
                return
            }
            Tool.GRADIENT -> drawGradient(fx, fy)
            else -> {}
        }
        lx = x; ly = y
        version++
    }

    fun endStroke() {
        if (!pressActive) return
        pressActive = false
        when (tool) {
            Tool.SELECT_RECT -> {
                val r = selectionDraftRect
                selectionDraftRect = null
                if (r == null) return
                if (r[0] == r[2] && r[1] == r[3]) { deselect(); return }
                val l = minOf(r[0], r[2]).coerceIn(0, width - 1); val rr = maxOf(r[0], r[2]).coerceIn(0, width - 1)
                val t = minOf(r[1], r[3]).coerceIn(0, height - 1); val b = maxOf(r[1], r[3]).coerceIn(0, height - 1)
                selection = BooleanArray(width * height) { i -> i % width in l..rr && i / width in t..b }
                return
            }
            Tool.LASSO -> {
                val pts = lassoPoints.toList()
                lassoPoints.clear()
                val mask = Raster.polygonMask(pts.map { it.first }, pts.map { it.second }, width, height)
                selection = if (mask.any { it }) mask else null
                return
            }
            Tool.PICKER -> { addRecent(primary); return }
            else -> {}
        }
        if (!strokeActive) return
        strokeActive = false
        if (tool == Tool.MOVE) {
            val m = moveMask
            if (m != null) {
                val (dx, dy) = selectionOffset
                selection = shiftMask(m, dx, dy)
            }
            selectionOffset = 0 to 0
            floating = null; moveMask = null
        }
        val base = strokeBase!!
        strokeBase = null
        if (!base.contentEquals(cel) || !strokeSelBefore.contentEqualsNullable(selection)) {
            push(CelEdit(currentLayer, currentFrame, base, cel.copyOf(), strokeSelBefore, selection?.copyOf()))
            changed()
            if (tool in setOf(Tool.PENCIL, Tool.BUCKET, Tool.LINE, Tool.RECT, Tool.ELLIPSE, Tool.SPRAY)) addRecent(primary)
        }
    }

    /** Aborts the in-progress stroke without recording it (e.g. a second finger touched down). */
    fun cancelStroke() {
        pressActive = false
        selectionDraftRect = null
        lassoPoints.clear()
        if (!strokeActive) return
        strokeActive = false
        strokeBase?.copyInto(cel)
        strokeBase = null
        if (tool == Tool.MOVE) { selectionOffset = 0 to 0; floating = null; moveMask = null }
        version++
    }

    private fun pack(x: Int, y: Int) = (x.toLong() shl 32) or (y.toLong() and 0xFFFFFFFFL)
    private fun ux(p: Long) = (p shr 32).toInt()
    private fun uy(p: Long) = p.toInt()

    /** Removes L-shaped corners so freehand lines stay 1px clean (Aseprite "pixel-perfect"). */
    private fun pixelPerfectFix(x: Int, y: Int) {
        ppPoints.add(pack(x, y))
        val n = ppPoints.size
        if (n < 3) return
        val a = ppPoints[n - 3]; val b = ppPoints[n - 2]; val c = ppPoints[n - 1]
        val adx = kotlin.math.abs(ux(a) - ux(c)); val ady = kotlin.math.abs(uy(a) - uy(c))
        val bOrthoA = ux(a) == ux(b) || uy(a) == uy(b)
        val bOrthoC = ux(c) == ux(b) || uy(c) == uy(b)
        if (adx == 1 && ady == 1 && bOrthoA && bOrthoC) {
            restoreMirrored(ux(b), uy(b))
            ppPoints.removeAt(n - 2)
        }
    }

    private fun restoreMirrored(x: Int, y: Int) {
        val base = strokeBase ?: return
        forEachMirror(x, y) { px, py ->
            if (px in 0 until width && py in 0 until height) {
                val i = py * width + px
                cel[i] = base[i]; touched!![i] = false
            }
        }
    }

    private inline fun forEachMirror(x: Int, y: Int, f: (Int, Int) -> Unit) {
        f(x, y)
        if (mirrorX) f(width - 1 - x, y)
        if (mirrorY) f(x, height - 1 - y)
        if (mirrorX && mirrorY) f(width - 1 - x, height - 1 - y)
    }

    private fun inSelection(i: Int) = selection?.get(i) ?: true

    /** Applies the current tool's ink at one pixel. */
    private fun ink(px: Int, py: Int) {
        if (px !in 0 until width || py !in 0 until height) return
        val i = py * width + px
        if (!inSelection(i)) return
        val t = touched!!
        cel[i] = when (tool) {
            Tool.ERASER -> 0
            Tool.SHADE -> { if (t[i]) return; ColorUtil.shade(strokeBase!![i], lighten) }
            Tool.DITHER -> if ((px + py) % 2 == 0) primary else secondary
            else -> primary
        }
        t[i] = true
    }

    /** Stamps a square brush of [brushSize] (with mirroring) centered at x,y. */
    private fun stamp(x: Int, y: Int) {
        val s = brushSize
        val off = (s - 1) / 2
        forEachMirror(x, y) { mx, my ->
            for (dy in 0 until s) for (dx in 0 until s) ink(mx - off + dx, my - off + dy)
        }
    }

    private fun spray(x: Int, y: Int) {
        val r = brushSize * 2 + 1
        repeat(brushSize * 2 + 2) {
            val dx = random.nextInt(-r, r + 1); val dy = random.nextInt(-r, r + 1)
            if (dx * dx + dy * dy <= r * r) forEachMirror(x + dx, y + dy) { px, py -> ink(px, py) }
        }
    }

    private fun drawShape(x: Int, y: Int) {
        touched!!.fill(false)
        when (tool) {
            Tool.LINE -> Raster.line(sx, sy, x, y) { px, py -> stamp(px, py) }
            Tool.RECT -> {
                if (filledShapes) Raster.rect(sx, sy, x, y, true) { px, py -> forEachMirror(px, py) { a, b -> ink(a, b) } }
                else Raster.rect(sx, sy, x, y, false) { px, py -> stamp(px, py) }
            }
            Tool.ELLIPSE -> Raster.ellipse(sx, sy, x, y, filledShapes) { px, py ->
                if (filledShapes) forEachMirror(px, py) { a, b -> ink(a, b) } else stamp(px, py)
            }
            else -> {}
        }
    }

    private fun fill(x: Int, y: Int) {
        if (x !in 0 until width || y !in 0 until height) return
        val region = Raster.matchRegion(cel, width, height, x, y, contiguous)
        for (i in region.indices) if (region[i] && inSelection(i)) cel[i] = primary
    }

    private val bayer = intArrayOf(0, 8, 2, 10, 12, 4, 14, 6, 3, 11, 1, 9, 15, 7, 13, 5)

    private fun drawGradient(fx: Float, fy: Float) {
        val region = gradientRegion ?: return
        val base = strokeBase!!
        base.copyInto(cel)
        val vx = fx - sfx; val vy = fy - sfy
        val len2 = vx * vx + vy * vy
        for (i in region.indices) {
            if (!region[i]) continue
            val px = i % width; val py = i / width
            val t = if (len2 < 0.01f) 0f else (((px + 0.5f - sfx) * vx + (py + 0.5f - sfy) * vy) / len2).coerceIn(0f, 1f)
            val threshold = (bayer[(py and 3) * 4 + (px and 3)] + 0.5f) / 16f
            cel[i] = if (t > threshold) secondary else primary
        }
    }

    private fun applyMove(dx: Int, dy: Int) {
        val base = strokeBase!!
        val fl = floating
        val m = moveMask
        if (fl == null || m == null) {
            cel.fill(0)
            for (y in 0 until height) {
                val ny = y + dy
                if (ny !in 0 until height) continue
                for (x in 0 until width) {
                    val nx = x + dx
                    if (nx in 0 until width) cel[ny * width + nx] = base[y * width + x]
                }
            }
            return
        }
        base.copyInto(cel)
        for (i in m.indices) if (m[i]) cel[i] = 0
        for (y in 0 until height) for (x in 0 until width) {
            val i = y * width + x
            if (!m[i] || fl[i] == 0) continue
            val nx = x + dx; val ny = y + dy
            if (nx in 0 until width && ny in 0 until height) cel[ny * width + nx] = fl[i]
        }
        selectionOffset = dx to dy
    }

    private fun shiftMask(m: BooleanArray, dx: Int, dy: Int): BooleanArray? {
        val out = BooleanArray(m.size)
        var any = false
        for (y in 0 until height) for (x in 0 until width) {
            if (!m[y * width + x]) continue
            val nx = x + dx; val ny = y + dy
            if (nx in 0 until width && ny in 0 until height) { out[ny * width + nx] = true; any = true }
        }
        return if (any) out else null
    }

    fun pickColor(x: Int, y: Int) {
        if (x !in 0 until width || y !in 0 until height) return
        val c = sprite.composite(currentFrame)[y * width + x]
        if (c == 0) return
        primary = c
    }

    /** Long-press eyedropper from any tool. */
    fun quickPick(fx: Float, fy: Float) {
        cancelStroke()
        pickColor(floor(fx).toInt(), floor(fy).toInt())
        addRecent(primary)
        message = "Picked ${ColorUtil.toHex(primary)}"
    }

    // endregion

    // region Colors

    fun swapColors() { val p = primary; primary = secondary; secondary = p }

    fun addRecent(c: Int) {
        if (c == 0) return
        recentColors.remove(c)
        recentColors.add(0, c)
        while (recentColors.size > 16) recentColors.removeAt(recentColors.lastIndex)
    }

    /** Distinct colors used across the whole sprite (Piskel's "current colors" palette). */
    fun usedColors(limit: Int = 64): List<Int> {
        val set = LinkedHashSet<Int>()
        for (l in sprite.layers) for (c in l.cels) for (p in c) {
            if (p != 0) { set.add(p); if (set.size >= limit) return set.toList() }
        }
        return set.toList()
    }

    /** Replaces one color with another in every cel (Aseprite "Replace color"). */
    fun replaceColorEverywhere(from: Int, to: Int) = structural {
        for (l in sprite.layers) for (c in l.cels) for (i in c.indices) if (c[i] == from) c[i] = to
    }

    // endregion

    // region Selection actions

    fun selectAll() { selection = BooleanArray(width * height) { true } }
    fun deselect() { selection = null }
    fun invertSelection() {
        val s = selection
        selection = if (s == null) null else BooleanArray(s.size) { !s[it] }.takeIf { m -> m.any { it } }
    }

    fun copy() {
        val sel = selection ?: BooleanArray(width * height) { true }
        val b = Raster.bounds(sel, width, height) ?: return
        val w = b[2] - b[0] + 1; val h = b[3] - b[1] + 1
        val px = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            val i = (y + b[1]) * width + (x + b[0])
            if (sel[i]) px[y * w + x] = cel[i]
        }
        clipboard = Clip(w, h, b[0], b[1], px)
        hasClipboard = true
        message = "Copied ${w}×$h"
    }

    fun cut() { copy(); deleteSelected() }

    fun paste() {
        val c = clipboard ?: return
        if (!canEditLayer()) return
        celChange { cel ->
            val x0 = c.x.coerceIn(0, maxOf(0, width - c.w)); val y0 = c.y.coerceIn(0, maxOf(0, height - c.h))
            val mask = BooleanArray(width * height)
            for (y in 0 until c.h) for (x in 0 until c.w) {
                val nx = x0 + x; val ny = y0 + y
                if (nx !in 0 until width || ny !in 0 until height) continue
                val p = c.pixels[y * c.w + x]
                if (p != 0) cel[ny * width + nx] = p
                mask[ny * width + nx] = true
            }
            selection = mask
        }
        tool = Tool.MOVE
        message = "Pasted — drag to position"
    }

    fun deleteSelected() = celChange { cel ->
        val sel = selection
        for (i in cel.indices) if (sel == null || sel[i]) cel[i] = 0
    }

    /** Fills the selection (or whole cel) with the primary color. */
    fun fillSelection() = celChange { cel ->
        for (i in cel.indices) if (inSelection(i)) cel[i] = primary
    }

    /** Flips the selected pixels (or the whole cel) in place. */
    fun flipSelection(horizontal: Boolean) {
        val sel = selection
        if (sel == null) { celChange { c -> (if (horizontal) Raster.flipH(c, width, height) else Raster.flipV(c, width, height)).copyInto(c) }; return }
        val b = Raster.bounds(sel, width, height) ?: return
        celChange { c ->
            val src = c.copyOf()
            val newSel = BooleanArray(sel.size)
            for (i in sel.indices) if (sel[i]) c[i] = 0
            for (y in 0 until height) for (x in 0 until width) {
                val i = y * width + x
                if (!sel[i]) continue
                val nx = if (horizontal) b[0] + b[2] - x else x
                val ny = if (horizontal) y else b[1] + b[3] - y
                val j = ny * width + nx
                c[j] = src[i]; newSel[j] = true
            }
            selection = newSel
        }
    }

    /** Rotates the selection (or the cel, if square) 90° clockwise around its center. */
    fun rotateSelection() {
        val sel = selection ?: BooleanArray(width * height) { true }
        val b = Raster.bounds(sel, width, height) ?: return
        val bw = b[2] - b[0] + 1; val bh = b[3] - b[1] + 1
        val nl = b[0] + (bw - bh) / 2; val nt = b[1] + (bh - bw) / 2
        val hadSel = selection != null
        celChange { c ->
            val src = c.copyOf()
            val newSel = BooleanArray(sel.size)
            for (i in sel.indices) if (sel[i]) c[i] = 0
            for (y in b[1]..b[3]) for (x in b[0]..b[2]) {
                val i = y * width + x
                if (!sel[i]) continue
                val rx = x - b[0]; val ry = y - b[1]
                val nx = nl + (bh - 1 - ry); val ny = nt + rx
                if (nx !in 0 until width || ny !in 0 until height) continue
                c[ny * width + nx] = src[i]; newSel[ny * width + nx] = true
            }
            if (hadSel) selection = newSel
        }
    }

    fun outline() = celChange { c -> Raster.outline(c, width, height, primary, selection).copyInto(c) }

    fun clearCel() = celChange { c -> c.fill(0) }

    // endregion

    // region Frames

    fun selectFrame(i: Int) { cancelStroke(); currentFrame = i.coerceIn(0, frameCount - 1) }

    fun addFrame() = structural {
        val at = currentFrame + 1
        for (l in sprite.layers) l.cels.add(at, sprite.newCel())
        currentFrame = at
    }

    fun duplicateFrame() = structural {
        val at = currentFrame + 1
        for (l in sprite.layers) l.cels.add(at, l.cels[currentFrame].copyOf())
        currentFrame = at
    }

    fun deleteFrame() {
        if (frameCount <= 1) { message = "A sprite needs at least one frame"; return }
        structural {
            for (l in sprite.layers) l.cels.removeAt(currentFrame)
            currentFrame = (currentFrame - 1).coerceAtLeast(0)
        }
    }

    fun moveFrame(delta: Int) {
        val to = currentFrame + delta
        if (to !in 0 until frameCount) return
        structural {
            for (l in sprite.layers) { val c = l.cels.removeAt(currentFrame); l.cels.add(to, c) }
            currentFrame = to
        }
    }

    fun setFps(fps: Int) { sprite.fps = fps.coerceIn(1, 30); version++; dirty = true }

    // endregion

    // region Layers

    fun selectLayer(i: Int) { cancelStroke(); currentLayer = i.coerceIn(0, sprite.layers.size - 1) }

    fun addLayer() = structural {
        val at = currentLayer + 1
        val n = (sprite.layers.size + 1)
        sprite.layers.add(at, Layer("Layer $n", MutableList(frameCount) { sprite.newCel() }))
        currentLayer = at
    }

    fun duplicateLayer() = structural {
        val copy = layer.deepCopy().also { it.name = "${it.name} copy" }
        sprite.layers.add(currentLayer + 1, copy)
        currentLayer++
    }

    fun deleteLayer() {
        if (sprite.layers.size <= 1) { message = "A sprite needs at least one layer"; return }
        structural {
            sprite.layers.removeAt(currentLayer)
            currentLayer = (currentLayer - 1).coerceAtLeast(0)
        }
    }

    fun moveLayer(delta: Int) {
        val to = currentLayer + delta
        if (to !in sprite.layers.indices) return
        structural {
            val l = sprite.layers.removeAt(currentLayer); sprite.layers.add(to, l); currentLayer = to
        }
    }

    fun mergeDown() {
        if (currentLayer == 0) { message = "No layer below to merge into"; return }
        structural {
            val top = sprite.layers[currentLayer]
            val below = sprite.layers[currentLayer - 1]
            for (f in 0 until frameCount) {
                val dst = below.cels[f]; val src = top.cels[f]
                for (i in dst.indices) if (src[i] != 0 && top.visible) dst[i] = Sprite.blend(dst[i], src[i], top.opacity)
            }
            sprite.layers.removeAt(currentLayer)
            currentLayer--
        }
    }

    fun toggleVisible(i: Int) = structural { sprite.layers[i].visible = !sprite.layers[i].visible }
    fun toggleLock(i: Int) = structural { sprite.layers[i].locked = !sprite.layers[i].locked }
    fun renameLayer(i: Int, newName: String) = structural { sprite.layers[i].name = newName.ifBlank { sprite.layers[i].name } }

    /** Live opacity change (no undo step per slider tick); call [commitOpacity] when done. */
    private var opacityBefore: Sprite? = null
    fun setOpacityLive(i: Int, v: Float) {
        if (opacityBefore == null) opacityBefore = sprite.deepCopy()
        sprite.layers[i].opacity = v; version++
    }
    fun commitOpacity() {
        val before = opacityBefore ?: return
        opacityBefore = null
        push(DocEdit(before, sprite.deepCopy(), currentFrame, currentLayer, currentFrame, currentLayer))
        changed()
    }

    // endregion

    // region Whole-sprite transforms

    fun flipSprite(horizontal: Boolean) = structural {
        for (l in sprite.layers) for (f in l.cels.indices) {
            l.cels[f] = if (horizontal) Raster.flipH(l.cels[f], width, height) else Raster.flipV(l.cels[f], width, height)
        }
        selection = null
    }

    fun rotateSprite() = structural {
        val w = width; val h = height
        for (l in sprite.layers) for (f in l.cels.indices) l.cels[f] = Raster.rotateCW(l.cels[f], w, h)
        sprite.width = h; sprite.height = w
        selection = null
    }

    /** Anchor: 0..8 in a 3x3 grid (0 = top-left, 4 = center). */
    fun resizeCanvas(nw: Int, nh: Int, anchor: Int) = structural {
        val w = width; val h = height
        val ox = when (anchor % 3) { 0 -> 0; 1 -> (nw - w) / 2; else -> nw - w }
        val oy = when (anchor / 3) { 0 -> 0; 1 -> (nh - h) / 2; else -> nh - h }
        for (l in sprite.layers) for (f in l.cels.indices) l.cels[f] = Raster.resizeCanvas(l.cels[f], w, h, nw, nh, ox, oy)
        sprite.width = nw; sprite.height = nh
        selection = null
    }

    fun scaleSprite(nw: Int, nh: Int) = structural {
        val w = width; val h = height
        for (l in sprite.layers) for (f in l.cels.indices) l.cels[f] = Raster.scale(l.cels[f], w, h, nw, nh)
        sprite.width = nw; sprite.height = nh
        selection = null
    }

    /** Adds an imported image (already sized to the sprite) as a new layer on the current frame. */
    fun importAsLayer(pixels: IntArray, layerName: String) = structural {
        val cels = MutableList(frameCount) { sprite.newCel() }
        pixels.copyInto(cels[currentFrame])
        sprite.layers.add(currentLayer + 1, Layer(layerName, cels))
        currentLayer++
    }

    // endregion
}
