package tvmtest;

import com.example.tudursvehiclemod.entity.AbstractVehicleEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

/** /tvmtest destroyride <player> <vehicle id> [height]: seats the (real) player in a fresh vehicle, destroys it 40 ticks later, and logs every 20 ticks for 200 more whether the player is still aboard. */
final class DestroyRideTest {

	static ServerPlayerEntity player;
	static AbstractVehicleEntity vehicle;
	static int ticks = -1;

	static void start(TvmTest t, ServerWorld world, String playerName, String id, int height) {
		player = world.getServer().getPlayerManager().getPlayer(playerName);
		if (player == null) {
			TvmTest.LOG.info("[tvmtest] DESTROYRIDE no player");
			return;
		}
		if (player.getVehicle() != null) {
			net.minecraft.entity.Entity old = player.getVehicle();
			player.stopRiding();
			old.discard();
		}
		double y = TvmTest.GROUND_Y + height;
		player.teleport(world, SpeedTest.CAR_X + 40.5, y, 0.5, java.util.Set.of(), 0f, 0f, true);
		vehicle = t.spawn(world, Identifier.of(id), new Vec3d(SpeedTest.CAR_X + 40.5, y, 0.5));
		TvmTest.fillFuel(vehicle);
		TvmTest.LOG.info("[tvmtest] DESTROYRIDE {} mounted={}", id, player.startRiding(vehicle, true, true));
		ticks = 0;
	}

	static void tick() {
		if (ticks < 0) {
			return;
		}
		ticks++;
		if (ticks == 40) {
			vehicle.tudursvehiclemod$forceDestroy((ServerWorld) vehicle.getEntityWorld());
			TvmTest.LOG.info("[tvmtest] DESTROYRIDE destroyed, aboard={}", player.getVehicle() == vehicle);
		}
		if (ticks > 40 && ticks % 20 == 0) {
			TvmTest.LOG.info("[tvmtest] DESTROYRIDE t+{} aboard={} vehicle={} removed={} y={}", ticks - 40, player.getVehicle() == vehicle,
					player.getVehicle() == null ? "none" : player.getVehicle().getType().toString(), vehicle.isRemoved(), Math.round(vehicle.getY()));
		}
		if (ticks >= 240) {
			ticks = -1;
			TvmTest.LOG.info("[tvmtest] DESTROYRIDE END");
		}
	}
}
