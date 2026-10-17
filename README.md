# PixPacket

Pixel art and animation editor for Android, designed for touch first. Built with Kotlin and Jetpack Compose.

It covers the feature set of [Piskel](https://github.com/piskelapp/piskel) and the core of
[Aseprite](https://www.aseprite.org/) (`auto-aseperite` is an Aseprite build workflow), reworked for a phone screen.

## Touch controls

| Gesture | Action |
|---|---|
| One finger | Draw with the current tool |
| Two fingers | Pinch to zoom, drag to pan |
| Two-finger tap | Undo |
| Three-finger tap | Redo |
| Long-press canvas | Eyedropper (works with any drawing tool) |
| Precision cursor (menu) | Drag to aim like a trackpad; hold the round button to draw |
| Tap active dock tab | Hide the panel for a bigger canvas |

## Features

### Tools
| PixPacket | Piskel | Aseprite |
|---|---|---|
| Pencil (size 1–16, pixel-perfect) | Pen | Pencil + pixel-perfect |
| Eraser | Eraser | Eraser |
| Fill (contiguous / global replace) | Paint bucket, Paint all same color | Paint bucket |
| Line | Stroke | Line |
| Rectangle (outline / filled) | Rectangle | Rectangle / filled rectangle |
| Ellipse (outline / filled) | Circle | Ellipse / filled ellipse |
| Picker + long-press picking | Color picker | Eyedropper |
| Shade (lighten / darken) | Lighten / darken | Shading ink |
| Dither (primary/secondary checker) | Dithering | Pattern |
| Spray | – | Spray |
| Gradient (dithered, primary → secondary) | – | Gradient |
| Move (layer or selection) | Move | Move |
| Rect select, Lasso, Magic wand | Rectangle / lasso / shape selection | Marquee / lasso / magic wand |
| Mirror horizontally / vertically | Vertical mirror pen | Symmetry mode |

### Selection
Copy, cut, paste, erase, fill, flip ↔/↕, rotate 90°, outline, invert, select all, deselect.
Drawing tools only affect the selected area.

### Animation
Frames: add, duplicate, delete, reorder. Play/pause, FPS 1–30, onion skin, floating preview window
(tap to enlarge), tile preview mode.

### Layers
Add, duplicate, delete, move up/down, merge down, rename, show/hide, lock, opacity.

### Colors
Primary/secondary colors with swap. HSV picker with alpha and hex input. Recent colors.
Preset palettes: Default, PICO-8, DB16, Endesga 32, Game Boy, NES, Grayscale, Skin & Hair.
"My palette" is saved between sessions. "Sprite colors" lists the colors used in the sprite.
Replace a color across the whole sprite.

### Canvas & sprite
Resize canvas with a 3×3 anchor, scale the art (nearest neighbour), flip, rotate 90°, outline,
clear cel, pixel grid (major line every 8 px), fit to screen.

### Files
- Gallery of sprites with thumbnails, plus rename, duplicate and delete
- Autosave (a few seconds after edits, and when the app goes to the background)
- Export a PNG (current frame), sprite sheet PNG (row or grid) or animated GIF, scaled 1×–32×,
  to `Pictures/PixPacket` or through the share sheet
- Import an image as a new layer

## Build

Open in Android Studio, or run:

```
./gradlew assembleDebug
```

minSdk 24, compileSdk 37.
