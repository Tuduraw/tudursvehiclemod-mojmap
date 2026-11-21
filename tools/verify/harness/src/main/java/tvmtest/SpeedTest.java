package tvmtest;

import com.example.tudursvehiclemod.asset.VehicleDefinition;
import com.example.tudursvehiclemod.asset.VehicleRegistry;
import com.example.tudursvehiclemod.entity.AbstractVehicleEntity;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.mojang.authlib.GameProfile;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import net.fabricmc.fabric.api.entity.FakePlayer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

/** /tvmtest speedtest setup | <name>: every vehicle's top speed as it works it out (getEffectiveMaxSpeed(), in km/h), and a car and a ship driven at full throttle by a fake player for 200 ticks (peak horizontal speed) - to compare runs at different vehicle speed multipliers. */
final class SpeedTest {

	static final int CAR_X = -3000, SHIP_X = -3200;
	static final Identifier CAR = Identifier.of("tudur_sample", "sample_halftrack");
	static final Identifier SHIP = Identifier.of("tudur_sample", "sample_ship");

	static AbstractVehicleEntity car, ship;
	static FakePlayer carDriver, shipDriver;
	static int ticks = -1;
	static double carPeak, shipPeak;
	static String name;
	static JsonObject report;

	static void setup(TvmTest t, ServerWorld world) {
		t.forceChunks(world, CAR_X, true);
		t.forceChunks(world, SHIP_X, true);
		t.buildPool(world, SHIP_X);
		TvmTest.LOG.info("[tvmtest] SPEEDSETUP");
	}

	static void run(TvmTest t, ServerWorld world, String runName) {
		name = runName;
		report = new JsonObject();
		JsonObject caps = new JsonObject();
		for (Identifier id : VehicleRegistry.getAll().keySet()) {
			AbstractVehicleEntity v = t.spawn(world, id, new Vec3d(CAR_X + 0.5, TvmTest.GROUND_Y + 200, -40.5));
			caps.addProperty(id.toString(), Math.round(v.tudursvehiclemod$getEffectiveMaxSpeed() * 72.0 * 10) / 10.0);
			v.discard();
		}
		report.add("topSpeedKmh", caps);
		car = t.spawn(world, CAR, new Vec3d(CAR_X + 0.5, TvmTest.GROUND_Y, 0.5));
		ship = t.spawn(world, SHIP, new Vec3d(SHIP_X + 0.5, TvmTest.WATER_TOP, 0.5));
		TvmTest.fillFuel(car);
		TvmTest.fillFuel(ship);
		carDriver = FakePlayer.get(world, new GameProfile(UUID.nameUUIDFromBytes("speedcar".getBytes()), "speedcar"));
		shipDriver = FakePlayer.get(world, new GameProfile(UUID.nameUUIDFromBytes("speedship".getBytes()), "speedship"));
		carDriver.refreshPositionAndAngles(car.getX(), car.getY(), car.getZ(), 0f, 0f);
		shipDriver.refreshPositionAndAngles(ship.getX(), ship.getY(), ship.getZ(), 0f, 0f);
		report.addProperty("carMounted", carDriver.startRiding(car, true, false));
		report.addProperty("shipMounted", shipDriver.startRiding(ship, true, false));
		carPeak = 0;
		shipPeak = 0;
		ticks = 0;
	}

	/** /tvmtest speeddrive <player>: puts that (real) player in the driver's seat of a fresh halftrack on flat ground, for the client probe (-Dtvmtest.speedsync) to drive. */
	static void board(TvmTest t, ServerWorld world, String playerName) {
		net.minecraft.server.network.ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(playerName);
		if (player == null) {
			TvmTest.LOG.info("[tvmtest] SPEEDDRIVE no player {}", playerName);
			return;
		}
		if (player.getVehicle() != null) {
			net.minecraft.entity.Entity old = player.getVehicle();
			player.stopRiding();
			old.discard();
		}
		player.teleport(world, CAR_X + 0.5, TvmTest.GROUND_Y, 0.5, java.util.Set.of(), 0f, 0f, true);
		AbstractVehicleEntity v = t.spawn(world, CAR, new Vec3d(CAR_X + 0.5, TvmTest.GROUND_Y, 0.5));
		TvmTest.fillFuel(v);
		TvmTest.LOG.info("[tvmtest] SPEEDDRIVE mounted={}", player.startRiding(v, true, true));
	}

	static void tick() {
		if (ticks < 0) {
			return;
		}
		ticks++;
		car.setSyncedThrottleInput(1f);
		ship.setSyncedThrottleInput(1f);
		carPeak = Math.max(carPeak, car.getVelocity().horizontalLength());
		if (ship.getZ() < 130) {
			shipPeak = Math.max(shipPeak, ship.getVelocity().horizontalLength());
		}
		if (ticks < 200) {
			return;
		}
		ticks = -1;
		report.addProperty("carPeakKmh", Math.round(carPeak * 72.0 * 10) / 10.0);
		report.addProperty("shipPeakKmh", Math.round(shipPeak * 72.0 * 10) / 10.0);
		report.addProperty("carDistance", Math.round(car.getZ() * 10) / 10.0);
		report.addProperty("shipDistance", Math.round(ship.getZ() * 10) / 10.0);
		carDriver.stopRiding();
		shipDriver.stopRiding();
		car.discard();
		ship.discard();
		try {
			Path out = Path.of("tvmtest-" + name + ".json");
			Files.writeString(out, new GsonBuilder().setPrettyPrinting().create().toJson(report));
			TvmTest.LOG.info("[tvmtest] DONE {} -> {}", name, out.toAbsolutePath());
		} catch (Exception e) {
			TvmTest.LOG.error("[tvmtest] write failed", e);
		}
	}
}
