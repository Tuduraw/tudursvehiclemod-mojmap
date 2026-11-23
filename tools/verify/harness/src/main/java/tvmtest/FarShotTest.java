package tvmtest;

import com.example.tudursvehiclemod.entity.projectile.VehicleModelProjectileEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

/** /tvmtest farshot <player> <speed>: a missile-model projectile (no gravity) 200 blocks from the player (who stands at (-3020, 0), as /tvmtest farveh puts it), 40 blocks up, flying +X - out of every loaded chunk - at speed blocks a tick. Logs its position every 10 ticks for 300 ticks ("FARSHOT"), to show whether it keeps flying past the loaded area (its own chunk loading - VehicleProjectileEntity.tudursvehiclemod$updateProjectileForcedChunks()). */
final class FarShotTest {

	private FarShotTest() {
	}

	static VehicleModelProjectileEntity projectile;
	static ServerWorld level;
	static int ticks = -1;

	static void start(ServerWorld world, String playerName, double speed) {
		ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(playerName);
		if (player != null) {
			if (player.getVehicle() != null) {
				player.stopRiding();
			}
			player.teleport(world, -3020.5, TvmTest.GROUND_Y + 1, 0.5, java.util.Set.of(), -90f, -5f, true);
		}
		projectile = new VehicleModelProjectileEntity(world, player,
				new net.minecraft.item.ItemStack(net.minecraft.item.Items.FIRE_CHARGE), 0f, 0f, 0f, false, false,
				Identifier.of("tudur_sample", "models/obj/bullet_missile.obj"), Identifier.of("tudur_sample", "textures/vehicle/bullet_missile.png"), 3.0f);
		// A lane of its own each run (by the world's time), through chunks not generated yet - the slow case for loading.
		double laneZ = 0.5 + 48.0 * (1 + world.getTime() / 20 % 400);
		projectile.setPosition(-2820.5, TvmTest.GROUND_Y + 40, laneZ);
		projectile.setVelocity(speed, 0, 0);
		world.spawnEntity(projectile);
		projectile.tudursvehiclemod$forceLoadSpawnChunk();
		level = world;
		ticks = 0;
		TvmTest.LOG.info("[tvmtest] FARSHOT start speed={}", speed);
	}

	static void tick() {
		if (ticks < 0 || projectile == null) {
			return;
		}
		if (ticks % 10 == 0) {
			TvmTest.LOG.info("[tvmtest] FARSHOT t={} removed={} pos={},{},{} vel={} age={}", ticks, projectile.isRemoved(),
					Math.round(projectile.getX()), Math.round(projectile.getY()), Math.round(projectile.getZ()),
					Math.round(projectile.getVelocity().length() * 100) / 100.0, projectile.age);
		}
		if (++ticks > 300) {
			ticks = -1;
			projectile.discard();
		}
	}
}
