package com.kimmeldevs.pixpacket.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.kimmeldevs.pixpacket.editor.EditorState
import com.kimmeldevs.pixpacket.editor.Tool
import com.kimmeldevs.pixpacket.ui.theme.CanvasBg
import com.kimmeldevs.pixpacket.ui.theme.CheckerDark
import com.kimmeldevs.pixpacket.ui.theme.CheckerLight
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** Pan/zoom of the canvas plus the precision-mode cursor (in sprite coordinates). */
class Viewport {
    var scale by mutableFloatStateOf(0f)
    var offset by mutableStateOf(Offset.Zero)
    var size by mutableStateOf(IntSize.Zero)
    var fitRequest by mutableIntStateOf(0)
    var cursor by mutableStateOf(Offset(0f, 0f))
    private var fittedFor = -1

    fun fit(w: Int, h: Int) {
        if (size.width == 0) return
        val raw = min(size.width * 0.92f / w, size.height * 0.88f / h)
        scale = if (raw >= 1f) floor(raw) else raw
        offset = Offset((size.width - w * scale) / 2f, (size.height - h * scale) / 2f)
        cursor = Offset(w / 2f, h / 2f)
    }

    fun ensureFitted(w: Int, h: Int) {
        val key = fitRequest * 1_000_003 + w * 1009 + h
        if (fittedFor != key && size.width > 0) { fittedFor = key; fit(w, h) }
    }

    fun toSprite(p: Offset) = Offset((p.x - offset.x) / scale, (p.y - offset.y) / scale)

    fun zoomBy(factor: Float, focus: Offset) {
        val ns = (scale * factor).coerceIn(0.5f, 96f)
        val f = ns / scale
        offset = focus - (focus - offset) * f
        scale = ns
    }
}

/** Draws an ARGB pixel array, re-reading [pixels] on every draw so state reads inside it trigger redraws. */
class PixelBuffer(val w: Int, val h: Int) {
    private val bmp: Bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val image: ImageBitmap = bmp.asImageBitmap()
    fun update(px: IntArray) = bmp.setPixels(px, 0, w, 0, 0, w, h)
}

fun DrawScope.drawPixels(buf: PixelBuffer, dst: IntOffset, dstSize: IntSize, alpha: Float = 1f) =
    drawImage(buf.image, IntOffset.Zero, IntSize(buf.w, buf.h), dst, dstSize, alpha = alpha, filterQuality = FilterQuality.None)

fun checkerPixels(w: Int, h: Int): IntArray {
    val cell = max(1, max(w, h) / 8)
    val a = CheckerLight.toArgb(); val b = CheckerDark.toArgb()
    return IntArray(w * h) { i -> if (((i % w) / cell + (i / w) / cell) % 2 == 0) a else b }
}

