package tvmtest;

import com.example.tudursvehiclemod.asset.VehicleDefinition;
import com.example.tudursvehiclemod.asset.VehicleRegistry;
import com.example.tudursvehiclemod.asset.WeaponDefinition;
import com.example.tudursvehiclemod.entity.AbstractVehicleEntity;
import com.example.tudursvehiclemod.entity.DummyPilotEntity;
import com.example.tudursvehiclemod.registry.ModEntityTypes;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.mob.ZombieEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.registry.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.function.IntConsumer;

/**
 * Verification harness for comparing two builds of tudursvehiclemod (Yarn release vs Mojang dev copy).
 * Built against the release jar, like any addon. Drives every vehicle definition through the same
 * scripted steps on the server and writes the observations to tvmtest-<name>.json.
 *
 *   /tvmtest run <name>        full scenario (idle, drive, every weapon, dummy-pilot combat)
 *   /tvmtest park <name>       spawn every vehicle in a park and record it (before a restart)
 *   /tvmtest parkcheck <name>  record the vehicles found in the park (after a restart)
 */
public class TvmTest implements ModInitializer {
	static final Logger LOG = LoggerFactory.getLogger("tvmtest");
	static final int GROUND_Y = -60;
	static final int WATER_TOP = -39;

	/** One scheduled step: run `start` once, then `each` every tick for `ticks` ticks. */
	record Step(String label, Runnable start, int ticks, IntConsumer each) {}

	final Deque<Step> queue = new ArrayDeque<>();
	Step current;
	int currentTick;
	MinecraftServer server;
	JsonObject report;
	String reportName;
	final Map<String, Integer> spawnCounts = new TreeMap<>();
	boolean counting;
	int limit = Integer.MAX_VALUE;
	/** Set once /tvmtest gallery has spawned everything (read by TvmTestClient in singleplayer). */
	static volatile int galleryCount;
	static final List<AbstractVehicleEntity> gallery = new ArrayList<>();

