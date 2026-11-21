package tvmtest;

import com.example.tudursvehiclemod.entity.AbstractVehicleEntity;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

/** /tvmtest farveh <player>: puts the (real) player at (-3020, 0) facing +X, and vehicles 200 blocks off at (-2820, z -30..30): an aircraft circling 20 blocks up, a helicopter hovering, a halftrack on the ground - plus a halftrack 20 blocks in front of the player (close enough to be an ordinary entity). With /tvmtest speedtest setup's chunks loaded, and a short server view distance, the far ones are only ever sent as far vehicles (entity.FarVehicleSync). The client probe -Dtvmtest.farveh=1 logs what it draws. */
final class FarVehicleTest {

	static final List<AbstractVehicleEntity> spawned = new ArrayList<>();

	static void start(TvmTest t, ServerWorld world, String playerName) {
		for (AbstractVehicleEntity v : spawned) {
			v.discard();
		}
		spawned.clear();
		for (int cx = (-2860 >> 4); cx <= (-2780 >> 4); cx++) {
			for (int cz = -4; cz <= 3; cz++) {
				world.setChunkForced(cx, cz, true);
			}
		}
		ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(playerName);
		if (player != null) {
			if (player.getVehicle() != null) {
				player.stopRiding();
			}
			player.teleport(world, -3020.5, TvmTest.GROUND_Y + 1, 0.5, java.util.Set.of(), -90f, -5f, true);
		}
		AbstractVehicleEntity aircraft = t.spawn(world, Identifier.of("tudur_sample:sample_aircraft"), new Vec3d(-2820.5, TvmTest.GROUND_Y + 20, -20.5));
		final int[] age = {0};
		aircraft.tudursvehiclemod$setExternalMotionController(vehicle -> {
			double a = age[0]++ * 0.05;
			Vec3d pos = new Vec3d(-2820.5 + 20 * Math.cos(a), TvmTest.GROUND_Y + 20, -20.5 + 20 * Math.sin(a));
			float yaw = (float) Math.toDegrees(a);
			vehicle.tudursvehiclemod$applyExternalPose(pos, new org.joml.Quaternionf().rotationY((float) Math.toRadians(-yaw)).rotateZ((float) Math.toRadians(-30)));
			return true;
		});
		spawned.add(aircraft);
		AbstractVehicleEntity heli = t.spawn(world, Identifier.of("tudur_sample:sample_helicopter"), new Vec3d(-2820.5, TvmTest.GROUND_Y + 10, 10.5));
		heli.setNoGravity(true);
		spawned.add(heli);
		spawned.add(t.spawn(world, Identifier.of("tudur_sample:sample_halftrack"), new Vec3d(-2820.5, TvmTest.GROUND_Y, 30.5)));
		spawned.add(t.spawn(world, Identifier.of("tudur_sample:sample_halftrack"), new Vec3d(-3000.5, TvmTest.GROUND_Y, 4.5)));
		TvmTest.LOG.info("[tvmtest] FARVEH placed {} vehicles, player={}", spawned.size(), player != null);
		level = world;
		shooter = player;
		fireTicks = 0;
	}

	static ServerWorld level;
	static ServerPlayerEntity shooter;
	static int fireTicks = -1;

	/** Every 30 ticks, a missile-model projectile from 15 blocks up over (-2840, -40), flying +Z at 1.5 blocks/tick and falling under gravity - a far projectile to draw. */
	static void tick() {
		if (fireTicks < 0 || level == null) {
			return;
		}
		if (fireTicks++ % 30 != 0) {
			return;
		}
		var projectile = new com.example.tudursvehiclemod.entity.projectile.VehicleModelProjectileEntity(level, shooter,
				new net.minecraft.item.ItemStack(net.minecraft.item.Items.FIRE_CHARGE), 0f, 0.03f, 0f, false, false,
				Identifier.of("tudur_sample", "models/obj/bullet_missile.obj"), Identifier.of("tudur_sample", "textures/vehicle/bullet_missile.png"), 3.0f);
		projectile.setPosition(-2840.5, TvmTest.GROUND_Y + 15, -40.5);
		projectile.setVelocity(0, 0.2, 1.5);
		level.spawnEntity(projectile);
	}
}