@Composable
fun PixelCanvas(state: EditorState, viewport: Viewport, modifier: Modifier = Modifier) {
    val haptics = LocalHapticFeedback.current
    val w = state.width; val h = state.height
    val main = remember(w, h) { PixelBuffer(w, h) }
    val onionPrev = remember(w, h) { PixelBuffer(w, h) }
    val onionNext = remember(w, h) { PixelBuffer(w, h) }
    val checker = remember(w, h) { PixelBuffer(w, h).also { it.update(checkerPixels(w, h)) } }
    val scratch = remember(w, h) { IntArray(w * h) }

    // Marching-ants outline of the selection, rebuilt when the selection changes.
    val selection = state.selection
    val selPath = remember(selection, w, h) { selection?.let { selectionEdges(it, w, h) } }

    Canvas(
        modifier
            .fillMaxSize()
            .background(CanvasBg)
            .pointerInput(state, viewport) { canvasGestures(state, viewport) { haptics.performHapticFeedback(it) } }
    ) {
        viewport.size = IntSize(size.width.toInt(), size.height.toInt())
        viewport.ensureFitted(w, h)
        state.version // observe edits
        val frame = state.currentFrame
        val s = viewport.scale
        val o = viewport.offset
        val dstSize = IntSize((w * s).toInt().coerceAtLeast(1), (h * s).toInt().coerceAtLeast(1))
        val dst = IntOffset(o.x.toInt(), o.y.toInt())

        main.update(state.sprite.composite(frame, scratch))

        if (state.tileMode) {
            for (ty in -1..1) for (tx in -1..1) {
                if (tx == 0 && ty == 0) continue
                val d = IntOffset(dst.x + tx * dstSize.width, dst.y + ty * dstSize.height)
                drawPixels(checker, d, dstSize, 0.5f)
                drawPixels(main, d, dstSize, 0.55f)
            }
        }

        drawPixels(checker, dst, dstSize)
        if (state.onionSkin && state.frameCount > 1 && !state.playing) {
            if (frame > 0) {
                onionPrev.update(state.sprite.composite(frame - 1, scratch))
                drawPixels(onionPrev, dst, dstSize, 0.3f)
            }
            if (frame < state.frameCount - 1) {
                onionNext.update(state.sprite.composite(frame + 1, scratch))
                drawPixels(onionNext, dst, dstSize, 0.18f)
            }
        }
        drawPixels(main, dst, dstSize)

        if (state.showGrid && s >= 7f) drawGrid(w, h, s, o)

        // Sprite border
        drawRect(Color.White.copy(alpha = 0.25f), Offset(dst.x.toFloat(), dst.y.toFloat()),
            Size(dstSize.width.toFloat(), dstSize.height.toFloat()), style = Stroke(1f))

        if (selPath != null) {
            val (dx, dy) = state.selectionOffset
            withTransform({ translate(o.x + dx * s, o.y + dy * s); scale(s, s, Offset.Zero) }) {
                drawPath(selPath, Color.Black, style = Stroke(2f / s))
                drawPath(selPath, Color.White, style = Stroke(2f / s, pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f / s, 4f / s))))
            }
        }
        state.selectionDraftRect?.let { r ->
            val l = min(r[0], r[2]); val t = min(r[1], r[3])
            val rr = max(r[0], r[2]) + 1; val b = max(r[1], r[3]) + 1
            val tl = Offset(o.x + l * s, o.y + t * s); val sz = Size((rr - l) * s, (b - t) * s)
            drawRect(Color.Black, tl, sz, style = Stroke(2f))
            drawRect(Color.White, tl, sz, style = Stroke(2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))))
        }
        if (state.lassoPoints.size > 1) {
            val p = Path()
            state.lassoPoints.forEachIndexed { i, (x, y) ->
                val sp = Offset(o.x + x * s, o.y + y * s)
                if (i == 0) p.moveTo(sp.x, sp.y) else p.lineTo(sp.x, sp.y)
            }
            drawPath(p, Color.Black, style = Stroke(3f))
            drawPath(p, Color.White, style = Stroke(2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f))))
        }

        if (state.precisionMode) {
            val c = viewport.cursor
            val px = floor(c.x); val py = floor(c.y)
            val tl = Offset(o.x + px * s, o.y + py * s)
            drawRect(Color.Black, tl, Size(s, s), style = Stroke(3f))
            drawRect(Color.White, tl, Size(s, s), style = Stroke(1.5f))
            val cp = Offset(o.x + c.x * s, o.y + c.y * s)
            val arm = max(14f, s * 1.2f)
            drawLine(Color.White, cp - Offset(arm, 0f), cp - Offset(s * 0.6f, 0f), 2f)
            drawLine(Color.White, cp + Offset(s * 0.6f, 0f), cp + Offset(arm, 0f), 2f)
            drawLine(Color.White, cp - Offset(0f, arm), cp - Offset(0f, s * 0.6f), 2f)
            drawLine(Color.White, cp + Offset(0f, s * 0.6f), cp + Offset(0f, arm), 2f)
        }
    }
}

private fun DrawScope.drawGrid(w: Int, h: Int, s: Float, o: Offset) {
    val minor = Color.Black.copy(alpha = 0.16f)
    val major = Color.Black.copy(alpha = 0.38f)
    val left = max(0, floor(-o.x / s).toInt()); val right = min(w, ((size.width - o.x) / s + 1f).toInt())
    val top = max(0, floor(-o.y / s).toInt()); val bottom = min(h, ((size.height - o.y) / s + 1f).toInt())
    for (x in left..right) {
        val sx = o.x + x * s
        drawLine(if (x % 8 == 0) major else minor, Offset(sx, o.y + top * s), Offset(sx, o.y + bottom * s), 1f)
    }
    for (y in top..bottom) {
        val sy = o.y + y * s
        drawLine(if (y % 8 == 0) major else minor, Offset(o.x + left * s, sy), Offset(o.x + right * s, sy), 1f)
    }
}

