I'm porting N+ (N v2) decompiled ActionScript 3 → Kotlin + libGDX for Android (Retroid) + desktop.

**Directories**
- Decomp source (READ ONLY, never modify): `/home/ampp33/Downloads/Nv2-Linux/decomp/`
- Port target: `/home/ampp33/Downloads/Nv2-Linux/nplus-port/`
- Memory file with full project context: `/home/ampp33/.claude/projects/-home-ampp33-Downloads-Nv2-Linux-decomp/memory/project_nplus_port.md`

**Stack**: Kotlin 1.9.23 + libGDX 1.12.1 + gdx-controllers 2.2.1, Gradle 8.7, AGP 8.3.2, JDK 17, minSdk 26.

**Build**: `cd /home/ampp33/Downloads/Nv2-Linux/nplus-port && ./gradlew desktop:run`
**Tests**: `./gradlew core:test` (91 tests, all green)

---

## What's complete

**Phase 1 COMPLETE**: Full physics engine, 21 entity types, 600-level parser, keyboard/gamepad input, AudioManager, GameScreen, MenuScreen, AppStateManager. Desktop and Android APK both working.

**Phase 2a COMPLETE**: All entities replaced from ShapeRenderer → SpriteBatch sprites (mine, thwomp, bounceblock, exitdoor, exitswitch, floorguard, gold, turret, drones, rocket, launchpad, oneway, tiles).

**Phase 2b COMPLETE**: Door sprites + background colours. See sprite details below.

**Phase 2c COMPLETE**: Ninja sprite — 105 frames, velocity-based run/inair animation, per-ninja `NinjaRunState`, dead ninja still uses ShapeRenderer ragdoll.

**Post-2c fixes applied**:
- Desktop window: 2560×1440 (was 1280×720). ExtendViewport(1280×720) scales 2× automatically.
- Gold collect animation: `FPS_GOLD_COLLECT = 30f` (was 18f — now matches original Flash 30fps, sparkle runs 14 frames ≈ 0.47s).
- Game speed: `SIM_RATE = 60f` (was 40f). Original `App_MultiPurpose.as` sets `sim_globals.sim_rate = 60`. All physics constants use `(40/SIM_RATE)` formulas and scale automatically.
- SKID→STAND animation bug fixed: `abs(fn.x) < 0.5f` in Ninja.kt SKIDDING handler. Tile corner collisions produce fn.x ≈ 0.01–0.1 even on flat floors; 0.5f epsilon correctly rejects 45° diagonals (fn.x ≈ 0.707) while allowing flat/near-flat transitions.
- Render order fixed: entities draw BEFORE tiles (was after). Mines/entities no longer render on top of wall tiles. Ninja remains after tiles (always visible).
- Ninja Y offset +2f: the sprite canvas is taller than the physics radius, causing feet to appear 1.8 units into the ground. Adding 2f to screen-y shifts the sprite up so feet align with the floor contact point (r=10 below centre).

**Phase 2d TODO**: TexturePacker atlas consolidation — currently ~320 individual PNG textures loaded at startup.

---

## Renderer facts (`GameRenderer.kt` at `core/src/main/kotlin/com/nplus/screens/GameRenderer.kt`)

**Coordinate system**: game y-down, libGDX y-up. `fy(y) = WORLD_H - y` (WORLD_H = 600).
World: 792×600 (33×25 cells × 24px/cell). Camera centred, ExtendViewport(1280×720).

