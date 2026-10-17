package com.kimmeldevs.pixpacket.editor

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Brightness6
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Colorize
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.FormatColorFill
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Grain
import androidx.compose.material.icons.filled.HighlightAlt
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.ShapeLine
import androidx.compose.material.icons.filled.Texture
import androidx.compose.material.icons.filled.Gradient
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

enum class Tool(val label: String, val icon: ImageVector, val hint: String) {
    PENCIL("Pencil", Icons.Filled.Brush, "Draw pixels. Long-press anywhere to pick a color."),
    ERASER("Eraser", EraserIcon, "Erase to transparent."),
    BUCKET("Fill", Icons.Filled.FormatColorFill, "Fill an area. Turn off Contiguous to replace a color everywhere."),
    LINE("Line", Icons.Filled.ShapeLine, "Drag to draw a straight line."),
    RECT("Rect", Icons.Filled.CropSquare, "Drag to draw a rectangle."),
    ELLIPSE("Ellipse", Icons.Filled.RadioButtonUnchecked, "Drag to draw an ellipse."),
    PICKER("Picker", Icons.Filled.Colorize, "Tap to pick a color from the canvas."),
    SHADE("Shade", Icons.Filled.Brightness6, "Lighten or darken pixels you paint over."),
    DITHER("Dither", Icons.Filled.Texture, "Checker pattern of primary and secondary colors."),
    SPRAY("Spray", Icons.Filled.Grain, "Airbrush scattered pixels."),
    GRADIENT("Gradient", Icons.Filled.Gradient, "Drag across a region to fill it with a dithered gradient."),
    MOVE("Move", Icons.Filled.OpenWith, "Drag to move the selection or the whole layer."),
    SELECT_RECT("Select", Icons.Filled.HighlightAlt, "Drag a rectangle to select."),
    LASSO("Lasso", Icons.Filled.Gesture, "Draw a free shape to select."),
    WAND("Wand", Icons.Filled.AutoFixHigh, "Tap to select same-colored pixels."),
    ;

    val usesSize get() = this in setOf(PENCIL, ERASER, LINE, RECT, ELLIPSE, SHADE, DITHER, SPRAY)
    val usesMirror get() = this in setOf(PENCIL, ERASER, LINE, RECT, ELLIPSE, SHADE, DITHER, SPRAY)
    val isSelection get() = this in setOf(SELECT_RECT, LASSO, WAND)
}

/** Eraser glyph (Material Design Icons "eraser", Apache 2.0). */
val EraserIcon: ImageVector = ImageVector.Builder("Eraser", 24.dp, 24.dp, 24f, 24f).apply {
    addPath(
        pathData = addPathNodes(
            "M16.24,3.56L21.19,8.5C21.97,9.29 21.97,10.55 21.19,11.34L12,20.53C10.44,22.09 7.91,22.09 6.34,20.53" +
                "L2.81,17C2.03,16.21 2.03,14.95 2.81,14.16L13.41,3.56C14.2,2.78 15.46,2.78 16.24,3.56M4.22,15.58" +
                "L7.76,19.11C8.54,19.9 9.8,19.9 10.59,19.11L14.12,15.58L9.17,10.63L4.22,15.58Z"
        ),
        fill = SolidColor(androidx.compose.ui.graphics.Color.Black),
    )
}.build()
