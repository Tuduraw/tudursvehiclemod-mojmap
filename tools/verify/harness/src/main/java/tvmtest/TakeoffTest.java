package tvmtest;

import com.example.tudursvehiclemod.entity.AbstractVehicleEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

/** /tvmtest takeoff <player> <ship id> <aircraft id> [sail]: a ship in the pool at (-3200, 40), and 60 ticks later the aircraft at the aft end of its first runway with the (real) player in it - the client probe (-Dtvmtest.speedsync) then holds forward for 200 ticks. Logs the aircraft every 5 ticks (speed, height above the deck, health) for 300 ticks. With sail, the ship is pushed along +Z at 0.3 blocks/tick by its own motion. */
final class TakeoffTest {

	static ServerPlayerEntity player;
	static String playerName;
	static AbstractVehicleEntity ship, aircraft;
	static String aircraftId;
	static boolean sail;
	static double offsetX;
	static ServerWorld level;
	static TvmTest harness;
	static int ticks = -1;

	static void start(TvmTest t, ServerWorld world, String playerName, String shipId, String planeId, boolean sailing) {
		offsetX = Double.parseDouble(System.getProperty("tvmtest.takeoffx", "0"));
		TakeoffTest.playerName = playerName;
		player = world.getServer().getPlayerManager().getPlayer(playerName);
		if (player != null && player.getVehicle() != null) {
			net.minecraft.entity.Entity old = player.getVehicle();
			player.stopRiding();
			old.discard();
		}
		if (ship != null) {
			ship.discard();
		}
		if (aircraft != null) {
			aircraft.discard();
		}
		ship = t.spawn(world, Identifier.of(shipId), new Vec3d(SpeedTest.SHIP_X + 0.5, TvmTest.WATER_TOP, 40.5));
		TvmTest.fillFuel(ship);
		aircraftId = planeId;
		level = world;
		harness = t;
		sail = sailing;
		ticks = 0;
	}

	static void tick() {
		if (ticks < 0) {
			return;
		}
		ticks++;
		if (ticks == 60) {
			player = level.getServer().getPlayerManager().getPlayer(playerName);
			var runway = ship.getDefinition().runways().get(0);
			double localZ = Math.min(runway.startZ(), runway.endZ()) + 4;
			double yaw = Math.toRadians(ship.getYaw());
			double fx = -Math.sin(yaw), fz = Math.cos(yaw);
			double rx = fz, rz = -fx;
			double localX = runway.centerX() + offsetX;
			Vec3d at = new Vec3d(ship.getX() + localX * rx + localZ * fx, ship.getY() + runway.heightY() + 0.05, ship.getZ() + localX * rz + localZ * fz);
			if (player != null) {
				player.teleport(level, at.x, at.y + 0.2, at.z, java.util.Set.of(), 0f, 0f, true);
			}
			aircraft = harness.spawn(level, Identifier.of(aircraftId), at);
			aircraft.setYaw(ship.getYaw());
			TvmTest.fillFuel(aircraft);
			TvmTest.LOG.info("[tvmtest] TAKEOFF mounted={} at={} deckY={}", player != null && player.startRiding(aircraft, true, true), at, ship.getY() + runway.heightY());
		}
		if (sail && ticks == 20) {
			// Under way at 0.2 blocks/tick, heaving +-0.3 blocks (40-tick period) - a sea state the ship's own physics doesn't make in a pool.
			final double startX = ship.getX(), startY = ship.getY(), startZ = ship.getZ();
			final int[] age = {0};
			ship.tudursvehiclemod$setExternalMotionController(vehicle -> {
				int a = age[0]++;
				vehicle.tudursvehiclemod$applyExternalPose(new Vec3d(startX, startY + 0.3 * Math.sin(a * Math.PI * 2 / 40.0), startZ + 0.2 * a), new org.joml.Quaternionf());
				return true;
			});
		}
		if (ticks > 60 && ticks % 5 == 0 && aircraft != null) {
			var runway = ship.getDefinition().runways().get(0);
			TvmTest.LOG.info("[tvmtest] TAKEOFF t={} aboard={} kmh={} rel={} aboveDeck={} hp={} onGround={} destroyed={} shipZ={}", ticks - 60, player != null && player.getVehicle() == aircraft,
					Math.round(aircraft.getVelocity().horizontalLength() * 72), Math.round((aircraft.getZ() - ship.getZ()) * 10) / 10.0,
					Math.round((aircraft.getY() - ship.getY() - runway.heightY()) * 100) / 100.0, aircraft.getHealth(), aircraft.isOnGround(), aircraft.tudursvehiclemod$isDestroyed(),
					Math.round(ship.getZ() * 10) / 10.0);
		}
		if (ticks >= 360) {
			ticks = -1;
			TvmTest.LOG.info("[tvmtest] TAKEOFF END");
		}
	}
}
