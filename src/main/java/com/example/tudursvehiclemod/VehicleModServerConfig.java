package com.example.tudursvehiclemod;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/** Server-side config, separate from client.VehicleModConfig (which is
 * client-only - camera distance, render culling, and so on, none of which
 * exist server-side at all). Currently just the hit-detection threading
 * options below. Written to its own file so the two never collide. */
public final class VehicleModServerConfig {

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path CONFIG_PATH = FabricLoader.getInstance().getConfigDir().resolve("tudursvehiclemod-server.json");

	/** When true, this mod's own custom hit
	 * detection (see AbstractVehicleEntity's own
	 * tudursvehiclemod$updateCustomHitDetection() doc) is batched across
	 * EVERY vehicle in the world and run as ONE parallel pass at the end
	 * of each server tick, rather than each vehicle independently doing
	 * its own (already-parallel, but only across its OWN few candidate
	 * projectiles) check inline during its own tick - see
	 * entity.HitDetectionCoordinator's own doc for why that distinction
	 * actually matters for real-world throughput.
	 *
	 * Defaults to FALSE - the existing per-vehicle
	 * approach stays the default, and this is strictly opt-in, so a
	 * server that's running fine today can't be destabilized by a
	 * behavioral change nobody asked for. Turn this on for a world with
	 * many high-poly vehicles where hit detection is measurably the
	 * bottleneck. */
	public boolean parallelHitDetectionAcrossVehicles = false;

	/** How many vehicles must actually need a hit-detection check in a
	 * given tick before the parallel path above is worth using at all -
	 * below this, the coordinator just runs them inline on the calling
	 * thread instead, since the fixed overhead of dispatching to a thread
	 * pool and joining back genuinely outweighs the work itself for one
	 * or two vehicles. Only meaningful when
	 * parallelHitDetectionAcrossVehicles is true. */
	public int parallelHitDetectionMinimumVehicles = 3;

	/** A vehicle that's been destroyed (see
	 * AbstractVehicleEntity's own tudursvehiclemod$isDestroyed() doc)
	 * despawns on its own after destroyedVehicleDespawnSeconds, rather
	 * than sitting in the world as wreckage forever until someone
	 * manually removes it. Deliberately only ever affects a vehicle
	 * that's actually confirmed destroyed - an intact, still-driveable
	 * vehicle never despawns from this, no matter how long it sits
	 * around unused. Defaults to true. */
	public boolean destroyedVehicleDespawnEnabled = true;

	/** How many seconds a destroyed vehicle (see
	 * destroyedVehicleDespawnEnabled's own doc) sits in the world before
	 * despawning on its own. Defaults to 300 (5 minutes). Only
	 * meaningful when destroyedVehicleDespawnEnabled is true. */
	public int destroyedVehicleDespawnSeconds = 300;

	/** No longer used: runway floors are no longer made of fixed-size tiles (see entity.RunwayDeckCollision). Still read from an existing config file, and ignored.
	 * @deprecated has no effect */
	@Deprecated
	public double carrierRunwayExpectedWidth = 30.0;

	/** Caps the TOTAL number of chunks force-loaded by flying projectiles (see entity.projectile.ProjectileChunkLoadTracker's own doc) across the whole server at once - deliberately separate from every OTHER always-loaded feature (CAS/Carrier aircraft, Drone Center, etc.), which a mapper can already tune indirectly via the relevant weapon/vehicle file's own settings and stay entirely unaffected by this. When exceeded, the OLDEST-spawned projectile(s) still holding a claim have theirs released first (oldest-spawn-order eviction, not oldest-updated) until back within the limit - a released projectile isn't discarded outright, it simply stops being force-loaded, so it may go on to freeze (stop ticking) if it then flies outside every player's own normal render/simulation distance. 0 disables projectile chunk-forcing entirely - no projectile ever force-loads anything, the exact pre-this-feature behavior. Defaults to 300 (a generous but bounded budget - a MachineGun-heavy server firing many projectiles at once won't spiral into an unbounded number of force-loaded chunks). */
	public int projectileForcedChunkLimit = 300;

	/** "/tvm clean -d" (command.TvmCommand - complete removal of every one of this mod's own vehicles, including ones sitting in currently-unloaded chunks, achieved by force-loading and checking every chunk the world has ever actually saved to disk) be speed-limited, since force-loading a chunk that would otherwise sit untouched is real, avoidable server work: the maximum number of chunk candidates command.WorldDataCleanupTask checks per server tick. -1 (the default) means no count-based cap at all - this does NOT mean "the entire scan finishes in one single, uninterrupted tick" any more (that was an earlier bug - see WorldDataCleanupTask's own MAX_TICK_DURATION_NANOS doc for why a wall-clock time budget is ALSO always enforced regardless of this setting, so the game's own tick loop - and this task's own periodic progress reports - keeps advancing throughout a long scan either way). Set to a positive number (e.g. 50) to additionally cap by chunk count on top of that same time budget, trading a longer total run for a gentler per-tick load - progress is reported periodically regardless of this setting (see WorldDataCleanupTask's own doc). */
	public int worldDataCleanupChunksPerTick = -1;

