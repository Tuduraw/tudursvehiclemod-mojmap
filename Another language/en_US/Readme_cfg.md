# Readme_cfg.md - Config settings

Files covered:
- `config/tudursvehiclemod.json` (client settings)
- `config/tudursvehiclemod-server.json` (server settings)

Both are JSON, and are generated automatically with default values on
first launch if they don't exist. When a file written by an older version lacks a
setting added since then, that setting is added to the file with its
default value at startup (values already in the file are kept as they
are, and settings this version doesn't know are kept too). Changing a setting from the in-game
settings screen saves to the file immediately, so there's normally no
need to edit these files by hand.

## Opening the settings in-game

While riding any vehicle, press the "open vehicle menu" key, then press
the "Debug" button inside that menu to open the debug menu. From there
you'll find entries for "Settings" (client settings) and "Server
settings".

"Server settings" can only be operated on a world you host yourself
(singleplayer, or a LAN game you opened). The settings of a remote
dedicated server can't be changed directly from this screen.

---

## Contents

1. About when a change takes effect
2. Client settings (`tudursvehiclemod.json`)
3. Server settings (`tudursvehiclemod-server.json`)
4. Settings that don't appear on the settings screen
5. Resetting force-loaded chunks (`/tvm unloadchunks`)
6. Showing contacts (`/tvm debug contacts`)

---

## 1. About when a change takes effect

When a change takes effect varies by setting.

- **Immediate**: a setting re-read every frame or every tick. The effect
  changes the moment you press the button.
- **On reload**: a setting whose effect changes at a resource reload
  (which the debug menu's own reload button can trigger immediately).
  These are the ones tied to texture loading.
- **Restart required**: a setting tied to machinery built only once per
  game session. After changing it, nothing visibly changes until you
  restart the game.

Each entry below states which of the three it is.

---

## 2. Client settings (`tudursvehiclemod.json`)

### Translucency mode (`translucencyMode`) - restart required

How a vehicle's own transparent parts (windows, optics, etc.) are drawn.
Default is `dither_texture`.

| Value | Display name | Notes |
|---|---|---|
| `dither_shader` | Dither (shader) | Dithering via this mod's own shader. Can't be combined with a shader pack (the pack replaces the drawing program with its own). |
| `dither_texture` (default) | Dither (texture) | Bakes the dither pattern into the texture at load time. Works with shader packs, and handles depth ordering against water and entities correctly. |
| `translucent` | Alpha blending | Straightforward alpha blending using vanilla's own translucency pipeline. Works with shader packs, but being draw-order dependent, water can end up hidden behind a transparent part. |

### Searchlight (`searchLightMode`) - lighting immediate, shape may need a restart

Switches both the searchlight's own lighting method and its visible shape
with one setting. Default is `beam`.

| Value | Display name | Notes |
|---|---|---|
| `beam` (default) | Beam | A closed translucent cone (MC Heli's own original shape). Lights entities only. |
| `dynamic_light` | Dynamic light (experimental) | Looks the same as Beam, but terrain is actually re-lit too (an experimental feature involving chunk rebuilds). |
| `blades` | Blades | Same lighting as Beam, drawn as several blades extending around the circumference. Displays correctly under any translucency mode. |
| `bands` | Bands | Same lighting as Beam, drawn as rings arranged along its length. |
| `dashes` | Dashes | Blades further cut into shorter segments, giving a dashed appearance. |

### Searchlight brightness (`searchLightBrightness`) - immediate

- Range: 0 - 6 (default: 3.0, in steps of 0.5; steps of 1.0 above 3.0)
- A multiplier on how brightly a lit searchlight illuminates blocks and
  entities. It scales the intensity configured on each light rather than
  replacing it. At 0, lighting is disabled and only the visible beam
  remains.

### Block overlay brightness (`searchLightBlockBrightness`) - immediate

- Range: 0 - 3 (default: 1.0, in steps of 0.5)
- How strongly a searchlight tints terrain. Independent of searchlight
  brightness (which controls entity lighting). At 0, only the terrain
  drawing is disabled; entity lighting remains.

### Block overlay sample count (`searchLightBlockOverlaySampleCount`) - immediate

- Range: 50 - 400 (default: 200, in steps of 50)
- How many samples a searchlight fires per tick to determine which parts
  of the terrain are lit. A fixed value, unrelated to the light's own
  shape or range. Fewer is lighter, and the small gaps at adjoining faces
  that coarse sampling produces are filled in by the diffusion pass.

### Block overlay update interval (`searchLightBlockOverlayIntervalTicks`) - immediate

- Range: 1 - 10 ticks (default: 4)
- How often the terrain drawing is fully recalculated. This is the part
  of searchlight processing that consumes the most temporary memory, so
  raising the value reduces memory use proportionally - at the cost of
  the drawing lagging slightly behind a quickly-moving beam. The lighting
  itself is always updated every tick regardless of this setting.

### Cone display distance (`searchLightConeDisplayDistance`) - immediate

- Range: 0.1 - 1.0 (default: 1.0, in steps of 0.1)
- How far the visible shape (cone/blades/bands/dashes) reaches, as a
  fraction of the light's own length. Affects only what is drawn; the
  lighting and block overlay ranges don't change.

### Chunk rebuild interval (`searchLightChunkRebuildIntervalTicks`) - immediate

- Range: 1 - 40 ticks (default: 10)
- Used only when the searchlight mode is "Dynamic light". How often
  terrain re-lighting is requested as the beam moves. Lower is smoother
  but heavier.

### Ring count multiplier (`searchLightRingCountMultiplier`) - immediate

- The settings screen cycles between 0.5, 1.0, 2.0, and 3.0 (default: 1.0)
- A multiplier on how many rings a blades/bands/dashes beam generates
  along its length, applied on top of the automatic size-based
  adjustment. There's no upper bound in the code, so edit the file
  directly to go beyond the values the screen cycles through.

### Blade count multiplier (`searchLightBladeCountMultiplier`) - immediate

- The settings screen cycles between 0.5, 1.0, 2.0, and 3.0 (default: 1.0)
- A multiplier on how many blades a blades/dashes beam generates around
  its circumference. As with the ring multiplier, there's no upper bound
  and the file can be edited directly for higher values.

### Dither upscale (`translucencyDitherUpscale`) - on reload

- Possible values: 0.25 / 0.5 / 1 / 2 / 4 / 8 (default: 4.0)
- The texture upscale factor applied before dithering. Above 1 gives a
  finer pattern in exchange for memory; below 1 downscales to save
  memory. Only meaningful when the translucency mode is "Dither
  (texture)".

### GPU texture compression, BC1 (`translucencyDitherGpuCompression`) - on reload

- Default: `false`
- Keeps the dithered texture in BC1, cutting VRAM use to 1/8. Alpha is
  preserved exactly, while color detail becomes approximate. It operates
  outside Minecraft's own texture API, so it falls back to the
  conventional path automatically on failure. Only meaningful when the
  translucency mode is "Dither (texture)".

### Dither max texture size (`translucencyDitherMaxTextureSize`) - on reload

- Range: 512 - 8192 px (default: 2048, cycling by doubling)
- An upper bound on the upscaled texture size, which caps the dither
  upscale factor. Only meaningful when the translucency mode is "Dither
  (texture)".

### Experimental triangle rendering (`experimentalTriangleRendering`) - restart required

- Default: `false`
- An experimental drawing path that submits 3 vertices per triangle
  rather than 4. Only meaningful when the translucency mode is "Dither
  (shader)".

### Match chunk view distance (`matchChunkViewDistanceForEntityRender`) - immediate

- Default: `true`
- Makes this mod's own entities (vehicles, projectiles) use the game's
  own render distance (chunk view distance) rather than the shorter
  entity render distance.

### Disable render culling (`disableEntityRenderCulling`) - immediate

- Default: `true`
- Skips this renderer's own distance and frustum checks, always drawing a
  vehicle as long as it's loaded.

### Draw far vehicles (`renderFarVehicles`) - immediate

- Default: `false`
- For use with an LOD mod (Distant Horizons and the like). Draws vehicles
  (and projectiles drawn with a model - missiles, bombs, shells) beyond the
  render distance, so they show over the distant terrain the
  LOD mod draws.
- Only works when the server (in single player, the world) has "Far
  vehicle sync" (`farVehicleSyncEnabled`, below) on.
- A far vehicle's moving parts are drawn as the server reports them:
  landing gear, hatches, canopies, weapon bays and folding wings open or
  closed as they are, turrets where they point (both eased between
  reports), propellers and rotors turning at their rate, and a wreck as a
  wreck. Wheels and tracks don't turn, and recoil, wakes, search light
  beams and smoke aren't drawn. It is lit as by open sky.
- Things to know:
  - Far vehicles aren't hidden by distant terrain, and may show through a
    mountain: an LOD mod's terrain isn't part of the game's own depth
    information.
  - With the game's own fog on, vehicles beyond the render distance are
    lost in it. Turn vanilla fog off in the LOD mod.
  - With Distant Horizons it has been reported not to work (cause not
    pinned down - see "Known incompatibilities" in the README).
  - While this is on, the game's furthest draw depth (normally four times
    the render distance) is extended to the distance below.

### Far vehicle distance (`farVehicleRenderDistance`) - immediate

- Values on the screen: 512 / 1024 / 2048 / 4096 (blocks, default: 2048)
- How far far vehicles are drawn when "Draw far vehicles" is on. If the
  server's limit (`farVehicleSyncMaxDistance`) is shorter, that applies.

### Third-person HUD (`showHudInThirdPerson`) - immediate

- Default: `true`
- Whether the vehicle HUD is drawn in third person too. In first person
  it's always drawn regardless of this setting.

### Max aircraft camera distance (`maxAircraftCameraDistance`) - immediate

- Range: 20 - 160 blocks (default: 80, in steps of 20)
- How far the third-person camera can zoom out while flying.

---

## 3. Server settings (`tudursvehiclemod-server.json`)

Changes made on the "Server settings" screen take effect immediately,
with no restart.

### Parallel hit detection (`parallelHitDetectionAcrossVehicles`) - immediate

- Default: `false`
- Batches this mod's own hit detection for every vehicle into a single
  parallel pass once per tick, instead of running it inline inside each
  vehicle's own tick. Turn this on for a world with many high-polygon
  vehicles where hit detection is clearly the bottleneck.

### Minimum vehicles for parallel hit detection (`parallelHitDetectionMinimumVehicles`) - immediate

- Range: 1 - 20 (default: 3)
- How many vehicles must need hit detection in a tick before the parallel
  pass above is worth using. Only meaningful when parallel hit detection
  is on.

### Destroyed vehicle despawn (`destroyedVehicleDespawnEnabled`) - immediate

- Default: `true`
- Makes a destroyed vehicle despawn automatically after the timer below,
  instead of remaining in the world as wreckage forever. Has no effect
  whatsoever on a vehicle that's still drivable.

### Time before a destroyed vehicle despawns (`destroyedVehicleDespawnSeconds`) - immediate

- Range: 30 - 1800 seconds (default: 300 = 5 minutes)
- How long a destroyed vehicle stays in the world before despawning. Only
  meaningful when destroyed vehicle despawn is on.

### Projectile forced-chunk limit (`projectileForcedChunkLimit`) - immediate

- Possible values: disabled (0) / 50 - 1000 (default: 300)
- A server-wide cap on the total number of chunks that in-flight
  projectiles force-load, which is what lets a projectile flying far from
  any player keep going until impact. Past the cap, the
  oldest-summoned projectile is released first. Disabled (0) means
  projectiles never force-load chunks at all. This doesn't affect
  CAS/Carrier aircraft or other always-loaded features (those are tuned
  on their own weapon/vehicle files).
- A projectile's state (explosion, fuses, guidance and so on) isn't saved
  with the world. When the server stops (including closing a
  singleplayer world), the force-loading of projectiles still in flight
  is released, and those projectiles are removed - without exploding -
  the next time their chunk loads.


### Autopilot collision avoidance (`autopilotCollisionAvoidance`) - immediate

- Default: `true`
- An aircraft flying itself (a CAS strike, a carrier's aircraft, a formation wingman, a drone) treats each other vehicle ahead of it (wingmen, the mothership, ships, ground vehicles) as the box around its model. When it would enter that box - grown by its own size plus a 3-block margin - within about 2 seconds (40 ticks), it steers out of it. It never dodges downward away from a vehicle below it (it climbs instead).
- While landing on or launching from its mothership, that mothership is not avoided.
- Never applies to an aircraft a player is flying, one on the ground, or a destroyed one.
- Avoidance shifts the course (velocity) only, not the nose; the autopilot brings the aircraft back to its route afterwards.
- With tight formation spacing or a crowded deck, aircraft may push each other around; turn it off in that case.

### Far vehicle sync (`farVehicleSyncEnabled`) - immediate

- Default: `false`
- Sends players who have "Draw far vehicles" (a client setting) on the
  position, heading and motion of vehicles beyond the range entities are
  sent in (the server's view distance), so they can see them over an LOD
  mod's distant terrain.
- It lets players see where far-away vehicles are, so it's off by
  default. Think twice on a PvP server.
- Projectiles are sent only if drawn with a model (weapons with
  `BulletModel`: missiles, bombs, shells); plain bullets are too small to
  see that far out, and too many. Projectiles move fast and end suddenly,
  so they're sent more often than vehicles
  (`farVehicleSyncProjectileIntervalTicks`, default 4 ticks); the most
  sent to one player is `farVehicleSyncMaxProjectiles` (default 64, 0 for
  none). Both can only be changed in the file. A projectile that has hit
  something may be seen going on until the next send (up to 4 ticks by
  default).
- Only vehicles and projectiles in chunks the server has loaded count -
  including force-loaded ones (what flying projectiles, CAS/carrier
  aircraft and remote-controlled vehicles do by themselves, or another mod
  does through `ChunkForceTracker` and the like). One where nobody is
  nearby (and no chunk loader) isn't on the server to be seen.
- Vehicles are sent up to the shorter of the player's setting and the
  limit below. How often they're sent (`farVehicleSyncIntervalTicks`,
  default 10 ticks) and the most sent to one player (`farVehicleSyncMaxCount`,
  default 64, nearest first) can only be changed in the file. In between,
  the client moves them on by the motion it was sent.

### Far vehicle sync distance (`farVehicleSyncMaxDistance`) - immediate

- Values on the screen: 512 / 1024 / 2048 / 4096 (blocks, default: 2048)
- The furthest a vehicle is sent, whatever a player asks for.

### Vehicle speed multiplier (`vehicleSpeedMultiplier`) - immediate

- Values on the screen: x0.25 / x0.5 / x0.75 / x1 / x1.25 / x1.5 / x2 /
  x3 / x5 / x10 (default: x1). Editing the file directly allows any value
  (values under 0.05 are used as 0.05).
- Multiplies every vehicle's top speed: each vehicle's `max_speed`, and
  the settings that give a speed directly (`SweepWingSpeed` for a folded
  swing wing, a submarine's `dive_max_speed`, a catapult launch's speed).
  Speeds set as a fraction of the top speed (throttle steps, stall speed,
  autopilot cruise speed) follow along by themselves.
- Acceleration, turning and the rest of a vehicle's handling don't change,
  so at a higher multiplier a vehicle takes longer to reach its top speed.
- Vehicle movement is also worked out on each player's client, so the
  server sends this setting to every player on joining and whenever it
  changes.

### Vehicle speed multiplier per type (`vehicleSpeedMultiplierByType`) - immediate

- Values on the screen: Common / x0.25 to x10 (default: Common). The screen
  lists six types: aircraft, VTOL, helicopter, car, ship and submarine.
- A speed multiplier used only for vehicles of a particular entity type.
  A type with its own value uses it; a type without one (every type, at
  first) uses the common vehicle speed multiplier above.
- In the file, the keys are entity type ids. This mod's own types can be
  given by name alone, such as `"aircraft"`; an addon's vehicles by that
  addon's id (for example `"mcheliaddon:mcheli_plane"`). Removing an entry goes
  back to the common setting.

```json
"vehicleSpeedMultiplierByType": {
  "aircraft": 1.5,
  "tudursvehiclemod:ship": 2.0
}
```

### Weapon damage multiplier (`weaponDamageMultiplier`) - immediate

- Values on the screen: x0 / x0.25 / x0.5 / x0.75 / x1 / x1.25 / x1.5 /
  x2 / x3 / x5 / x10 (default: x1). Editing the file directly allows any
  value of 0 or more.
- Multiplies the damage this mod's weapons do when a projectile hits an
  entity or a vehicle (a direct hit). Lets a server tune weapon power as a
  whole while leaving each weapon file's `Power` as it is.
- At x0 a hit does no damage. A living target is still knocked back as by
  the hit (without the red hurt flash or invulnerability time).
- Explosion damage is set separately, by the explosion blast multiplier
  below. Crash, collision and drowning damage isn't affected.

### Explosion blast multiplier (`explosionBlastMultiplier`) - immediate

- Values on the screen: the same as the weapon damage multiplier (default: x1)
- Multiplies the damage explosions from this mod's weapons and destroyed
  vehicles do to entities and vehicles. How far the blast reaches, how hard
  it pushes, and how the explosion looks stay the same.
- At x0 the blast does no damage and only pushes entities away.

### Explosion block destruction multiplier (`explosionBlockMultiplier`) - immediate

- Values on the screen: the same as the weapon damage multiplier (default: x1)
- Multiplies how far explosions from this mod's weapons and destroyed
  vehicles break blocks (and set fire), independently of the blast
  multiplier. For a weapon's explosion it multiplies `ExplosionBlock` (or
  `Explosion` when that isn't set); for a vehicle's explosion, how far
  vanilla's explosion reaches into blocks.
- At x0 no blocks are broken and no fire is set.
- An explosion that wouldn't break blocks anyway (a weapon with
  `ExplosionBlock = 0`, a fuel-air explosive, a vehicle's
  explosion with the `mobGriefing` game rule off) doesn't start breaking
  them at a higher multiplier. A projectile breaking glass or going through
  blocks with `Piercing` isn't an explosion and isn't affected.

---

## 4. Settings that don't appear on the settings screen

These exist in the config files but aren't shown on the in-game settings
screen, so changing them requires editing the file directly.

### `searchLightBlockOverlayRange` (client settings)

- Default: `50.0` (blocks)
- How far from a searchlight terrain is tinted with its color. The effect
  fades gradually toward this distance (it never cuts off abruptly). If
  the light's own length is shorter than this, it's truncated at that
  length. Cost scales with the cube of this block count, making it the
  single most performance-relevant setting in this whole feature. Lower
  it on weaker hardware.

### `worldDataCleanupChunksPerTick` (server settings)

- Default: `-1` (no count limit)
- An upper bound on how many candidate chunks `/tvm clean -d` (the
  command that fully removes vehicles across the world, including every
  unloaded chunk) examines per tick. At -1 (the default) no count-based
  limit is applied - which does **not** mean the work finishes in one
  tick. The processing time itself is always separately limited, and
  progress is logged periodically. Setting a positive value (50, say)
  adds a chunk-count cap on top of that time limit, smoothing the
  per-tick load at the cost of taking even longer to finish overall.


### `hitMeshBuildMaxConcurrent` (server settings)

- Default: `-1` (no limit)
- The hit-detection meshes (the hit data built from each vehicle's model)
  are built in the background as soon as a vehicle is loaded into the
  world (they used to be built on the server thread on the first hit, so
  a high-poly vehicle could stall the server the moment it was hit). This
  is how many vehicle definitions may be built at the same time. -1 means
  no limit (built in parallel, one per CPU core); a positive value (1,
  say) caps it at that many, on low-priority threads. Lower it to keep the
  server load down. Takes effect after a server restart.
- `/reload` drops the in-memory model caches (the hit-detection meshes and
  so on) and reads the OBJ files again. The track roller disk cache
  (`.track-roller-bounds.txt`) doesn't notice a changed OBJ - delete it by
  hand after editing a model.

### `fireSupportDesignatorRange` (server settings)

- Default: `512`
- How far (blocks) a Fire Support Designator can mark a target. The
  point has to be within loaded chunks.

### `fireSupportMaxDistance` (server settings)

- Default: `4096`
- The largest relative coordinate (blocks, each axis) the Fire Support
  Center's screen accepts.

---

## 5. Resetting force-loaded chunks (`/tvm unloadchunks`)

Not a setting, but it's the command that goes with chunk force-loading,
so it's covered here.

- **Permission**: operator (permission level 2 or higher)
- **What it does**: across every dimension, keeps only the force-loaded
  chunks this mod's features are currently using and releases every
  other force-loaded chunk.
  - Released chunks go back to being loaded only around players, as
    usual.
  - Anything still in use (CAS/Carrier aircraft and motherships in
    flight, active Drone Centers, Stations, projectiles in flight) keeps
    its force-loading, so running it doesn't interrupt anything.
    Force-loading that starts afterwards works as usual.
- **Why**: in earlier versions, chunks could stay force-loaded after the
  aircraft, projectile, Drone Center or Station that needed them was
  gone. Force-loading is saved with the world, so those survive a
  restart too. This command releases all of those leftovers at once.
- **Note**: it also releases chunks force-loaded with vanilla
  `/forceload` or by other mods. Set those up again afterwards if you
  still need them.
- **Output**: how many chunks were released, and how many were kept
  because they're in use, per dimension.
- **When to run it**: not right after the world loads (for about 10
  seconds after the server starts it's refused). Run that early, it
  would release the chunks of aircraft, Drone Centers and the like that
  have loaded but haven't asked for their force-loading again yet.
- **Blocks replaced with `/setblock` and similar**: when a player breaks
  a Drone Center or Station, its cleanup (releasing its force-loading,
  among other things) runs. Replacing it with `/setblock` (without
  `destroy`), `/fill`, `/clone` and the like skips that cleanup, as
  vanilla does for every block entity. Any force-loading left behind
  that way is released by this command.

## 6. Showing contacts (`/tvm debug contacts`)

- **Permission**: operator (permission level 2 or higher)
- **What it does**: shows the contacts found by shape-based contact
  detection (see "Contact detection by vehicle shape" in the README) as
  particles. Each run toggles it on or off. Everyone can see them.
  - Red: vehicle against vehicle
  - Yellow: vehicle against a living entity (player, mob)
- **Output**: how many shape grids are built, how many solid cells they
  have, and the memory they take.
- **Note**: while it's on, every loaded vehicle's contacts are worked out
  several times a second. Turn it off when you're done.
