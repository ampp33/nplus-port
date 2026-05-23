# N+ Port — Rendering & Sprite Reference

## Overview

This port uses **procedural rendering** via libGDX `ShapeRenderer` — no bitmap sprites or
texture atlases. Every game object is drawn with filled shapes and lines at runtime.
The original Flash SWF had vector art; this approach preserves that spirit while keeping
the asset footprint minimal.

Renderer: [GameRenderer.kt](core/src/main/kotlin/com/nplus/screens/GameRenderer.kt)

---

## Coordinate system

The physics engine uses **y-down** coordinates (origin top-left, y increases downward),
matching the original AS3 source. libGDX uses y-up by default.

Conversion applied in `GameRenderer`:

```
libgdx_y = WORLD_H - game_y        // WORLD_H = 600f
```

All `fy()` calls in the renderer perform this flip.

**World dimensions:** 792 × 600 world units (33 columns × 24 rows × 24 px/cell).

---

## Viewport

`ExtendViewport` with a minimum of 1280 × 720 world units, camera centred on the
level (792 × 600). On wider screens the level is letterboxed with blank space; on
taller screens the level is pillarboxed. The level always fills the shorter axis.

---

## Tile rendering

Tiles are drawn in a single filled-rect pass over the 33 × 24 grid.

| Tile type | Colour (RGBA) | Shape |
|-----------|--------------|-------|
| `FULL` | `#404040` (0.25 grey) | filled rect, full cell |
| Edge tiles (`EDGE_*`) | `#333333` (0.20 grey) | filled rect, full cell |
| Slope tiles (all others) | `#4D4747` (0.30/0.28) | filled rect, full cell |
| Empty | — | not drawn |
| Background clear | `#0F0F14` (0.06/0.06/0.08) | `glClear` |

Slope geometry is handled by the physics collision shapes, not by the rendered rect.
A future renderer pass would clip the rect to the slope triangle.

---

## Entity rendering

Two render passes per frame: **Filled** then **Line**.

### Gold piece
- Shape: filled circle, radius 6, 12 segments
- Colour: `#FFD91A` (gold)
- Hidden when collected (`isCollected()`)

### Mine
- Shape: filled circle, radius 4, 12 segments
- Colour: `#E61A1A` (red)
- Hidden after explosion (`isExploded()`)

### Exit door
- Shape: filled rect 20 × 20, centred on position
- Colour: `#33FF33` open / `#1A661A` closed

### Exit switch
- Shape: filled rect 10 × 10
- Colour: `#66FF66`
- Hidden once door is open

### Launchpad
- Shape: filled triangle (arrow head), length 8, base ±5
- Colour: `#33CCFF`
- Points in the launchpad's surface normal direction

### Bounce block
- Shape: filled rect 19.2 × 19.2 (radius 9.6)
- Colour: `#3366FF`

### One-way platform (line pass)
- Shape: line 24 units long along the platform surface + 5-unit normal indicator
- Colour: `#CCCCFF`

### Thwomp
- Shape: filled rect 18 × 18
- Colour: `#B233E6`

### Floor guard
- Shape: filled circle, radius 6, 12 segments
- Colour: `#FF8019`

### Turret
- Shape: filled circle radius 5 + line to aim position (line pass)
- Colour: `#CCCCCC`

### Rocket (projectile)
- Shape: filled circle radius 5; when flying, a 6-unit tail line (line pass)
- Colour: `#FF661A`

### Drone
- Shape: filled circle, radius 9, 16 segments
- Colour: `#E633CC`

### Doors (line pass only)

| Door type | Colour (open / closed) |
|-----------|----------------------|
| Regular | `#6699E6` at 20% alpha open / 90% alpha closed |
| Locked | `#66E666` at 20% / 90% |
| Trap | `#E66666` at 20% / 90% |

All doors rendered as a single horizontal line 24 units wide.

---

## Ninja rendering

### Alive
- **Filled pass:** white circle (`#FFFFFF`), radius = `ninja.r` (~10 units), 20 segments
- **Line pass:**
  - Facing indicator — line from centre in the direction the ninja faces (length = `ninja.r`)
  - Floor normal indicator (only when grounded) — short green line (`#66E666` at 60% alpha)
    pointing in the surface-normal direction

### Dead (ragdoll)
- **Filled pass:** grey (`#666666`) line segments for each ragdoll stick
- Each stick rendered as a line from `p0 - cos(orn)*8` to `p0 + cos(orn)*8`
- No circle drawn when dead

---

## Future work: bitmap sprites

The original SWF sprites are extracted to `decomp/sprites/` (hundreds of `DefineSprite_*`
directories). A Phase 2 renderer could:

1. Export each sprite's frames to PNG via a SWF-to-PNG pipeline.
2. Pack them into a texture atlas (`TexturePacker`).
3. Replace `ShapeRenderer` calls in `GameRenderer` with `SpriteBatch` + `TextureRegion`.

The physics and game logic are completely decoupled from rendering — `GameRenderer`
only reads positions and state from `Simulator`, so swapping the renderer requires no
changes to `core/physics/`.
