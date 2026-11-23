package tvmtest;

import com.example.tudursvehiclemod.entity.AbstractVehicleEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

/** /tvmtest seat <player> "<vehicle>" <seat> <pitch> <roll> <spin>: a new vehicle 30 blocks up at (-3000, 0), held in the air at that pitch and roll and turning spin degrees a tick (seat(), the same on the client - TvmTestClient's -Dtvmtest.seatcheck), with the player in the given seat. The client checks the player's position and eye against the seat and camera position as the model is drawn. The previous one is removed. */
final class SeatTest {

	private SeatTest() {
	}

	static AbstractVehicleEntity current;

	static void start(TvmTest t, ServerWorld world, String playerName, String vehicleId, int seat, float pitch, float roll, float spin) {
		if (current != null) {
			current.discard();
		}
		world.setChunkForced(-3000 >> 4, 0, true);
		ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(playerName);
		if (player == null) {
			return;
		}
		if (player.getVehicle() != null) {
			player.stopRiding();
		}
		Vec3d pos = new Vec3d(-3000.5, TvmTest.GROUND_Y + 30, 0.5);
		player.teleport(world, pos.x, pos.y, pos.z, java.util.Set.of(), 0f, 0f, true);
		AbstractVehicleEntity v = t.spawn(world, Identifier.of(vehicleId), pos);
		v.setNoGravity(true);
		v.tudursvehiclemod$setExternalMotionController(controller(pos, pitch, roll, spin));
		boolean mounted = v.tudursvehiclemod$mountToSeat(player, seat);
		current = v;
		TvmTest.LOG.info("[tvmtest] SEAT start {} seat={} pitch={} roll={} spin={} mounted={} assigned={}", vehicleId, seat, pitch, roll, spin, mounted, v.tudursvehiclemod$getAssignedSeatIndex(player));
	}

	/** Holds the vehicle at pos, at that pitch and roll, its heading turning spin degrees a tick. */
	static AbstractVehicleEntity.ExternalMotionController controller(Vec3d pos, float pitch, float roll, float spin) {
		final int[] age = {0};
		return vehicle -> {
			float yaw = age[0]++ * spin;
			vehicle.tudursvehiclemod$applyExternalPose(pos, new org.joml.Quaternionf()
					.rotationY((float) Math.toRadians(-yaw))
					.rotateX((float) Math.toRadians(pitch))
					.rotateZ((float) Math.toRadians(roll)));
			return true;
		};
	}
}