/** Builds a path (in sprite units) along every edge between selected and unselected pixels. */
fun selectionEdges(mask: BooleanArray, w: Int, h: Int): Path {
    val p = Path()
    fun sel(x: Int, y: Int) = x in 0 until w && y in 0 until h && mask[y * w + x]
    for (y in 0 until h) for (x in 0 until w) {
        if (!mask[y * w + x]) continue
        if (!sel(x, y - 1)) { p.moveTo(x.toFloat(), y.toFloat()); p.lineTo(x + 1f, y.toFloat()) }
        if (!sel(x, y + 1)) { p.moveTo(x.toFloat(), y + 1f); p.lineTo(x + 1f, y + 1f) }
        if (!sel(x - 1, y)) { p.moveTo(x.toFloat(), y.toFloat()); p.lineTo(x.toFloat(), y + 1f) }
        if (!sel(x + 1, y)) { p.moveTo(x + 1f, y.toFloat()); p.lineTo(x + 1f, y + 1f) }
    }
    return p
}

private enum class Mode { Pending, Draw, Transform, Done }

private const val DRAW_DELAY_MS = 60L
private const val LONG_PRESS_MS = 480L
private const val TAP_MS = 280L

/**
 * Gesture rules (designed so fingers never fight each other):
 *  - 1 finger: draw with the current tool (starts after a tiny delay so a pinch never leaves a stray pixel)
 *  - long-press without moving: eyedropper
 *  - 2 fingers: pinch to zoom, drag to pan; quick 2-finger tap = undo, 3-finger tap = redo
 *  - precision mode: 1 finger moves the cursor like a trackpad; the on-screen Draw button paints
 */
