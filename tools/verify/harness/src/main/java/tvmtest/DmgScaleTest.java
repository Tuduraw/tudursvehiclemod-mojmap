package tvmtest;

import com.example.tudursvehiclemod.VehicleModServerConfig;
import com.example.tudursvehiclemod.entity.DamageScaling;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.nio.file.Files;
import java.nio.file.Path;
import net.minecraft.block.Blocks;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.HuskEntity;
import net.minecraft.entity.projectile.ArrowEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

/** /tvmtest dmgscale setup | <name>: the server's damage/explosion multipliers (entity.DamageScaling) - a hit and an explosion at each setting, in a fresh spot each, with husks as targets and a stone block to blow up. */
final class DmgScaleTest {

	static final int BASE_X = 3000, BASE_Z = 3000, Y = -55;

	static void setup(ServerWorld world) {
		for (int cx = -2; cx <= 12; cx++) {
			for (int cz = -2; cz <= 2; cz++) {
				world.setChunkForced((BASE_X >> 4) + cx, (BASE_Z >> 4) + cz, true);
			}
		}
		TvmTest.LOG.info("[tvmtest] DMGSETUP");
	}

	static void run(ServerWorld world, String name) {
		double[][] settings = {{1, 1, 1}, {0, 0, 0}, {0.5, 0.5, 0.5}, {2, 2, 2}, {1, 0, 1}, {1, 1, 0}};
		VehicleModServerConfig config = VehicleModServerConfig.get();
		JsonArray results = new JsonArray();
		for (int i = 0; i < settings.length; i++) {
			config.weaponDamageMultiplier = settings[i][0];
			config.explosionBlastMultiplier = settings[i][1];
			config.explosionBlockMultiplier = settings[i][2];
			int x0 = BASE_X + i * 30;
			JsonObject r = new JsonObject();
			r.addProperty("damage", settings[i][0]);
			r.addProperty("blast", settings[i][1]);
			r.addProperty("block", settings[i][2]);
			// The hit: 5 damage from an arrow flying +Z.
			HuskEntity hitTarget = husk(world, x0 + 0.5, Y, BASE_Z - 10 + 0.5);
			ArrowEntity arrow = EntityType.ARROW.create(world, SpawnReason.COMMAND);
			arrow.setPosition(x0 + 0.5, Y + 1, BASE_Z - 11);
			arrow.setVelocity(0, 0, 2.0);
			boolean hurt = DamageScaling.hitEntity(world, hitTarget, world.getDamageSources().thrown(arrow, null), 5f, arrow);
			r.addProperty("hitReturned", hurt);
			r.addProperty("hitHealth", hitTarget.getHealth());
			r.addProperty("hitVelZ", Math.round(hitTarget.getVelocity().z * 1000) / 1000.0);
			r.addProperty("hitVelY", Math.round(hitTarget.getVelocity().y * 1000) / 1000.0);
			// The explosion: power 3 on top of a 9x5x9 stone block, a husk 3 blocks off.
			int stone = 0;
			for (int dx = -4; dx <= 4; dx++) for (int dy = -5; dy <= -1; dy++) for (int dz = -4; dz <= 4; dz++) {
				world.setBlockState(new BlockPos(x0 + dx, Y + dy, BASE_Z + dz), Blocks.STONE.getDefaultState());
				stone++;
			}
			HuskEntity blastTarget = husk(world, x0 + 3.5, Y, BASE_Z + 0.5);
			ArrowEntity source = EntityType.ARROW.create(world, SpawnReason.COMMAND);
			source.setPosition(x0 + 0.5, Y, BASE_Z + 0.5);
			DamageScaling.explode(world, source, x0 + 0.5, Y, BASE_Z + 0.5, 3f, true, World.ExplosionSourceType.MOB);
			int broken = 0;
			for (int dx = -4; dx <= 4; dx++) for (int dy = -5; dy <= -1; dy++) for (int dz = -4; dz <= 4; dz++) {
				if (!world.getBlockState(new BlockPos(x0 + dx, Y + dy, BASE_Z + dz)).isOf(Blocks.STONE)) broken++;
			}
			int fire = 0;
			for (int dx = -6; dx <= 6; dx++) for (int dy = -6; dy <= 3; dy++) for (int dz = -6; dz <= 6; dz++) {
				if (world.getBlockState(new BlockPos(x0 + dx, Y + dy, BASE_Z + dz)).isOf(Blocks.FIRE)) fire++;
			}
			r.addProperty("blocksBroken", broken);
			r.addProperty("fireBlocks", fire);
			r.addProperty("blastHealth", blastTarget.getHealth());
			r.addProperty("blastVelX", Math.round(blastTarget.getVelocity().x * 1000) / 1000.0);
			results.add(r);
			hitTarget.discard();
			blastTarget.discard();
		}
		config.weaponDamageMultiplier = 1.0;
		config.explosionBlastMultiplier = 1.0;
		config.explosionBlockMultiplier = 1.0;
		try {
			Path out = Path.of("tvmtest-" + name + ".json");
			Files.writeString(out, new GsonBuilder().setPrettyPrinting().create().toJson(results));
			TvmTest.LOG.info("[tvmtest] DONE {} -> {}", name, out.toAbsolutePath());
		} catch (Exception e) {
			TvmTest.LOG.error("[tvmtest] write failed", e);
		}
	}

	private static HuskEntity husk(ServerWorld world, double x, double y, double z) {
		HuskEntity husk = EntityType.HUSK.create(world, SpawnReason.COMMAND);
		husk.refreshPositionAndAngles(x, y, z, 0f, 0f);
		husk.setAiDisabled(true);
		world.spawnEntity(husk);
		return husk;
	}
}