**Render passes (in order)**:
1. ShapeRenderer pre-pass: fill level interior rect (COL_LEVEL_BG = #CACAD0 = 202,202,208).
   Exterior clear colour = COL_BG = #797988 = 121,121,136.
2. SpriteBatch: entities first (mines, gold, doors, etc.) → tiles → live ninja.
   Order matters: tiles occlude entities at edges; ninja always on top.
3. ShapeRenderer: dead ninja ragdoll sticks only.
   — IMPORTANT: must call `shape.projectionMatrix = camera.combined` before this pass.

**Tile sprites**: `assets/sprites/tiles/` — 42 frames. Each PNG 72×72 with 24px transparent padding.
Draw via `TextureRegion(tex, 24, 24, 24, 24)` at 1× (no scaling). Frame 1 = tile type 0 = EMPTY (skip it). Stored in `sprTileRegions`.

**Registration point = canvas centre** for all entity sprites except:
- Gold: bottom-centre (`batch.draw(tex, px - w/2, fy(py))`)
- Rocket: right-body offset x=55.5 (`batch.draw(tex, px - 55.5f, fy(py) - h/2)`)

**Rotation formula (launchpad + oneway + doors)**:
Flash `mc.rotation = atan2(ny, nx) * (180/π)` (CW degrees, y-down). libGDX CCW equivalent = `-normalAngleDeg(nx, ny)` where:
```kotlin
private fun normalAngleDeg(nx: Float, ny: Float): Float =
    Math.toDegrees(atan2(-ny.toDouble(), nx.toDouble())).toFloat()
```

---

## Sprites in `assets/sprites/`

```
mine/        (2f)   thwomp/       (1f)   bounceblock/     (1f)
exitdoor/   (17f)   exitswitch/   (2f)   floorguard/      (3f)
gold/       (18f)   turret/      (17f)   drone_chaser/    (3f)
drone_zap/   (1f)   rocket/      (13f)   launchpad/      (19f)
oneway/      (1f)   drone_laser/ (26f)   drone_chaingun/  (7f)
tiles/      (42f)

door_regular/       (18f) — idx 1 = closed; only drawn when !isOpen()
door_locked/        (11f) — idx 0 = closed; only drawn when !isOpen(); also shows switch
door_trap/          (11f) — idx 10 = closed; drawn when !isOpen(); switch shown when isOpen()
door_locked_switch/ (2f)  — idx 0 = armed
door_trap_switch/   (2f)  — idx 0 = armed
ninja/             (105f) — frames 1-105, DefineSprite_738, 187×140 each
```

**Door orientation**: `doorOrn() == 0f` → 0° (vertical barrier); `doorOrn() == π/2` → -90° (horizontal).

**Ninja frame layout** (all at scale 0.2, registration = canvas centre, facing via `batch.draw` scaleX):
- STAND: idx 0–11 (frames 1–12, 12-frame loop)
- RUN: idx 12–83 (frames 13–84, velocity-based via `NinjaRunState`)
- INAIR/JUMPING/FALLING/AWAITING_DEATH: idx 84–102 (frames 85–103, vel-mapped to -9..+9 offset from centre idx 93)
- SKID: idx 103 (frame 104)
- WALLSLIDE: idx 104 (frame 105)
- Rotation: `-degrees(orn)` (negate AS3 CW → libGDX CCW)
- Dead ninja: ShapeRenderer ragdoll sticks (unchanged from Phase 1)

---

## Key files

| File | Purpose |
|---|---|
| `core/.../screens/GameRenderer.kt` | All rendering |
| `core/.../screens/GameScreen.kt` | Fixed-step physics loop (SIM_RATE ticks/sec) |
| `core/.../physics/Ninja.kt` | Player physics + GfxState snapshot |
| `core/.../physics/Simulator.kt` | Simulation tick, entity/grid management |
| `core/.../physics/entities/DoorEntities.kt` | Door physics + `doorOrn()`, `getSwitchPos()` accessors |
| `core/.../levels/EntityFactory.kt` | Builds entity instances from level data |
| `core/.../SimGlobals.kt` | `SIM_RATE = 60f`, death/enemy type constants |
| `desktop/.../DesktopLauncher.kt` | Window 2560×1440, 60fps vsync |
| `assets/levels/levels.bin` | 600 campaign levels (binary) |
| `assets/sprites/` | All sprite PNGs |

---

## FFDec sprite exports

`decomp/sprites/DefineSprite_NNN_name/1.png … N.png` — already rasterized PNGs.
Symbol ID → name map: `decomp/symbolClass/symbols.csv`.
Canvas centre = Flash registration point = entity physics position (key for alignment).

---

## Next task: Phase 2d — TexturePacker atlas consolidation

Currently ~320 individual PNG textures are loaded at startup (one `Texture` object per frame). This is inefficient on Android (texture switches, memory). Phase 2d packs all sprites into one or a few TextureAtlas files using libGDX TexturePacker, then updates GameRenderer to use `TextureRegion` lookups instead of `List<Texture>`.

Steps:
1. Add `gdx-tools` dependency (TexturePacker) to `desktop/build.gradle.kts` or a separate `tools/` module.
2. Write a packing script (or Gradle task) that packs `assets/sprites/**/*.png` into `assets/atlas/sprites.atlas` + `sprites.png`.
3. Update `GameRenderer.kt`: replace all `lateinit var sprXxx: List<Texture>` with `TextureAtlas` lookups. Naming convention: region name = `<folder>/<index>` e.g. `ninja/1`, `tiles/3`.
4. Dispose the atlas in `GameRenderer.dispose()` instead of individual textures.

The ninja sprites alone are 105 textures at 187×140 — these will dominate the atlas size. May need 2 atlas pages (4096×4096 each). Verify the atlas stays within GL_MAX_TEXTURE_SIZE on Android (typically 4096 or 8192).
