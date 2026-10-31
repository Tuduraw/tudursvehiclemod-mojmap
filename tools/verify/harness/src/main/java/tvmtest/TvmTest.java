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
		ServerTickEvents.END_SERVER_TICK.register(this::tick);
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
						.then(CommandManager.literal("probe").executes(ctx -> { probe(ctx.getSource().getWorld()); return 1; }))
						.then(CommandManager.literal("gallery").executes(ctx -> { gallery(ctx.getSource().getWorld()); return 1; }))
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
					world.setChunkForced(cx, cz, true);
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

	void forceChunks(ServerWorld world, int siteX, boolean on) {
		for (int cx = (siteX >> 4) - 5; cx <= (siteX >> 4) + 5; cx++) {
			for (int cz = -5; cz <= 12; cz++) {
				world.setChunkForced(cx, cz, on);
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
