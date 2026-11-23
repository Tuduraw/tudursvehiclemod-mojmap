package tvmtest;

import com.example.tudursvehiclemod.entity.AbstractVehicleEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

/** /tvmtest zone "<vehicle>": a vehicle's damage zones (asset.DamageZone). For each case a fresh vehicle at (3000, 2900), hit once at a point given in the model's own coordinates (or with no point), logging its health lost as "ZONE <case> lost=". Meant for tudur_sample:test_zone_halftrack - armor_damage_factor 0.7, armor_min_damage 2, a zone z 0..4 with damage_factor 3, a zone z -4..0 with damage_factor 0.1 and min_damage 5 (expected: front20=60, rear20=0, rear100=10, outside20=14, nopoint20=14, small2=0, front2=6, blastFront20=60, yawed front20=60). */
final class ZoneTest {

	private ZoneTest() {
	}

	static void run(TvmTest t, ServerWorld world, String vehicleId) {
		for (int cx = -1; cx <= 1; cx++) {
			for (int cz = -1; cz <= 1; cz++) {
				world.setChunkForced((3000 >> 4) + cx, (2900 >> 4) + cz, true);
			}
		}
		Identifier id = Identifier.of(vehicleId);
		hit(t, world, id, "front20", new Vec3d(0, 1, 1), 20f, false, 0f);
		hit(t, world, id, "rear20", new Vec3d(0, 1, -1), 20f, false, 0f);
		hit(t, world, id, "rear100", new Vec3d(0, 1, -1), 100f, false, 0f);
		hit(t, world, id, "outside20", new Vec3d(0, 1, 10), 20f, false, 0f);
		hit(t, world, id, "nopoint20", null, 20f, false, 0f);
		hit(t, world, id, "small2", new Vec3d(0, 1, 10), 2f, false, 0f);
		hit(t, world, id, "front2", new Vec3d(0, 1, 1), 2f, false, 0f);
		hit(t, world, id, "blastFront20", new Vec3d(0, 1, 1), 20f, true, 0f);
		hit(t, world, id, "yawedFront20", new Vec3d(0, 1, 1), 20f, false, 90f);
	}

	/** /tvmtest zonepart "<vehicle>": a damage zone on a moving part (its "part"). For tudur_sample:test_zone_part_halftrack - a 0.6-block box at (0, 2, 4) on "$canopy" (pivot (0, 1.5, 3.25), opens 80 degrees about x), damage_factor 1.5, the vehicle's own armor 0.7 / min 2: closed, a hit at the box loses 30; opened, a hit where the box was loses 14 (outside), and a hit where the box has moved to with the canopy loses 30. */
	static void partRun(TvmTest t, ServerWorld world, String vehicleId) {
		for (int cx = -1; cx <= 1; cx++) {
			for (int cz = -1; cz <= 1; cz++) {
				world.setChunkForced((3000 >> 4) + cx, (2900 >> 4) + cz, true);
			}
		}
		Identifier id = Identifier.of(vehicleId);
		Vec3d rest = new Vec3d(0, 2.0, 4.0);
		AbstractVehicleEntity closed = t.spawn(world, id, new Vec3d(3000.5, TvmTest.GROUND_Y, 2900.5));
		log(world, closed, "closedAtBox", rest);
		closed.discard();
		AbstractVehicleEntity open = t.spawn(world, id, new Vec3d(3000.5, TvmTest.GROUND_Y, 2900.5));
		open.setYaw(45f);
		open.tudursvehiclemod$tryToggleHatch();
		for (int i = 0; i < 60; i++) {
			open.tick();
		}
		TvmTest.LOG.info("[tvmtest] ZONE canopyProgress={}", open.getTogglePartProgress("$canopy"));
		log(world, open, "openWhereBoxWas", rest);
		org.joml.Vector3f moved = open.tudursvehiclemod$getPartTransform("$canopy").transformPosition(new org.joml.Vector3f((float) rest.x, (float) rest.y, (float) rest.z));
		log(world, open, "openWhereBoxIs", new Vec3d(moved.x, moved.y, moved.z));
		open.discard();
	}

	private static void log(ServerWorld world, AbstractVehicleEntity v, String name, Vec3d local) {
		org.joml.Vector3f w = new org.joml.Vector3f((float) local.x, (float) local.y, (float) local.z).mul(v.getDefinition().scale());
		v.tudursvehiclemod$getCurrentRotationForRendering().transform(w);
		float before = v.getHealth();
		v.tudursvehiclemod$hurtAt(world, world.getDamageSources().generic(), 20f, new Vec3d(v.getX() + w.x, v.getY() + w.y, v.getZ() + w.z));
		TvmTest.LOG.info("[tvmtest] ZONE {} lost={}", name, Math.round((before - v.getHealth()) * 100) / 100.0);
		v.tudursvehiclemod$setHealth(v.getMaxHealth());
	}

	/** /tvmtest zoneshow "<vehicle>": one to look at, 10 blocks east of where /tvmtest farveh puts the player (client -Dtvmtest.hitmesh=1 draws its zones). */
	static void show(TvmTest t, ServerWorld world, String vehicleId) {
		AbstractVehicleEntity v = t.spawn(world, Identifier.of(vehicleId), new Vec3d(-3010.5, TvmTest.GROUND_Y, 0.5));
		v.setYaw(30f);
		TvmTest.LOG.info("[tvmtest] ZONESHOW {}", v.getId());
	}

	private static void hit(TvmTest t, ServerWorld world, Identifier id, String name, Vec3d local, float damage, boolean blast, float yaw) {
		AbstractVehicleEntity v = t.spawn(world, id, new Vec3d(3000.5, TvmTest.GROUND_Y, 2900.5));
		if (yaw != 0f) {
			v.setYaw(yaw);
		}
		float before = v.getHealth();
		Vec3d point = null;
		if (local != null) {
			// Model coordinates to world: rotated with the body, scaled (the reverse of AbstractVehicleEntity.tudursvehiclemod$damageZoneAt()).
			org.joml.Vector3f w = new org.joml.Vector3f((float) local.x, (float) local.y, (float) local.z).mul(v.getDefinition().scale());
			v.tudursvehiclemod$getCurrentRotationForRendering().transform(w);
			point = new Vec3d(v.getX() + w.x, v.getY() + w.y, v.getZ() + w.z);
		}
		DamageSource source;
		if (blast) {
			source = world.getDamageSources().explosion(null, null);
			// An explosion's centre is its source position: one made at the point (DamageSource with a position).
			source = new DamageSource(source.getTypeRegistryEntry(), point);
			v.damage(world, source, damage);
		} else if (point != null) {
			v.tudursvehiclemod$hurtAt(world, world.getDamageSources().generic(), damage, point);
		} else {
			v.damage(world, world.getDamageSources().generic(), damage);
		}
		TvmTest.LOG.info("[tvmtest] ZONE {} lost={}", name, Math.round((before - v.getHealth()) * 100) / 100.0);
		v.discard();
	}
}