	@Override
	public void onInitialize() {
		ServerEntityEvents.ENTITY_LOAD.register((entity, world) -> {
			if (counting) {
				spawnCounts.merge(Registries.ENTITY_TYPE.getId(entity.getType()).toString(), 1, Integer::sum);
			}
		});
		ServerTickEvents.START_SERVER_TICK.register(srv -> tickStartNanos = System.nanoTime());
		ServerTickEvents.END_SERVER_TICK.register(srv -> {
			if (tickDurations != null) {
				tickDurations.add(System.nanoTime() - tickStartNanos);
			}
		});
		ServerTickEvents.END_SERVER_TICK.register(this::tick);
		ServerTickEvents.END_SERVER_TICK.register(srv -> deckServerTick());
		ServerTickEvents.END_SERVER_TICK.register(srv -> SpeedTest.tick());
		ServerTickEvents.END_SERVER_TICK.register(srv -> DestroyRideTest.tick());
		ServerTickEvents.END_SERVER_TICK.register(srv -> TakeoffTest.tick());
		ServerTickEvents.END_SERVER_TICK.register(srv -> FarVehicleTest.tick());
		ServerTickEvents.END_SERVER_TICK.register(srv -> FarShotTest.tick());
		ServerTickEvents.END_SERVER_TICK.register(srv -> AvoidTest.tick());
		CommandRegistrationCallback.EVENT.register((dispatcher, access, env) -> dispatcher.register(
				CommandManager.literal("tvmtest").requires(src -> true)
						.then(CommandManager.literal("run").then(CommandManager.argument("name", StringArgumentType.word())
								.executes(ctx -> { limit = Integer.MAX_VALUE; start(ctx.getSource().getServer(), StringArgumentType.getString(ctx, "name"), "run"); return 1; })
								.then(CommandManager.argument("limit", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1))
										.executes(ctx -> { limit = com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "limit"); start(ctx.getSource().getServer(), StringArgumentType.getString(ctx, "name"), "run"); return 1; }))))
						.then(CommandManager.literal("park").then(CommandManager.argument("name", StringArgumentType.word())
								.executes(ctx -> { start(ctx.getSource().getServer(), StringArgumentType.getString(ctx, "name"), "park"); return 1; })))
						.then(CommandManager.literal("parkcheck").then(CommandManager.argument("name", StringArgumentType.word())
								.executes(ctx -> { start(ctx.getSource().getServer(), StringArgumentType.getString(ctx, "name"), "parkcheck"); return 1; })))
						.then(CommandManager.literal("carriers").then(CommandManager.argument("name", StringArgumentType.word())
								.then(CommandManager.argument("count", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1))
										.then(CommandManager.argument("ticks", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1))
												.then(CommandManager.argument("mode", StringArgumentType.word())
														.executes(ctx -> { carriers(ctx.getSource().getServer(), StringArgumentType.getString(ctx, "name"),
																com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "count"),
																com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "ticks"),
																StringArgumentType.getString(ctx, "mode")); return 1; }))))))
						.then(CommandManager.literal("stress").then(CommandManager.argument("name", StringArgumentType.word())
								.then(CommandManager.argument("copies", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1))
										.then(CommandManager.argument("ticks", com.mojang.brigadier.arguments.IntegerArgumentType.integer(1))
												.executes(ctx -> { stress(ctx.getSource().getServer(), StringArgumentType.getString(ctx, "name"),
														com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "copies"),
														com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "ticks"), "fire"); return 1; })
												.then(CommandManager.argument("mode", StringArgumentType.word())
														.executes(ctx -> { stress(ctx.getSource().getServer(), StringArgumentType.getString(ctx, "name"),
																com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "copies"),
																com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "ticks"),
																StringArgumentType.getString(ctx, "mode")); return 1; }))))))
						.then(CommandManager.literal("deck").then(CommandManager.argument("name", StringArgumentType.word())
								.executes(ctx -> { deck(ctx.getSource().getServer(), StringArgumentType.getString(ctx, "name")); return 1; })))
						.then(CommandManager.literal("setspeed").then(CommandManager.argument("value", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg(0))
								.executes(ctx -> { com.example.tudursvehiclemod.VehicleModServerConfig.get().vehicleSpeedMultiplier = com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(ctx, "value");
									LOG.info("[tvmtest] SETSPEED {}", com.example.tudursvehiclemod.VehicleModServerConfig.get().vehicleSpeedMultiplier); return 1; })))
						.then(CommandManager.literal("speedtest").then(CommandManager.argument("name", StringArgumentType.word())
								.executes(ctx -> { String n = StringArgumentType.getString(ctx, "name");
									if (n.equals("setup")) SpeedTest.setup(this, ctx.getSource().getWorld()); else SpeedTest.run(this, ctx.getSource().getWorld(), n); return 1; })))
						.then(CommandManager.literal("speeddrive").then(CommandManager.argument("player", StringArgumentType.word())
								.executes(ctx -> { SpeedTest.board(this, ctx.getSource().getWorld(), StringArgumentType.getString(ctx, "player")); return 1; })))
						.then(CommandManager.literal("setspeedtype").then(CommandManager.argument("type", StringArgumentType.string()).then(CommandManager.argument("value", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg(0))
								.executes(ctx -> { String type = StringArgumentType.getString(ctx, "type"); double value = com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(ctx, "value");
									java.util.Map<String, Double> map = com.example.tudursvehiclemod.VehicleModServerConfig.get().vehicleSpeedMultiplierByType;
									if (value <= 0) map.remove(type); else map.put(type, value);
									LOG.info("[tvmtest] SETSPEEDTYPE {}", map); return 1; }))))
						.then(CommandManager.literal("destroyride").then(CommandManager.argument("player", StringArgumentType.word()).then(CommandManager.argument("vehicle", StringArgumentType.string())
								.then(CommandManager.argument("height", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 300))
								.executes(ctx -> { DestroyRideTest.start(this, ctx.getSource().getWorld(), StringArgumentType.getString(ctx, "player"), StringArgumentType.getString(ctx, "vehicle"),
										com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "height")); return 1; })))))
						.then(CommandManager.literal("takeoff").then(CommandManager.argument("player", StringArgumentType.word()).then(CommandManager.argument("ship", StringArgumentType.string()).then(CommandManager.argument("aircraft", StringArgumentType.string())
								.executes(ctx -> { TakeoffTest.start(this, ctx.getSource().getWorld(), StringArgumentType.getString(ctx, "player"), StringArgumentType.getString(ctx, "ship"), StringArgumentType.getString(ctx, "aircraft"), false); return 1; })
								.then(CommandManager.literal("sail")
								.executes(ctx -> { TakeoffTest.start(this, ctx.getSource().getWorld(), StringArgumentType.getString(ctx, "player"), StringArgumentType.getString(ctx, "ship"), StringArgumentType.getString(ctx, "aircraft"), true); return 1; }))))))
						.then(CommandManager.literal("takeoffx").then(CommandManager.argument("x", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg())
								.executes(ctx -> { System.setProperty("tvmtest.takeoffx", Double.toString(com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(ctx, "x"))); return 1; })))
						.then(CommandManager.literal("farveh").then(CommandManager.argument("player", StringArgumentType.word())
								.executes(ctx -> { FarVehicleTest.start(this, ctx.getSource().getWorld(), StringArgumentType.getString(ctx, "player")); return 1; })))
						.then(CommandManager.literal("avoid").then(CommandManager.argument("mode", StringArgumentType.word()).then(CommandManager.argument("obstacle", StringArgumentType.string()).then(CommandManager.argument("aircraft", StringArgumentType.string())
								.then(CommandManager.argument("height", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0, 200))
								.executes(ctx -> { AvoidTest.start(this, ctx.getSource().getWorld(), StringArgumentType.getString(ctx, "mode").equals("on"), StringArgumentType.getString(ctx, "obstacle"),
										StringArgumentType.getString(ctx, "aircraft"), com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "height")); return 1; }))))))
						.then(CommandManager.literal("farshot").then(CommandManager.argument("player", StringArgumentType.word()).then(CommandManager.argument("speed", com.mojang.brigadier.arguments.DoubleArgumentType.doubleArg())
								.executes(ctx -> { FarShotTest.start(ctx.getSource().getWorld(), StringArgumentType.getString(ctx, "player"), com.mojang.brigadier.arguments.DoubleArgumentType.getDouble(ctx, "speed")); return 1; }))))
						.then(CommandManager.literal("seat").then(CommandManager.argument("player", StringArgumentType.word()).then(CommandManager.argument("vehicle", StringArgumentType.string())
								.then(CommandManager.argument("seat", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0)).then(CommandManager.argument("pitch", com.mojang.brigadier.arguments.FloatArgumentType.floatArg())
								.then(CommandManager.argument("roll", com.mojang.brigadier.arguments.FloatArgumentType.floatArg()).then(CommandManager.argument("spin", com.mojang.brigadier.arguments.FloatArgumentType.floatArg())
								.executes(ctx -> { SeatTest.start(this, ctx.getSource().getWorld(), StringArgumentType.getString(ctx, "player"), StringArgumentType.getString(ctx, "vehicle"),
										com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "seat"), com.mojang.brigadier.arguments.FloatArgumentType.getFloat(ctx, "pitch"),
										com.mojang.brigadier.arguments.FloatArgumentType.getFloat(ctx, "roll"), com.mojang.brigadier.arguments.FloatArgumentType.getFloat(ctx, "spin")); return 1; }))))))))
						.then(CommandManager.literal("zonepart").then(CommandManager.argument("vehicle", StringArgumentType.string())
								.executes(ctx -> { ZoneTest.partRun(this, ctx.getSource().getWorld(), StringArgumentType.getString(ctx, "vehicle")); return 1; })))
						.then(CommandManager.literal("zoneshow").then(CommandManager.argument("vehicle", StringArgumentType.string())
								.executes(ctx -> { ZoneTest.show(this, ctx.getSource().getWorld(), StringArgumentType.getString(ctx, "vehicle")); return 1; })))
						.then(CommandManager.literal("zone").then(CommandManager.argument("vehicle", StringArgumentType.string())
								.executes(ctx -> { ZoneTest.run(this, ctx.getSource().getWorld(), StringArgumentType.getString(ctx, "vehicle")); return 1; })))
						.then(CommandManager.literal("carmob").then(CommandManager.argument("player", StringArgumentType.word()).then(CommandManager.argument("vehicle", StringArgumentType.string())
								.executes(ctx -> { SpeedTest.carMob(this, ctx.getSource().getWorld(), StringArgumentType.getString(ctx, "player"), StringArgumentType.getString(ctx, "vehicle")); return 1; }))))
						.then(CommandManager.literal("dmgscale").then(CommandManager.argument("name", StringArgumentType.word())
								.executes(ctx -> { String n = StringArgumentType.getString(ctx, "name");
									if (n.equals("setup")) DmgScaleTest.setup(ctx.getSource().getWorld()); else DmgScaleTest.run(ctx.getSource().getWorld(), n); return 1; })))
						.then(CommandManager.literal("probe").executes(ctx -> { probe(ctx.getSource().getWorld()); return 1; }))
						.then(CommandManager.literal("gallery").executes(ctx -> { gallery(ctx.getSource().getWorld()); return 1; }))
						.then(CommandManager.literal("deckwalk").then(CommandManager.argument("step", StringArgumentType.word())
								.executes(ctx -> { deckwalk(ctx.getSource().getPlayer(), StringArgumentType.getString(ctx, "step")); return 1; })))
						.then(CommandManager.literal("view").then(CommandManager.argument("i", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0))
								.executes(ctx -> { view(ctx.getSource().getPlayer(), com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "i")); return 1; })))
						.then(CommandManager.literal("ride").then(CommandManager.argument("i", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0))
								.executes(ctx -> { ride(ctx.getSource().getPlayer(), com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "i"), true); return 1; })))
						.then(CommandManager.literal("rideend").then(CommandManager.argument("i", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0))
								.executes(ctx -> { ride(ctx.getSource().getPlayer(), com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "i"), false); return 1; })))
						.then(CommandManager.literal("fire").then(CommandManager.argument("i", com.mojang.brigadier.arguments.IntegerArgumentType.integer(0))
								.executes(ctx -> { fireAll(com.mojang.brigadier.arguments.IntegerArgumentType.getInteger(ctx, "i")); return 1; })))));
	}

	// ------------------------------------------------------------------ scheduler
	void tick(MinecraftServer srv) {
		if (current == null) {
			current = queue.poll();
			if (current == null) {
				return;
			}
			currentTick = 0;
			guard(current.label() + "/start", current.start());
		}
		if (currentTick < current.ticks()) {
			final int t = currentTick;
			if (current.each() != null) {
				guard(current.label() + "/tick", () -> current.each().accept(t));
			}
			currentTick++;
		}
		if (currentTick >= current.ticks()) {
			current = null;
		}
	}

	void guard(String where, Runnable r) {
		if (r == null) {
			return;
		}
		try {
			r.run();
		} catch (Throwable t) {
			StringWriter sw = new StringWriter();
			t.printStackTrace(new PrintWriter(sw));
			LOG.error("[tvmtest] exception in {}", where, t);
			JsonObject e = new JsonObject();
			e.addProperty("where", where);
			e.addProperty("exception", sw.toString().lines().limit(6).reduce("", (a, b) -> a + b.trim() + " | "));
			report.getAsJsonArray("exceptions").add(e);
		}
	}

	void then(String label, Runnable start) {
		queue.add(new Step(label, start, 0, null));
	}

	void wait(String label, int ticks, IntConsumer each) {
		queue.add(new Step(label, null, ticks, each));
	}

	// ------------------------------------------------------------------ scenarios
	void start(MinecraftServer srv, String name, String mode) {
		this.server = srv;
		this.reportName = name;
		this.report = new JsonObject();
		report.addProperty("mode", mode);
		report.add("exceptions", new JsonArray());
		ServerWorld world = srv.getOverworld();
		List<Identifier> ids = new ArrayList<>(VehicleRegistry.getAll().keySet());
		ids.sort(java.util.Comparator.comparing(Identifier::toString));
		report.addProperty("definitionCount", ids.size());
		LOG.info("[tvmtest] {} {} with {} definitions", mode, name, ids.size());
		switch (mode) {
			case "run" -> {
				JsonObject vehicles = new JsonObject();
				report.add("vehicles", vehicles);
				for (int i = 0; i < ids.size() && i < limit; i++) {
					scheduleVehicle(world, ids.get(i), 2000 + i * 320, vehicles);
				}
			}
			case "park" -> schedulePark(world, ids, true);
			case "parkcheck" -> schedulePark(world, ids, false);
			default -> { }
		}
		then("write", this::write);
	}

	void write() {
		try {
			Path out = server.getRunDirectory().resolve("tvmtest-" + reportName + ".json");
			Files.writeString(out, new GsonBuilder().setPrettyPrinting().create().toJson(report), StandardCharsets.UTF_8);
			LOG.info("[tvmtest] DONE {} -> {}", reportName, out);
		} catch (Exception e) {
			LOG.error("[tvmtest] write failed", e);
		}
	}

	boolean isWater(VehicleDefinition def) {
		String t = def.entityType().getPath();
		return t.equals("ship") || t.equals("submarine");
	}

	void scheduleVehicle(ServerWorld world, Identifier id, int siteX, JsonObject vehicles) {
		VehicleDefinition def = VehicleRegistry.get(id).orElseThrow();
		JsonObject rec = new JsonObject();
		vehicles.add(id.toString(), rec);
		rec.addProperty("entityType", def.entityType().toString());
		rec.addProperty("weaponCount", def.weapons().size());
		final AbstractVehicleEntity[] v = new AbstractVehicleEntity[1];
		Vec3d origin = new Vec3d(siteX + 0.5, isWater(def) ? WATER_TOP : GROUND_Y, 0.5);

		then(id + "/prepare", () -> {
			forceChunks(world, siteX, true);
			if (isWater(def)) {
				buildPool(world, siteX);
			}
		});
		// 1. Spawn and idle.
		then(id + "/spawn", () -> v[0] = spawn(world, id, origin));
		wait(id + "/idle", 60, null);
		then(id + "/idle-record", () -> rec.add("idle", state(v[0])));
		// 2. Drive with a fake pilot at full throttle.
		final DummyPilotEntity[] driver = new DummyPilotEntity[1];
		then(id + "/mount", () -> {
			driver[0] = (DummyPilotEntity) ModEntityTypes.DUMMY_PILOT.create(world, SpawnReason.COMMAND);
			driver[0].refreshPositionAndAngles(v[0].getX(), v[0].getY(), v[0].getZ(), 0f, 0f);
			world.spawnEntity(driver[0]);
			fillFuel(v[0]);
			rec.addProperty("pilotMounted", driver[0].startRiding(v[0], true, false));
		});
		wait(id + "/drive", 160, t -> {
			if (v[0] != null && !v[0].isRemoved()) {
				v[0].setSyncedThrottleInput(1.0f);
			}
		});
		then(id + "/drive-record", () -> {
			rec.add("drive", state(v[0]));
			driver[0].discard();
			v[0].discard();
		});
		// 3. Every weapon, unmanned, at a husk 40 blocks ahead.
		JsonArray weapons = new JsonArray();
		rec.add("weapons", weapons);
		for (int w = 0; w < def.weapons().size(); w++) {
			final int wi = w;
			WeaponDefinition wd = def.weapons().get(w);
			JsonObject wr = new JsonObject();
			weapons.add(wr);
			final LivingEntity[] target = new LivingEntity[1];
			final int[] fired = new int[1];
			then(id + "/w" + w + "/setup", () -> {
				wr.addProperty("type", String.valueOf(wd.weaponType()));
				v[0] = spawn(world, id, origin);
				fillAmmo(v[0]);
				target[0] = husk(world, new Vec3d(origin.x, GROUND_Y, origin.z + 40));
				spawnCounts.clear();
				counting = true;
			});
			wait(id + "/w" + w + "/settle", 20, null);
			wait(id + "/w" + w + "/fire", 60, t -> {
				if (v[0] != null && !v[0].isRemoved()
						&& v[0].tudursvehiclemod$tryFireWeaponAt(wi, target[0].getEntityPos().add(0, 1, 0))) {
					fired[0]++;
				}
			});
			wait(id + "/w" + w + "/resolve", 100, null);
			then(id + "/w" + w + "/record", () -> {
				counting = false;
				wr.addProperty("fired", fired[0]);
				JsonObject sc = new JsonObject();
				spawnCounts.forEach(sc::addProperty);
				wr.add("spawned", sc);
				wr.addProperty("targetAlive", target[0].isAlive());
				wr.addProperty("targetHealth", round(target[0].getHealth()));
				wr.addProperty("magazine", v[0].tudursvehiclemod$getWeaponMagazineAmmo(wi));
				wr.addProperty("reserve", v[0].tudursvehiclemod$getWeaponReserveAmmo(wi));
				wr.add("vehicle", state(v[0]));
				cleanup(world, siteX);
			});
		}
		// 4. Dummy pilots in every seat against zombies.
		JsonObject combat = new JsonObject();
		rec.add("dummyCombat", combat);
		final List<LivingEntity> zombies = new ArrayList<>();
		then(id + "/combat-setup", () -> {
			v[0] = spawn(world, id, origin);
			fillAmmo(v[0]);
			int seats = Math.max(1, v[0].getDefinition().seats().size());
			int mounted = 0;
			for (int s = 0; s < seats; s++) {
				DummyPilotEntity p = (DummyPilotEntity) ModEntityTypes.DUMMY_PILOT.create(world, SpawnReason.COMMAND);
				p.refreshPositionAndAngles(origin.x, origin.y, origin.z, 0f, 0f);
				world.spawnEntity(p);
				if (p.startRiding(v[0], true, false)) {
					mounted++;
				}
			}
			combat.addProperty("pilotsMounted", mounted);
			zombies.clear();
			for (int z = 0; z < 4; z++) {
				zombies.add(zombie(world, new Vec3d(origin.x - 6 + z * 4, GROUND_Y, origin.z + 25)));
			}
			spawnCounts.clear();
			counting = true;
		});
		wait(id + "/combat", 200, null);
		then(id + "/combat-record", () -> {
			counting = false;
			JsonObject sc = new JsonObject();
			spawnCounts.forEach(sc::addProperty);
			combat.add("spawned", sc);
			double hp = 0;
			int alive = 0;
			for (LivingEntity z : zombies) {
				if (z.isAlive()) {
					alive++;
					hp += z.getHealth();
				}
			}
			combat.addProperty("zombiesAlive", alive);
			combat.addProperty("zombieHealth", round(hp));
			combat.add("vehicle", state(v[0]));
			cleanup(world, siteX);
			forceChunks(world, siteX, false);
		});
	}

	void schedulePark(ServerWorld world, List<Identifier> ids, boolean spawnFirst) {
		JsonObject park = new JsonObject();
		report.add("park", park);
		then("park-chunks", () -> {
			for (int cx = -1; cx <= (ids.size() * 24) / 16 + 1; cx++) {
				for (int cz = 49; cz <= 54; cz++) {
					force(world, cx, cz, true);
				}
			}
			if (spawnFirst) {
				for (int i = 0; i < ids.size(); i++) {
					VehicleDefinition def = VehicleRegistry.get(ids.get(i)).orElseThrow();
					if (isWater(def)) {
						// small pond under water vehicles
						for (int x = i * 24 - 8; x <= i * 24 + 8; x++) for (int z = 800 - 16; z <= 800 + 16; z++) for (int y = -63; y <= WATER_TOP - 1; y++) {
							boolean wall = x == i * 24 - 8 || x == i * 24 + 8 || z == 800 - 16 || z == 800 + 16;
							world.setBlockState(new BlockPos(x, y, z), wall ? Blocks.STONE.getDefaultState() : Blocks.WATER.getDefaultState(), Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
						}
					}
					spawn(world, ids.get(i), new Vec3d(i * 24 + 0.5, isWater(def) ? WATER_TOP : GROUND_Y, 800.5));
				}
			}
		});
		wait("park-wait", spawnFirst ? 60 : 100, null);
		then("park-record", () -> {
			List<AbstractVehicleEntity> found = new ArrayList<>();
			for (Entity e : world.iterateEntities()) {
				if (e instanceof AbstractVehicleEntity av && Math.abs(av.getZ() - 800) < 40) {
					found.add(av);
				}
			}
			found.sort(java.util.Comparator.comparing(a -> a.getVehicleDefinitionId().toString()));
			for (AbstractVehicleEntity av : found) {
				park.add(av.getVehicleDefinitionId().toString(), state(av));
			}
			report.addProperty("parkCount", found.size());
		});
	}

	void probe(ServerWorld world) {
		this.report = new JsonObject();
		report.add("exceptions", new JsonArray());
		Identifier id = Identifier.of("ww2fict", "a_fighter");
		AbstractVehicleEntity v = spawn(world, id, new Vec3d(0.5, GROUND_Y, 0.5));
		FakePlayer fp = FakePlayer.get(world, new GameProfile(UUID.nameUUIDFromBytes("probe".getBytes()), "probe"));
		fp.refreshPositionAndAngles(0.5, GROUND_Y, 0.5, 0f, 0f);
		LOG.info("[tvmtest] probe fp removed={} alive={} world={} couldAccept={} canStart={} canAdd={}", fp.isRemoved(), fp.isAlive(), fp.getEntityWorld() == world,
				v.getPassengerList().size(), "?", v.canAddPassenger(fp));
		LOG.info("[tvmtest] probe startRiding(force)={} vehicle={}", fp.startRiding(v, true, false), fp.getVehicle());
		LOG.info("[tvmtest] probe startRiding(no force)={} vehicle={}", fp.startRiding(v, false, true), fp.getVehicle());
		LOG.info("[tvmtest] probe ammo before {}", v.tudursvehiclemod$getWeaponMagazineAmmo(0));
		fillAmmo(v);
		LOG.info("[tvmtest] probe fire={}", v.tryFireWeapon(0, null));
	}

	/** Vehicles spawn with empty magazines and resupply needs a seated player; fill them directly (field names are the mod's own, identical in both jars). */
	static void fillAmmo(AbstractVehicleEntity v) {
		try {
			VehicleDefinition def = v.getDefinition();
			java.lang.reflect.Method size = AbstractVehicleEntity.class.getDeclaredMethod("tudursvehiclemod$ensureWeaponAmmoArraysSized", VehicleDefinition.class);
			size.setAccessible(true);
			size.invoke(v, def);
			java.lang.reflect.Field ammo = AbstractVehicleEntity.class.getDeclaredField("weaponAmmo");
			java.lang.reflect.Field reserve = AbstractVehicleEntity.class.getDeclaredField("weaponReserveAmmo");
			ammo.setAccessible(true);
			reserve.setAccessible(true);
			int[] a = (int[]) ammo.get(v);
			int[] r = (int[]) reserve.get(v);
			for (int i = 0; i < def.weapons().size() && i < a.length; i++) {
				WeaponDefinition w = def.weapons().get(i);
				a[i] = Math.max(1, w.magazineSize());
				if (r[i] >= 0) {
					r[i] = Math.max(0, w.maxAmmo() - a[i]);
				}
			}
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException(e);
		}
	}

	// ------------------------------------------------------------------ load test
	long tickStartNanos;
	List<Long> tickDurations;

	/**
	 * Load test for comparing tick cost between two builds: `copies` of every definition, a dummy pilot in every seat,
	 * tough targets in front of each, everything firing for `ticks` ticks. Writes the server tick durations
	 * (START_SERVER_TICK to END_SERVER_TICK) to tvmtest-<name>.json. mode "idle" spawns the vehicles only (empty, parked);
	 * "manned" seats the pilots and places the targets but nothing is fired by the harness.
	 */
	void stress(MinecraftServer srv, String name, int copies, int ticks, String mode) {
		final boolean crew = !mode.equals("idle");
		final boolean fire = mode.equals("fire");
		this.server = srv;
		this.reportName = name;
		this.report = new JsonObject();
		report.addProperty("mode", "stress-" + mode);
		report.add("exceptions", new JsonArray());
		ServerWorld world = srv.getOverworld();
		List<Identifier> ids = new ArrayList<>(VehicleRegistry.getAll().keySet());
		ids.sort(java.util.Comparator.comparing(Identifier::toString));
		final List<AbstractVehicleEntity> fleet = new ArrayList<>();
		// A compact grid (about what one player's view distance keeps loaded), so the share of vanilla chunk ticking stays realistic.
		final int columns = 10, spacingX = 40, spacingZ = 56;
		final int total = ids.size() * copies;
		final int rows = (total + columns - 1) / columns;
		then("stress/setup", () -> {
			for (int cx = -2; cx <= (columns * spacingX) / 16 + 1; cx++) {
				for (int cz = -2; cz <= (rows * spacingZ) / 16 + 2; cz++) {
					force(world, cx, cz, true);
				}
			}
		});
		wait("stress/chunks", 40, null);
		then("stress/spawn", () -> {
			for (int n = 0; n < total; n++) {
				final Identifier id = ids.get(n % ids.size());
				VehicleDefinition def = VehicleRegistry.get(id).orElseThrow();
				final int x = (n % columns) * spacingX;
				final int zBase = (n / columns) * spacingZ;
				guard("stress/" + id, () -> {
					if (isWater(def)) {
						for (int px = x - 16; px <= x + 16; px++) for (int pz = zBase - 20; pz <= zBase + 30; pz++) for (int y = -63; y <= WATER_TOP - 1; y++) {
							boolean wall = px == x - 16 || px == x + 16 || pz == zBase - 20 || pz == zBase + 30;
							world.setBlockState(new BlockPos(px, y, pz), wall ? Blocks.STONE.getDefaultState() : Blocks.WATER.getDefaultState(), Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
						}
					}
					double z = zBase + 0.5;
					AbstractVehicleEntity v = spawn(world, id, new Vec3d(x + 0.5, isWater(def) ? WATER_TOP : GROUND_Y, z));
					fillAmmo(v);
					fillFuel(v);
					int seats = Math.max(1, def.seats().size());
					for (int seat = 0; crew && seat < seats; seat++) {
						DummyPilotEntity p = (DummyPilotEntity) ModEntityTypes.DUMMY_PILOT.create(world, SpawnReason.COMMAND);
						p.refreshPositionAndAngles(v.getX(), v.getY(), v.getZ(), 0f, 0f);
						world.spawnEntity(p);
						p.startRiding(v, true, false);
					}
					for (int t = 0; crew && t < 2; t++) {
						husk(world, new Vec3d(x - 3 + t * 6, isWater(def) ? WATER_TOP : GROUND_Y, z + 24));
					}
					fleet.add(v);
				});
			}
			report.addProperty("vehicles", fleet.size());
		});
		wait("stress/settle", 100, null);
		then("stress/begin", () -> tickDurations = new ArrayList<>());
		wait("stress/run", ticks, t -> {
			for (AbstractVehicleEntity v : fleet) {
				if (v.isRemoved() || !fire) {
					continue;
				}
				if (t % 20 == 0) {
					fillAmmo(v);
				}
				if (t % 4 == 0) {
					for (int w = 0; w < v.getDefinition().weapons().size(); w++) {
						final int wi = w;
						guard("stress-fire/" + v.getVehicleDefinitionId() + "/" + w, () -> v.tryFireWeapon(wi, null));
					}
				}
			}
		});
		then("stress/record", () -> {
			List<Long> d = tickDurations;
			tickDurations = null;
			long[] sorted = d.stream().mapToLong(Long::longValue).sorted().toArray();
			double sum = 0;
			for (long v : sorted) {
				sum += v;
			}
			report.addProperty("ticks", sorted.length);
			report.addProperty("meanMs", round(sum / sorted.length / 1.0e6));
			report.addProperty("p50Ms", round(sorted[sorted.length / 2] / 1.0e6));
			report.addProperty("p95Ms", round(sorted[(int) (sorted.length * 0.95)] / 1.0e6));
			report.addProperty("maxMs", round(sorted[sorted.length - 1] / 1.0e6));
			int alive = 0, destroyed = 0, entities = 0;
			for (AbstractVehicleEntity v : fleet) {
				if (!v.isRemoved()) {
					alive++;
					if (v.tudursvehiclemod$isDestroyed()) {
						destroyed++;
					}
				}
			}
			for (Entity e : world.iterateEntities()) {
				entities++;
			}
			report.addProperty("fleetAlive", alive);
			report.addProperty("fleetDestroyed", destroyed);
			report.addProperty("entitiesAtEnd", entities);
		});
		then("write", this::write);
	}

	// ------------------------------------------------------------------ carrier load test
	/**
	 * Runway cost: `count` copies of every definition with a runway, each in its own water lane, with zombies walking on
	 * the deck. mode "park": the ships stay put; "sail": a dummy pilot drives each one at full throttle. Records the
	 * server tick durations (as stress does) and, at the end, how many of the zombies are still on their deck.
	 */
	void carriers(MinecraftServer srv, String name, int count, int ticks, String mode) {
		this.server = srv;
		this.reportName = name;
		this.report = new JsonObject();
		report.addProperty("mode", "carriers-" + mode);
		report.add("exceptions", new JsonArray());
		ServerWorld world = srv.getOverworld();
		List<Identifier> ids = new ArrayList<>();
		for (Identifier id : VehicleRegistry.getAll().keySet()) {
			if (!VehicleRegistry.get(id).orElseThrow().runways().isEmpty() && isWater(VehicleRegistry.get(id).orElseThrow())) {
				ids.add(id);
			}
		}
		ids.sort(java.util.Comparator.comparing(Identifier::toString));
		final boolean sail = mode.equals("sail");
		final int laneWidth = 40, laneLength = 320;
		final int total = ids.size() * count;
		final List<AbstractVehicleEntity> fleet = new ArrayList<>();
		final List<Entity> riders = new ArrayList<>();
		then("carriers/setup", () -> {
			for (int cx = -2; cx <= (total * laneWidth) / 16 + 1; cx++) {
				for (int cz = -4; cz <= laneLength / 16 + 2; cz++) {
					force(world, cx, cz, true);
				}
			}
		});
		wait("carriers/chunks", 60, null);
		then("carriers/spawn", () -> {
			for (int n = 0; n < total; n++) {
				final Identifier id = ids.get(n % ids.size());
				final int x = n * laneWidth;
				guard("carriers/" + id, () -> {
					for (int px = x - laneWidth / 2; px < x + laneWidth / 2; px++) for (int pz = -40; pz <= laneLength; pz++) for (int y = WATER_TOP - 11; y <= WATER_TOP - 1; y++) {
						boolean wall = y == WATER_TOP - 11 || px == x - laneWidth / 2 || px == x + laneWidth / 2 - 1 || pz == -40 || pz == laneLength;
						world.setBlockState(new BlockPos(px, y, pz), wall ? Blocks.STONE.getDefaultState() : Blocks.WATER.getDefaultState(), Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
					}
					AbstractVehicleEntity v = spawn(world, id, new Vec3d(x + 0.5, WATER_TOP, 0.5));
					fillFuel(v);
					fleet.add(v);
				});
			}
			report.addProperty("vehicles", fleet.size());
		});
		wait("carriers/tiles", 200, null);
		then("carriers/board", () -> {
			int tiles = 0;
			for (Entity e : world.iterateEntities()) {
				if (e instanceof com.example.tudursvehiclemod.entity.CarrierRunwayPlatformEntity) {
					tiles++;
				}
			}
			report.addProperty("tiles", tiles);
			for (AbstractVehicleEntity v : fleet) {
				var runway = v.getDefinition().runways().get(0);
				double midZ = (runway.startZ() + runway.endZ()) / 2.0;
				for (int i = 0; i < 6; i++) {
					// Three zombies walking about (their every step asks for the collision shapes around them), three armor stands that should simply stay where they are on the deck.
					Entity z = i < 3 ? EntityType.ZOMBIE.create(world, SpawnReason.COMMAND) : EntityType.ARMOR_STAND.create(world, SpawnReason.COMMAND);
					if (z instanceof ZombieEntity zombie) {
						zombie.setPersistent();
					}
					z.refreshPositionAndAngles(v.getX() + runway.centerX() - 3 + (i % 3) * 3, v.getY() + runway.heightY() + 1.0, v.getZ() + midZ - 6 + (i / 3) * 12, 0f, 0f);
					world.spawnEntity(z);
					riders.add(z);
				}
			}
		});
		wait("carriers/settle", 60, null);
		then("carriers/begin", () -> {
			// Under way only once everything has settled on the deck.
			for (AbstractVehicleEntity v : fleet) {
					if (sail) {
						// Driven along its lane at 0.3 blocks/tick, swinging 8 degrees either way: moving and turning, as a carrier under way does.
						final double startX = v.getX(), startY = v.getY(), startZ = v.getZ();
						final int[] age = {0};
						v.tudursvehiclemod$setExternalMotionController(vehicle -> {
							int a = age[0]++;
							double yaw = Math.toRadians(8.0 * Math.sin(a / 40.0));
							org.joml.Quaternionf q = new org.joml.Quaternionf().rotationY((float) -yaw);
							vehicle.tudursvehiclemod$applyExternalPose(new Vec3d(startX + 3.0 * Math.sin(a / 80.0), startY, startZ + 0.3 * a), q);
							return true;
						});
					}
			}
			tickDurations = new ArrayList<>();
		});
		wait("carriers/run", ticks, null);
		then("carriers/record", () -> {
			List<Long> d = tickDurations;
			tickDurations = null;
			long[] sorted = d.stream().mapToLong(Long::longValue).sorted().toArray();
			double sum = 0;
			for (long v : sorted) {
				sum += v;
			}
			report.addProperty("ticks", sorted.length);
			report.addProperty("meanMs", round(sum / sorted.length / 1.0e6));
			report.addProperty("p50Ms", round(sorted[sorted.length / 2] / 1.0e6));
			report.addProperty("p95Ms", round(sorted[(int) (sorted.length * 0.95)] / 1.0e6));
			report.addProperty("maxMs", round(sorted[sorted.length - 1] / 1.0e6));
			int onDeck = 0;
			double travelled = 0;
			for (AbstractVehicleEntity v : fleet) {
				travelled += Math.abs(v.getZ() - 0.5);
			}
			for (Entity e : riders) {
				AbstractVehicleEntity nearest = null;
				for (AbstractVehicleEntity v : fleet) {
					if (nearest == null || v.squaredDistanceTo(e) < nearest.squaredDistanceTo(e)) {
						nearest = v;
					}
				}
				if (!e.isRemoved() && nearest != null) {
					double deckY = nearest.getY() + nearest.getDefinition().runways().get(0).heightY();
					if (Math.abs(e.getY() - deckY) < 1.5) {
						onDeck++;
					}
				}
			}
			int standsOnDeck = 0, stands = 0;
			for (Entity e : riders) {
				if (!(e instanceof net.minecraft.entity.decoration.ArmorStandEntity)) {
					continue;
				}
				stands++;
				for (AbstractVehicleEntity v : fleet) {
					var runway = v.getDefinition().runways().get(0);
					double deckY = v.getY() + runway.heightY();
					double dx = e.getX() - v.getX(), dz = e.getZ() - v.getZ();
					if (!e.isRemoved() && Math.abs(e.getY() - deckY) < 0.25 && dx * dx + dz * dz < 60 * 60) {
						standsOnDeck++;
						break;
					}
				}
			}
			report.addProperty("standsOnDeck", standsOnDeck + "/" + stands);
			JsonArray where = new JsonArray();
			for (int i = 0; i < riders.size() && i < 12; i++) {
				Entity e = riders.get(i);
				AbstractVehicleEntity v = fleet.get(i / 6);
				where.add(Registries.ENTITY_TYPE.getId(e.getType()).getPath() + " dy=" + round(e.getY() - v.getY() - v.getDefinition().runways().get(0).heightY())
						+ " dx=" + round(e.getX() - v.getX()) + " dz=" + round(e.getZ() - v.getZ()));
			}
			report.add("where", where);
			report.addProperty("ridersOnDeck", onDeck);
			report.addProperty("riders", riders.size());
			report.addProperty("meanTravel", round(travelled / Math.max(1, fleet.size())));
		});
		then("write", this::write);
	}

	// ------------------------------------------------------------------ runway deck
	/**
	 * For every definition with a runway: things dropped onto the deck (an armor stand, a zombie, a boat, an item) must come
	 * to rest on it, and stay on it while the ship sails. Writes where each ended up, relative to the deck, to tvmtest-<name>.json.
	 */
	void deck(MinecraftServer srv, String name) {
		this.server = srv;
		this.reportName = name;
		this.report = new JsonObject();
		report.addProperty("mode", "deck");
		report.add("exceptions", new JsonArray());
		ServerWorld world = srv.getOverworld();
		List<Identifier> ids = new ArrayList<>(VehicleRegistry.getAll().keySet());
		ids.sort(java.util.Comparator.comparing(Identifier::toString));
		int site = 0;
		for (Identifier id : ids) {
			VehicleDefinition def = VehicleRegistry.get(id).orElseThrow();
			if (def.runways().isEmpty()) {
				continue;
			}
			final int siteX = 4000 + site++ * 320;
			final JsonObject rec = new JsonObject();
			report.add(id.toString(), rec);
			final AbstractVehicleEntity[] v = new AbstractVehicleEntity[1];
			final List<Entity> cargo = new ArrayList<>();
			final var runway = def.runways().get(0);
			final double midZ = (runway.startZ() + runway.endZ()) / 2.0;
			then(id + "/deck-prepare", () -> {
				forceChunks(world, siteX, true);
				if (isWater(def)) {
					buildPool(world, siteX);
				}
				v[0] = spawn(world, id, new Vec3d(siteX + 0.5, isWater(def) ? WATER_TOP : GROUND_Y, 0.5));
				fillFuel(v[0]);
			});
			wait(id + "/deck-tiles", 200, null);
			then(id + "/deck-drop", () -> {
				double deckY = v[0].getY() + runway.heightY();
				EntityType<?>[] types = {EntityType.ARMOR_STAND, EntityType.ZOMBIE, EntityType.OAK_BOAT, EntityType.PIG};
				for (int i = 0; i < types.length; i++) {
					Entity e = types[i].create(world, SpawnReason.COMMAND);
					if (e instanceof MobEntity mob) {
						mob.setPersistent();
						mob.setAiDisabled(false);
					}
					e.refreshPositionAndAngles(v[0].getX() + runway.centerX() - 3 + i * 2, deckY + 3.0, v[0].getZ() + midZ, 0f, 0f);
					world.spawnEntity(e);
					cargo.add(e);
				}
				int tiles = 0;
				for (Entity e : world.iterateEntities()) {
					if (e instanceof com.example.tudursvehiclemod.entity.CarrierRunwayPlatformEntity && Math.abs(e.getX() - siteX) < 300) {
						tiles++;
					}
				}
				rec.addProperty("tiles", tiles);
			});
			wait(id + "/deck-settle", 100, null);
			then(id + "/deck-rest", () -> {
				rec.add("atRest", deckState(v[0], runway.heightY(), cargo));
				DummyPilotEntity p = (DummyPilotEntity) ModEntityTypes.DUMMY_PILOT.create(world, SpawnReason.COMMAND);
				p.refreshPositionAndAngles(v[0].getX(), v[0].getY(), v[0].getZ(), 0f, 0f);
				world.spawnEntity(p);
				p.startRiding(v[0], true, false);
			});
			wait(id + "/deck-sail", 200, t -> {
				if (v[0] != null && !v[0].isRemoved()) {
					v[0].setSyncedThrottleInput(1.0f);
				}
			});
			then(id + "/deck-sailed", () -> {
				rec.add("afterSailing", deckState(v[0], runway.heightY(), cargo));
				rec.add("ship", state(v[0]));
				cleanup(world, siteX);
				forceChunks(world, siteX, false);
			});
		}
		then("write", this::write);
	}

	JsonObject deckState(AbstractVehicleEntity ship, double deckHeight, List<Entity> cargo) {
		JsonObject o = new JsonObject();
		for (Entity e : cargo) {
			JsonObject c = new JsonObject();
			c.addProperty("removed", e.isRemoved());
			c.addProperty("aboveDeck", round(e.getY() - (ship.getY() + deckHeight)));
			c.addProperty("offsetX", round(e.getX() - ship.getX()));
			c.addProperty("offsetZ", round(e.getZ() - ship.getZ()));
			o.add(Registries.ENTITY_TYPE.getId(e.getType()).getPath(), c);
		}
		return o;
	}

	// ------------------------------------------------------------------ deck walk (client)
	/** The carrier of the deck-walk test (read by TvmTestClient in singleplayer). */
	static volatile AbstractVehicleEntity deckShip;
	static int deckServerOn, deckServerOff;
	static net.minecraft.server.network.ServerPlayerEntity deckPlayer;

	java.nio.file.Path srvDir() {
		return server.getRunDirectory();
	}

	/** Server side of the deck-walk test: whether the server has the player on the deck, every tick. */
	void deckServerTick() {
		AbstractVehicleEntity ship = deckShip;
		net.minecraft.server.network.ServerPlayerEntity player = deckPlayer;
		if (ship == null || player == null || ship.isRemoved() || player.isRemoved()) {
			return;
		}
		double dy = player.getY() - (ship.getY() + ship.getDefinition().runways().get(0).heightY());
		if (Math.abs(dy) < 0.3) {
			deckServerOn++;
		} else {
			deckServerOff++;
		}
	}

	/** Deck-walk test, driven by TvmTestClient: "setup" puts a carrier in a pool and the player on its deck, "sail" sets it under way (as /tvmtest carriers sail does). */
	void deckwalk(net.minecraft.server.network.ServerPlayerEntity player, String step) {
		ServerWorld world = (ServerWorld) player.getEntityWorld();
		if (step.equals("setup")) {
			Identifier id = null;
			for (Identifier candidate : VehicleRegistry.getAll().keySet()) {
				VehicleDefinition def = VehicleRegistry.get(candidate).orElseThrow();
				if (!def.runways().isEmpty() && isWater(def) && (id == null || candidate.toString().compareTo(id.toString()) < 0)) {
					id = candidate;
				}
			}
			final int x = 6000;
			for (int cx = (x - 40) >> 4; cx <= (x + 40) >> 4; cx++) for (int cz = -4; cz <= 22; cz++) force(world, cx, cz, true);
			for (int px = x - 20; px < x + 20; px++) for (int pz = -40; pz <= 330; pz++) for (int y = WATER_TOP - 11; y <= WATER_TOP - 1; y++) {
				boolean wall = y == WATER_TOP - 11 || px == x - 20 || px == x + 19 || pz == -40 || pz == 330;
				world.setBlockState(new BlockPos(px, y, pz), wall ? Blocks.STONE.getDefaultState() : Blocks.WATER.getDefaultState(), Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
			}
			AbstractVehicleEntity v = spawn(world, id, new Vec3d(x + 0.5, WATER_TOP, 0.5));
			fillFuel(v);
			deckShip = v;
			deckPlayer = player;
			this.server = player.getEntityWorld().getServer();
			var runway = v.getDefinition().runways().get(0);
			double midZ = (runway.startZ() + runway.endZ()) / 2.0;
			player.changeGameMode(net.minecraft.world.GameMode.SURVIVAL);
			// Next to the ship for now: boarded with "board" once the deck is there (the 1.1.2 tiles took a while to appear).
			player.teleport(world, v.getX() + 18, WATER_TOP + 2, v.getZ(), java.util.Set.of(), 0f, 10f, true);
			player.getAbilities().flying = false;
			LOG.info("[tvmtest] deckwalk on {}", id);
		} else if (step.equals("board") && deckShip != null) {
			AbstractVehicleEntity v = deckShip;
			var runway = v.getDefinition().runways().get(0);
			double midZ = (runway.startZ() + runway.endZ()) / 2.0;
			player.teleport(world, v.getX() + runway.centerX(), v.getY() + runway.heightY() + 0.5, v.getZ() + midZ - 10, java.util.Set.of(), 0f, 10f, true);
		} else if (step.equals("report")) {
			JsonObject r = new JsonObject();
			r.addProperty("serverTicksOnDeck", deckServerOn);
			r.addProperty("serverTicksOffDeck", deckServerOff);
			try {
				java.nio.file.Files.writeString(srvDir().resolve("tvmtest-deckwalk-server.json"), new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(r));
			} catch (java.io.IOException e) {
				LOG.error("[tvmtest] deckwalk server report", e);
			}
		} else if (step.equals("sail") && deckShip != null) {
			final double startX = deckShip.getX(), startY = deckShip.getY(), startZ = deckShip.getZ();
			final int[] age = {0};
			deckShip.tudursvehiclemod$setExternalMotionController(vehicle -> {
				int a = age[0]++;
				double yaw = Math.toRadians(8.0 * Math.sin(a / 40.0));
				org.joml.Quaternionf q = new org.joml.Quaternionf().rotationY((float) -yaw);
				vehicle.tudursvehiclemod$applyExternalPose(new Vec3d(startX + 3.0 * Math.sin(a / 80.0), startY, startZ + 0.3 * a), q);
				return true;
			});
		}
	}

	// ------------------------------------------------------------------ client gallery
	void gallery(ServerWorld world) {
		this.report = new JsonObject();
		report.add("exceptions", new JsonArray());
		List<Identifier> ids = new ArrayList<>(VehicleRegistry.getAll().keySet());
		ids.sort(java.util.Comparator.comparing(Identifier::toString));
		gallery.clear();
		for (int i = 0; i < ids.size(); i++) {
			final int idx = i;
			guard("gallery/" + ids.get(i), () -> {
				VehicleDefinition def = VehicleRegistry.get(ids.get(idx)).orElseThrow();
				int x = idx * 64;
				if (isWater(def)) {
					for (int px = x - 20; px <= x + 20; px++) for (int pz = -20; pz <= 30; pz++) for (int y = -63; y <= WATER_TOP - 1; y++) {
						boolean wall = px == x - 20 || px == x + 20 || pz == -20 || pz == 30;
						world.setBlockState(new BlockPos(px, y, pz), wall ? Blocks.STONE.getDefaultState() : Blocks.WATER.getDefaultState(), Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
					}
				}
				AbstractVehicleEntity v = spawn(world, ids.get(idx), new Vec3d(x + 0.5, isWater(def) ? WATER_TOP : GROUND_Y, 0.5));
				fillAmmo(v);
				int seats = Math.max(1, def.seats().size());
				for (int seat = 0; seat < seats; seat++) {
					DummyPilotEntity p = (DummyPilotEntity) ModEntityTypes.DUMMY_PILOT.create(world, SpawnReason.COMMAND);
					p.refreshPositionAndAngles(v.getX(), v.getY(), v.getZ(), 0f, 0f);
					world.spawnEntity(p);
					p.startRiding(v, true, false);
				}
				gallery.add(v);
			});
		}
		LOG.info("[tvmtest] gallery spawned {} vehicles, exceptions {}", gallery.size(), report.getAsJsonArray("exceptions").size());
		galleryCount = gallery.size();
	}

	void view(net.minecraft.server.network.ServerPlayerEntity player, int i) {
		if (player == null || i >= gallery.size()) {
			return;
		}
		AbstractVehicleEntity v = gallery.get(i);
		double tx = v.getX(), ty = v.getY() + 1.5, tz = v.getZ();
		double px = tx + 16, py = ty + 7, pz = tz + 20;
		double dx = tx - px, dy = ty - py, dz = tz - pz;
		float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
		float pitch = (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
		player.changeGameMode(net.minecraft.world.GameMode.CREATIVE);
		player.getAbilities().flying = true;
		player.sendAbilitiesUpdate();
		player.teleport((ServerWorld) v.getEntityWorld(), px, py, pz, java.util.Set.of(), yaw, pitch, true);
		LOG.info("[tvmtest] view {} {}", i, v.getVehicleDefinitionId());
	}

	final JsonObject rides = new JsonObject();

	void ride(net.minecraft.server.network.ServerPlayerEntity player, int i, boolean begin) {
		if (player == null || i >= gallery.size()) {
			return;
		}
		AbstractVehicleEntity old = gallery.get(i);
		if (begin) {
			// A fresh vehicle: the gallery one may have been damaged by its own weapons.
			for (Entity p : new ArrayList<>(old.getPassengerList())) {
				p.discard();
			}
			Vec3d at = new Vec3d(old.getX(), old.getY(), old.getZ());
			Identifier oid = old.getVehicleDefinitionId();
			old.discard();
			gallery.set(i, spawn((ServerWorld) player.getEntityWorld(), oid, at));
		}
		AbstractVehicleEntity v = gallery.get(i);
		String key = v.getVehicleDefinitionId().toString();
		JsonObject r = rides.has(key) ? rides.getAsJsonObject(key) : new JsonObject();
		rides.add(key, r);
		guard("ride/" + key, () -> {
			if (begin) {
				for (Entity p : new ArrayList<>(v.getPassengerList())) {
					p.discard();
				}
				fillAmmo(v);
				fillFuel(v);
				r.add("start", state(v));
				player.teleport((ServerWorld) v.getEntityWorld(), v.getX() + 1.5, v.getY(), v.getZ() + 1.5, java.util.Set.of(), 0f, 0f, true);
				// The real boarding path: right-click with an empty hand.
				r.addProperty("interact", String.valueOf(v.interact(player, net.minecraft.util.Hand.MAIN_HAND)));
				r.addProperty("mounted", player.getVehicle() == v);
			} else {
				r.add("end", state(v));
				r.addProperty("stillRiding", player.getVehicle() == v);
				player.stopRiding();
				try {
					Files.writeString(server(player).getRunDirectory().resolve("tvmtest-ride.json"),
							new GsonBuilder().setPrettyPrinting().create().toJson(rides), StandardCharsets.UTF_8);
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			}
		});
		LOG.info("[tvmtest] ride {} {} {}", begin ? "begin" : "end", i, key);
	}

	static MinecraftServer server(net.minecraft.server.network.ServerPlayerEntity player) {
		return ((ServerWorld) player.getEntityWorld()).getServer();
	}

	@SuppressWarnings("unchecked")
	static void fillFuel(AbstractVehicleEntity v) {
		try {
			java.lang.reflect.Field f = AbstractVehicleEntity.class.getDeclaredField("FUEL");
			f.setAccessible(true);
			v.getDataTracker().set((net.minecraft.entity.data.TrackedData<Float>) f.get(null), v.getMaxFuel());
		} catch (ReflectiveOperationException e) {
			throw new RuntimeException(e);
		}
	}

	void fireAll(int i) {
		if (i >= gallery.size()) {
			return;
		}
		AbstractVehicleEntity v = gallery.get(i);
		for (int w = 0; w < v.getDefinition().weapons().size(); w++) {
			final int wi = w;
			guard("fire/" + v.getVehicleDefinitionId() + "/" + w, () -> v.tryFireWeapon(wi, null));
		}
	}

	// ------------------------------------------------------------------ helpers
	AbstractVehicleEntity spawn(ServerWorld world, Identifier id, Vec3d pos) {
		VehicleDefinition def = VehicleRegistry.get(id).orElseThrow();
		EntityType<?> type = Registries.ENTITY_TYPE.get(def.entityType());
		Entity e = type.create(world, spawned -> {
			if (spawned instanceof AbstractVehicleEntity av) {
				av.setVehicleDefinitionId(id);
			}
		}, BlockPos.ofFloored(pos), SpawnReason.COMMAND, false, false);
		e.refreshPositionAndAngles(pos.x, pos.y, pos.z, 0f, 0f);
		world.spawnEntity(e);
		return (AbstractVehicleEntity) e;
	}

	LivingEntity husk(ServerWorld world, Vec3d pos) {
		MobEntity m = EntityType.HUSK.create(world, SpawnReason.COMMAND);
		m.setAiDisabled(true);
		m.setPersistent();
		m.getAttributeInstance(EntityAttributes.MAX_HEALTH).setBaseValue(1024);
		m.setHealth(1024);
		m.refreshPositionAndAngles(pos.x, pos.y, pos.z, 180f, 0f);
		world.spawnEntity(m);
		return m;
	}

	LivingEntity zombie(ServerWorld world, Vec3d pos) {
		MobEntity m = EntityType.ZOMBIE.create(world, SpawnReason.COMMAND);
		m.setAiDisabled(true);
		m.setPersistent();
		m.refreshPositionAndAngles(pos.x, pos.y, pos.z, 180f, 0f);
		world.spawnEntity(m);
		return m;
	}

	/** Through the mod's own ChunkForceTracker, not ServerWorld.setChunkForced(): a chunk forced directly is un-forced as soon as the last projectile that also asked for it lets go, and with no player around the vehicle under test then stops ticking. */
	void force(ServerWorld world, int cx, int cz, boolean on) {
		net.minecraft.util.math.ChunkPos pos = new net.minecraft.util.math.ChunkPos(cx, cz);
		if (on) {
			com.example.tudursvehiclemod.ChunkForceTracker.request(world, pos, this);
		} else {
			com.example.tudursvehiclemod.ChunkForceTracker.release(world, pos, this);
		}
	}

	void forceChunks(ServerWorld world, int siteX, boolean on) {
		for (int cx = (siteX >> 4) - 5; cx <= (siteX >> 4) + 5; cx++) {
			for (int cz = -5; cz <= 12; cz++) {
				force(world, cx, cz, on);
			}
		}
	}

	void buildPool(ServerWorld world, int siteX) {
		BlockState water = Blocks.WATER.getDefaultState();
		BlockState stone = Blocks.STONE.getDefaultState();
		for (int x = siteX - 30; x <= siteX + 30; x++) {
			for (int z = -30; z <= 150; z++) {
				boolean wall = x == siteX - 30 || x == siteX + 30 || z == -30 || z == 150;
				for (int y = -63; y <= WATER_TOP - 1; y++) {
					world.setBlockState(new BlockPos(x, y, z), wall ? stone : water, Block.NOTIFY_LISTENERS | Block.FORCE_STATE);
				}
			}
		}
	}

	/** Removes everything but players around the site (projectiles, spawned aircraft, pilots, targets). */
	void cleanup(ServerWorld world, int siteX) {
		List<Entity> remove = new ArrayList<>();
		for (Entity e : world.iterateEntities()) {
			if (e instanceof net.minecraft.entity.player.PlayerEntity) {
				continue;
			}
			String ns = Registries.ENTITY_TYPE.getId(e.getType()).getNamespace();
			boolean near = Math.abs(e.getX() - siteX) < 600;
			if (near || ns.equals("tudursvehiclemod")) {
				remove.add(e);
			}
		}
		for (Entity e : remove) {
			e.discard();
		}
	}

	static double round(double d) {
		return Math.round(d * 1000.0) / 1000.0;
	}

	JsonObject state(AbstractVehicleEntity v) {
		JsonObject o = new JsonObject();
		if (v == null) {
			o.addProperty("missing", true);
			return o;
		}
		o.addProperty("removed", v.isRemoved());
		o.addProperty("x", round(v.getX()));
		o.addProperty("y", round(v.getY()));
		o.addProperty("z", round(v.getZ()));
		o.addProperty("yaw", round(v.getYaw()));
		o.addProperty("pitch", round(v.getPitch()));
		Vec3d vel = v.getVelocity();
		o.addProperty("vx", round(vel.x));
		o.addProperty("vy", round(vel.y));
		o.addProperty("vz", round(vel.z));
		o.addProperty("onGround", v.isOnGround());
		o.addProperty("health", round(v.getHealth()));
		o.addProperty("maxHealth", round(v.getMaxHealth()));
		o.addProperty("fuel", round(v.getFuel()));
		o.addProperty("throttle", round(v.getThrottle()));
		o.addProperty("destroyed", v.tudursvehiclemod$isDestroyed());
		o.addProperty("passengers", v.getPassengerList().size());
		return o;
	}
}
