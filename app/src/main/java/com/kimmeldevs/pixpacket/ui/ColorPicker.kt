package com.kimmeldevs.pixpacket.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import com.kimmeldevs.pixpacket.model.ColorUtil

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ColorPickerSheet(
    initial: Int,
    recent: List<Int>,
    onDismiss: () -> Unit,
    onPick: (Int) -> Unit,
    onAddToPalette: (Int) -> Unit,
) {
    val hsv0 = remember { ColorUtil.toHsv(initial) }
    var hue by remember { mutableFloatStateOf(hsv0[0]) }
    var sat by remember { mutableFloatStateOf(hsv0[1]) }
    var value by remember { mutableFloatStateOf(hsv0[2]) }
    var alpha by remember { mutableFloatStateOf((initial ushr 24) / 255f) }
    val color = ColorUtil.fromHsv(hue, sat, value, (alpha * 255).toInt())
    var hexText by remember { mutableStateOf(ColorUtil.toHex(initial)) }
    var editingHex by remember { mutableStateOf(false) }
    if (!editingHex) hexText = ColorUtil.toHex(color)

    fun setColor(c: Int) {
        val h = ColorUtil.toHsv(c); hue = h[0]; sat = h[1]; value = h[2]; alpha = (c ushr 24) / 255f
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier.padding(horizontal = 20.dp).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(Modifier.clip(RoundedCornerShape(10.dp)).border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(10.dp))) {
                    ColorBox(initial, Modifier.size(44.dp).clickable { setColor(initial) })
                    ColorBox(color, Modifier.size(44.dp))
                }
                OutlinedTextField(
                    value = hexText,
                    onValueChange = { t ->
                        editingHex = true; hexText = t
                        ColorUtil.parseHex(t)?.let { setColor(it) }
                    },
                    singleLine = true,
                    label = { Text("Hex") },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Characters),
                    modifier = Modifier.weight(1f),
                )
            }

            // Saturation / value square
            Canvas(
                Modifier.fillMaxWidth().aspectRatio(1.6f).clip(RoundedCornerShape(12.dp))
                    .pointerInput(Unit) {
                        fun upd(o: Offset) {
                            editingHex = false
                            sat = (o.x / size.width).coerceIn(0f, 1f); value = 1f - (o.y / size.height).coerceIn(0f, 1f)
                        }
                        detectTapGestures { upd(it) }
                    }
                    .pointerInput(Unit) {
                        detectDragGestures(onDragStart = {
                            editingHex = false
                            sat = (it.x / size.width).coerceIn(0f, 1f); value = 1f - (it.y / size.height).coerceIn(0f, 1f)
                        }) { ch, _ ->
                            sat = (ch.position.x / size.width).coerceIn(0f, 1f); value = 1f - (ch.position.y / size.height).coerceIn(0f, 1f)
                        }
                    }
            ) {
                drawRect(Brush.horizontalGradient(listOf(Color.White, Color(ColorUtil.fromHsv(hue, 1f, 1f)))))
                drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black)))
                val p = Offset(sat * size.width, (1 - value) * size.height)
                drawCircle(Color.Black, 13.dp.toPx(), p, style = Stroke(4.dp.toPx()))
                drawCircle(Color.White, 13.dp.toPx(), p, style = Stroke(2.dp.toPx()))
            }

            SliderBar(
                fraction = hue / 360f,
                brush = Brush.horizontalGradient((0..6).map { Color(ColorUtil.fromHsv((it * 60f).coerceAtMost(359.9f), 1f, 1f)) }),
            ) { editingHex = false; hue = (it * 360f).coerceIn(0f, 359.9f) }
            SliderBar(
                fraction = alpha,
                brush = Brush.horizontalGradient(listOf(Color(color).copy(alpha = 0f), Color(color).copy(alpha = 1f))),
                checker = true,
            ) { editingHex = false; alpha = it }

            if (recent.isNotEmpty()) {
                Text("Recent", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(recent) { c ->
                        ColorBox(c, Modifier.size(34.dp).clip(CircleShape).clickable { editingHex = false; setColor(c) })
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { onAddToPalette(color) }, modifier = Modifier.weight(1f).height(48.dp)) { Text("Add to palette") }
                Button(onClick = { onPick(color) }, modifier = Modifier.weight(1f).height(48.dp)) { Text("Use color") }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SliderBar(fraction: Float, brush: Brush, checker: Boolean = false, onChange: (Float) -> Unit) {
    Canvas(
        Modifier.fillMaxWidth().height(34.dp).clip(RoundedCornerShape(17.dp))
            .pointerInput(Unit) { detectTapGestures { onChange((it.x / size.width).coerceIn(0f, 1f)) } }
            .pointerInput(Unit) {
                detectDragGestures(onDragStart = { onChange((it.x / size.width).coerceIn(0f, 1f)) }) { ch, _ ->
                    onChange((ch.position.x / size.width).coerceIn(0f, 1f))
                }
            }
    ) {
        if (checker) {
            val cell = size.height / 2
            var x = 0f; var i = 0
            while (x < size.width) {
                for (r in 0..1) drawRect(if ((i + r) % 2 == 0) Color(0xFF9A9AA4) else Color(0xFF6A6A74), Offset(x, r * cell), Size(cell, cell))
                x += cell; i++
            }
        }
        drawRect(brush)
        val cx = (fraction * size.width).coerceIn(size.height / 2, size.width - size.height / 2)
        drawCircle(Color.Black, size.height / 2 - 2.dp.toPx(), Offset(cx, size.height / 2), style = Stroke(4.dp.toPx()))
        drawCircle(Color.White, size.height / 2 - 2.dp.toPx(), Offset(cx, size.height / 2), style = Stroke(2.dp.toPx()))
    }
}

/** A color square with a checkerboard under translucent colors. */
@Composable
fun ColorBox(color: Int, modifier: Modifier = Modifier) {
    Canvas(modifier.background(Color(0xFF8E8E98))) {
        if ((color ushr 24) < 255) {
            val c = size.minDimension / 4
            var y = 0f; var r = 0
            while (y < size.height) {
                var x = if (r % 2 == 0) 0f else c
                while (x < size.width) { drawRect(Color(0xFF6E6E78), Offset(x, y), Size(c, c)); x += 2 * c }
                y += c; r++
            }
        }
        drawRect(Color(color))
    }
}

@Composable
fun Swatch(color: Int, selected: Boolean, modifier: Modifier = Modifier, size: Int = 36) {
    val ring = if (ColorUtil.isLight(color)) Color.Black else Color.White
    Box(
        modifier.size(size.dp).clip(RoundedCornerShape(8.dp))
            .then(if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        ColorBox(color, Modifier.matchParentSize())
        if (selected) Box(Modifier.size(8.dp).clip(CircleShape).background(ring))
    }
}
