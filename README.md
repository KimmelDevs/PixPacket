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
Compact rows with visibility, lock and a ⋮ menu; the selected layer shows its opacity slider inline.
Add, duplicate, delete, move up/down, merge down, rename (double-tap), solo, clear, show/hide, lock, opacity.

### Colors
Primary/secondary colors with swap. HSV picker with alpha and hex input. Recent colors.
Preset palettes: Default, PICO-8, DB16, Endesga 32, Game Boy, NES, Grayscale, Skin & Hair.
Make as many of your own palettes as you like, with any number of colors: create, rename, duplicate
(including presets), sort by hue, delete, or build one from the sprite's colors. Palettes show 16 colors
per page; swipe for more. "Sprite colors" lists the colors used in the sprite.
Replace a color across the whole sprite.

### Canvas & sprite
Resize canvas with a 3×3 anchor, scale the art (nearest neighbour), crop to selection, trim empty borders,
flip, rotate 90°, outline, clear cel, fit to screen.
Grid settings per sprite: pixel lines, tile grid of any size (separate width/height), offset, and line color.

### Files
- Gallery with folders, sorting, rename, duplicate and move-to-folder
- Recently deleted: deleted sprites can be restored for 30 days
- Autosave (a few seconds after edits, and when the app goes to the background)
- Open/import: PNG/JPG (as one image or sliced as a sprite sheet), animated GIF, `.aseprite`/`.ase`,
  `.piskel` and PixPacket `.pxp`, either as a new sprite or as a layer in the current one
- Export: PNG, animated GIF, sprite sheet, ZIP of PNG frames (optionally per layer), `.aseprite`,
  `.piskel`, `.pxp` backup; scaled 1×–32×; save to Gallery, to Files, or share

### Settings
Light / dark / system theme, 7 accent colors, checkerboard size, haptics, finger-tap undo/redo,
long-press eyedropper, left-handed layout, auto-hide tools, keep screen on, defaults for new sprites.

### Landscape
In landscape the canvas fills one side and the tools sit in a side column (on the left in left-handed mode).

## Google Play assets
`playstore/` contains framed 1080×1920 screenshots (`phone/`), plain 2:1 screenshots (`plain/`),
a 1024×500 feature graphic and a 512×512 icon.

## Build

Open in Android Studio, or run:

```
./gradlew assembleDebug
```

minSdk 24, compileSdk 37.