	/** How many vehicle definitions' hit-detection meshes may be built at the same time, in the background (entity.HitMeshPreloader - the meshes are built as soon as a vehicle of that definition is in a world, not on its first hit). -1 (the default) means no limit: the shared fork-join pool, one build per core. A positive number gives a pool of that many low-priority threads - 1 builds one model at a time, the gentlest on a busy server. Read once, when the first build is queued. */
	public int hitMeshBuildMaxConcurrent = -1;

	/** Fire Support Center (block.FireSupportCenterBlockEntity): how far (blocks) a Fire Support Designator reaches when a player marks a target with it. The point has to be in a loaded chunk - the server traces the player's line of sight through the world as it is loaded. Defaults to 512. */
	public int fireSupportDesignatorRange = 512;

	/** Fire Support Center: the largest relative coordinate (blocks, each axis) the center's screen accepts for a target. Defaults to 4096. */
	public int fireSupportMaxDistance = 4096;

	/** Tells players about vehicles beyond the range the game sends entities in, so that clients with an LOD mod (and renderFarVehicles on) can draw them over its distant terrain (entity.FarVehicleSync). Off by default: it lets players see where far-away vehicles are. */
	public boolean farVehicleSyncEnabled = false;

	/** The furthest (blocks) a vehicle is told about, whatever a client asks for. */
	public int farVehicleSyncMaxDistance = 2048;

	/** How often (ticks) each player is sent the far vehicles; the client moves them on smoothly in between. */
	public int farVehicleSyncIntervalTicks = 10;

	/** The most far vehicles sent to one player at a time - the nearest ones. */
	public int farVehicleSyncMaxCount = 64;

	/** The most far projectiles (missiles, bombs, shells - those drawn with a model, bullet_model; plain bullets aren't sent) sent to one player at a time, nearest first, with far vehicle sync on; 0 for none. */
	public int farVehicleSyncMaxProjectiles = 64;

	/** How often (ticks) each player is sent the far projectiles - more often than vehicles, as they move fast and end suddenly. */
	public int farVehicleSyncProjectileIntervalTicks = 4;

	/** Multiplies every vehicle's top speed (entity.VehicleSpeedScaling): max_speed, and the other speeds a vehicle gives in absolute terms (SweepWingSpeed, dive_max_speed, a catapult launch's speed boost). Acceleration, turning and the rest of a vehicle's handling stay as defined. 1.0 (the default) is unchanged; values under 0.05 are used as 0.05. Applies immediately, and is sent to players' clients (which move vehicles too). */
	public double vehicleSpeedMultiplier = 1.0;

	/** Vehicle speed multipliers for particular entity types, in place of vehicleSpeedMultiplier for those types (entity.VehicleSpeedScaling). Keyed by entity type id - "tudursvehiclemod:aircraft", or just "aircraft" for this mod's own types (helicopter, car, ship, submarine, aircraft, vtol); an addon's vehicle types by their own ids. Empty by default: every type uses vehicleSpeedMultiplier. */
	public java.util.Map<String, Double> vehicleSpeedMultiplierByType = new java.util.LinkedHashMap<>();

	/** Multiplies the damage this mod's weapons do when a projectile hits an entity or a vehicle (entity.DamageScaling). 1.0 (the default) is the damage each weapon file sets; 0 means no damage - a hit only knocks a living target back. Crash, collision and drowning damage isn't affected. Applies immediately. */
	public double weaponDamageMultiplier = 1.0;

	/** Multiplies the damage explosions from this mod's weapons and destroyed vehicles do to entities and vehicles (entity.DamageScaling). 1.0 (the default) is unchanged; 0 means no damage - the blast only pushes entities away (like TNT under water, whose blast only shoves). How far the blast reaches stays the same. Applies immediately. */
	public double explosionBlastMultiplier = 1.0;

	/** Multiplies how far explosions from this mod's weapons and destroyed vehicles break blocks (and set fire), separately from their blast (entity.DamageScaling). 1.0 (the default) is unchanged; 0 means no blocks are broken and no fire is set. Only explosions that would break blocks at all are affected (ExplosionBlock = 0 in the weapon file, the mobGriefing rule). Applies immediately. */
	public double explosionBlockMultiplier = 1.0;

	/** Exposed so VehicleMod's own startup log can state exactly where this file actually lives - the resolved path isn't necessarily obvious. */
	public static Path getConfigPath() {
		return CONFIG_PATH;
	}

	private static VehicleModServerConfig instance;

	/** Loaded once, lazily, on first use - and deliberately NOT reloaded afterwards, so a mid-tick config change can't change threading behavior halfway through a tick's own batch. */
	public static VehicleModServerConfig get() {
		if (instance == null) {
			instance = load();
		}
		return instance;
	}

	private static VehicleModServerConfig load() {
		// A file from an older version gets the settings added since then, with their defaults (ConfigFiles.load()).
		VehicleModServerConfig loaded = com.example.tudursvehiclemod.ConfigFiles.load(CONFIG_PATH, GSON, VehicleModServerConfig.class);
		if (loaded != null) {
			return loaded;
		}
		// Missing, malformed or unreadable - defaults.
		VehicleModServerConfig defaults = new VehicleModServerConfig();
		defaults.save();
		return defaults;
	}

	public void save() {
		// Keeps settings in the file this version doesn't know (ConfigFiles.save()).
		com.example.tudursvehiclemod.ConfigFiles.save(CONFIG_PATH, GSON, this);
	}

}