private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.canvasGestures(
    state: EditorState,
    vp: Viewport,
    haptic: (HapticFeedbackType) -> Unit,
) {
    val slop = viewConfiguration.touchSlop
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        val t0 = down.uptimeMillis
        val start = down.position
        var mode = Mode.Pending
        var maxPointers = 1
        var travelled = 0f
        var lastCentroid = Offset.Unspecified
        var lastSpan = 0f
        var strokeStart = 0L
        var stillForLongPress = true

        fun centroid(ps: List<PointerInputChange>): Offset {
            var x = 0f; var y = 0f
            ps.forEach { x += it.position.x; y += it.position.y }
            return Offset(x / ps.size, y / ps.size)
        }
        fun span(ps: List<PointerInputChange>, c: Offset): Float =
            ps.map { hypot(it.position.x - c.x, it.position.y - c.y) }.average().toFloat()

        if (state.precisionMode) {
            var last = start
            while (true) {
                val ev = awaitPointerEvent()
                val pressed = ev.changes.filter { it.pressed }
                if (pressed.isEmpty()) {
                    if (mode == Mode.Transform && maxPointers >= 2 && ev.changes.first().uptimeMillis - t0 < TAP_MS && travelled < slop * 2) {
                        if (maxPointers == 2) state.undo() else state.redo()
                        haptic(HapticFeedbackType.LongPress)
                    }
                    break
                }
                maxPointers = max(maxPointers, pressed.size)
                if (pressed.size >= 2) {
                    mode = Mode.Transform
                    val c = centroid(pressed); val sp = span(pressed, c)
                    if (lastCentroid.isSpecified && lastSpan > 0f) {
                        travelled += (c - lastCentroid).getDistance()
                        vp.offset += c - lastCentroid
                        if (sp > 0f) vp.zoomBy(sp / lastSpan, c)
                    }
                    lastCentroid = c; lastSpan = sp
                } else if (mode != Mode.Transform) {
                    val p = pressed.first().position
                    val d = (p - last) / vp.scale
                    last = p
                    vp.cursor = Offset((vp.cursor.x + d.x).coerceIn(0f, state.width - 0.01f), (vp.cursor.y + d.y).coerceIn(0f, state.height - 0.01f))
                    state.moveStroke(vp.cursor.x, vp.cursor.y)
                }
                ev.changes.forEach { it.consume() }
            }
            return@awaitEachGesture
        }

        while (true) {
            val timeout = when {
                mode == Mode.Pending -> DRAW_DELAY_MS
                mode == Mode.Draw && stillForLongPress -> max(1L, LONG_PRESS_MS - (System.currentTimeMillis() - strokeStart))
                else -> Long.MAX_VALUE
            }
            val ev = if (timeout == Long.MAX_VALUE) awaitPointerEvent() else withTimeoutOrNull(timeout) { awaitPointerEvent() }

            if (ev == null) {
                // Timer fired with no new input.
                if (mode == Mode.Pending) {
                    mode = Mode.Draw
                    strokeStart = System.currentTimeMillis()
                    val p = vp.toSprite(start); state.beginStroke(p.x, p.y)
                } else if (mode == Mode.Draw && stillForLongPress) {
                    stillForLongPress = false
                    if (state.tool !in setOf(Tool.PICKER, Tool.MOVE, Tool.SELECT_RECT, Tool.LASSO, Tool.GRADIENT)) {
                        val p = vp.toSprite(start)
                        state.quickPick(p.x, p.y)
                        haptic(HapticFeedbackType.LongPress)
                        mode = Mode.Done
                    }
                }
                continue
            }

            val pressed = ev.changes.filter { it.pressed }
            maxPointers = max(maxPointers, pressed.size)

            if (pressed.isEmpty()) {
                when (mode) {
                    Mode.Pending -> { // quick tap
                        val p = vp.toSprite(start); state.beginStroke(p.x, p.y); state.endStroke()
                    }
                    Mode.Draw -> state.endStroke()
                    Mode.Transform -> {
                        val dt = ev.changes.first().uptimeMillis - t0
                        if (maxPointers >= 2 && dt < TAP_MS && travelled < slop * 2) {
                            if (maxPointers == 2) state.undo() else state.redo()
                            haptic(HapticFeedbackType.LongPress)
                        }
                    }
                    Mode.Done -> {}
                }
                ev.changes.forEach { it.consume() }
                break
            }

            if (pressed.size >= 2 && mode != Mode.Transform && mode != Mode.Done) {
                // A second finger arrived: cancel a just-started stroke and switch to pan/zoom.
                if (mode == Mode.Draw) {
                    if (System.currentTimeMillis() - strokeStart < 350) state.cancelStroke() else { state.endStroke() }
                }
                mode = Mode.Transform
                lastCentroid = Offset.Unspecified
            }

            when (mode) {
                Mode.Pending -> {
                    val p = pressed.first().position
                    if ((p - start).getDistance() > slop * 0.5f) {
                        mode = Mode.Draw
                        strokeStart = System.currentTimeMillis()
                        stillForLongPress = false
                        val s0 = vp.toSprite(start); state.beginStroke(s0.x, s0.y)
                        val s1 = vp.toSprite(p); state.moveStroke(s1.x, s1.y)
                    }
                }
                Mode.Draw -> {
                    val p = pressed.first().position
                    if (stillForLongPress && (p - start).getDistance() > slop) stillForLongPress = false
                    val sp = vp.toSprite(p); state.moveStroke(sp.x, sp.y)
                }
                Mode.Transform -> {
                    val c = centroid(pressed); val sp = span(pressed, c)
                    if (lastCentroid.isSpecified && lastSpan > 0f && pressed.size >= 2) {
                        travelled += (c - lastCentroid).getDistance() + kotlin.math.abs(sp - lastSpan)
                        vp.offset += c - lastCentroid
                        if (sp > 0f) vp.zoomBy(sp / lastSpan, c)
                    }
                    lastCentroid = if (pressed.size >= 2) c else Offset.Unspecified
                    lastSpan = sp
                }
                Mode.Done -> {}
            }
            ev.changes.forEach { it.consume() }
        }
    }
}
