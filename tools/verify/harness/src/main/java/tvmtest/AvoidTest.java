package tvmtest;

import com.example.tudursvehiclemod.VehicleModServerConfig;
import com.example.tudursvehiclemod.block.DroneWaypoint;
import com.example.tudursvehiclemod.entity.AbstractVehicleEntity;
import com.example.tudursvehiclemod.entity.AircraftEntity;
import com.example.tudursvehiclemod.entity.VehicleContacts;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

/** /tvmtest avoid <on|off> <obstacle id> <aircraft id> [height]: a CAS-route aircraft flown straight through an obstacle vehicle parked on the ground at (-2600, 0), with autopilotCollisionAvoidance on or off. Logs whether the two touched (VehicleContacts), how close the aircraft's centre came to the obstacle's, the height it passed at, and its health. Uses /tvmtest speedtest setup's world; forces its own chunks. */
final class AvoidTest {

	static AbstractVehicleEntity obstacle;
	static AircraftEntity aircraft;
	static int ticks = -1;
	static int contactTicks;
	static double minDistance;
	static double passY;
	static float startHealth;
	static String label;

	static void start(TvmTest t, ServerWorld world, boolean on, String obstacleId, String aircraftId, int height) {
		for (int cx = -2600 - 160 >> 4; cx <= -2600 + 160 >> 4; cx++) {
			for (int cz = -320 >> 4; cz <= 320 >> 4; cz++) {
				world.setChunkForced(cx, cz, true);
			}
		}
		if (obstacle != null) obstacle.discard();
		if (aircraft != null) aircraft.discard();
		VehicleModServerConfig.get().autopilotCollisionAvoidance = on;
		label = (on ? "on " : "off ") + obstacleId + " " + aircraftId + " h" + height;
		obstacle = t.spawn(world, Identifier.of(obstacleId), new Vec3d(-2600.5, TvmTest.GROUND_Y, 0.5));
		BlockPos center = BlockPos.ofFloored(-2600.5, TvmTest.GROUND_Y, 0.5);
		List<DroneWaypoint> route = new ArrayList<>();
		route.add(new DroneWaypoint(0, height, -160, 0.6f, 0f, 1f, 1f));
		route.add(new DroneWaypoint(0, height, 160, 0.6f, 0f, 1f, 1f));
		route.add(new DroneWaypoint(0, height, 300, 0.6f, 0f, 1f, 1f));
		aircraft = (AircraftEntity) t.spawn(world, Identifier.of(aircraftId), new Vec3d(-2600.5, TvmTest.GROUND_Y + height, -160.5));
		aircraft.tudursvehiclemod$setCasWaypointRoute(route, List.of(false, false, false), 0);
		aircraft.tudursvehiclemod$setCasTimeoutTicks(800);
		aircraft.tudursvehiclemod$setCasStuckTimeoutTicks(800);
		aircraft.tudursvehiclemod$setDroneLink(center);
		startHealth = -1;
		contactTicks = 0;
		minDistance = Double.MAX_VALUE;
		passY = Double.NaN;
		ticks = 0;
	}

	static void tick() {
		if (ticks < 0 || aircraft == null) {
			return;
		}
		ticks++;
		if (ticks == 61) {
			startHealth = aircraft.getHealth();
		}
		if (!aircraft.isRemoved()) {
			for (VehicleContacts.Contact c : VehicleContacts.contactsFor(aircraft)) {
				if (c.other() == obstacle) {
					contactTicks++;
				}
			}
			double d = Math.sqrt(aircraft.squaredDistanceTo(obstacle));
			if (d < minDistance) {
				minDistance = d;
			}
			if (Double.isNaN(passY) && aircraft.getZ() > obstacle.getZ()) {
				passY = aircraft.getY() - obstacle.getY();
			}
		}
		if (ticks >= 400 || aircraft.isRemoved()) {
			TvmTest.LOG.info("[tvmtest] AVOID {} contactTicks={} minCentreDistance={} passHeight={} health={}->{} removed={} destroyed={}", label, contactTicks,
					Math.round(minDistance * 10) / 10.0, Math.round(passY * 10) / 10.0, startHealth, aircraft.getHealth(), aircraft.isRemoved(), aircraft.tudursvehiclemod$isDestroyed());
			VehicleModServerConfig.get().autopilotCollisionAvoidance = true;
			ticks = -1;
		}
	}
}
