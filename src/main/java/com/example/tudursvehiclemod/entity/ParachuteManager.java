package com.example.tudursvehiclemod.entity;

/** Unified across every trigger (ejection seat, mob drop, and equipped-parachute jump alike) - ("パラシュートをエンティティとして登録し、搭乗させるように機能を変更しようと思います。射出座席や装備時のパラシュートについても同様の動作に統一してください"): the deploy/track/retract lifecycle this class used to own now lives entirely in ParachuteEntity's own physics instead (spawned via that class's own tudursvehiclemod$spawnAndMount()) - a real entity's own model/hitbox/health naturally replaces every part of what a status effect + synced flag used to have to fake. Only the ground-clearance/deployable check below survives, still used by network.ModNetworking's own equipped-parachute Space-to-deploy handler to re-validate server-side before spawning one. */
public final class ParachuteManager {

	private ParachuteManager() {
	}

	/** How far below the entity tudursvehiclemod$hasGroundClearance() casts its own downward ray before concluding there's nothing in the way. */
	private static final double MIN_GROUND_CLEARANCE_BLOCKS = 5.0;

	/** Reuses the exact same ground-altitude technique already proven correct for AircraftEntity's own tudursvehiclemod$altitudeAboveGroundOrWater() (used extensively throughout this project's own Carrier/CAS safe-altitude logic): casts a ray straight down from the entity's own feet, computing the actual distance to the first solid block or water surface found (FluidHandling.SOURCE_ONLY - matches that proven method's own choice exactly, so a water surface counts as "ground" for this purpose too, not just solid blocks), or a large sentinel (256.0, well above MIN_GROUND_CLEARANCE_BLOCKS) if nothing is found within that method's own 256-block search range - true whenever that distance is at least MIN_GROUND_CLEARANCE_BLOCKS. */
	private static boolean tudursvehiclemod$hasGroundClearance(net.minecraft.world.entity.LivingEntity entity) {
		net.minecraft.world.phys.Vec3 position = new net.minecraft.world.phys.Vec3(entity.getX(), entity.getY(), entity.getZ());
		net.minecraft.world.phys.Vec3 end = position.add(0.0, -256.0, 0.0);
		net.minecraft.world.phys.HitResult hit = entity.level().clip(new net.minecraft.world.level.ClipContext(
				position, end, net.minecraft.world.level.ClipContext.Block.COLLIDER, net.minecraft.world.level.ClipContext.Fluid.SOURCE_ONLY, entity));
		double altitudeAboveGround = 256.0;
		if (hit instanceof net.minecraft.world.phys.BlockHitResult blockHit && hit.getType() == net.minecraft.world.phys.HitResult.Type.BLOCK) {
			altitudeAboveGround = Math.max(0.0, position.y - blockHit.getLocation().y);
		}
		return altitudeAboveGround >= MIN_GROUND_CLEARANCE_BLOCKS;
	}

	/** True only while airborne (not on ground, not already gliding via an actual elytra), actually descending (negative Y velocity) rather than merely airborne (e.g. still rising from a jump), and at least MIN_GROUND_CLEARANCE_BLOCKS above solid ground. Does NOT check whether a parachute is already deployed or equipped - the caller's own responsibility. */
	public static boolean tudursvehiclemod$isDeployable(net.minecraft.world.entity.LivingEntity entity) {
		return !entity.onGround() && !entity.isFallFlying() && !entity.isInWater()
				&& entity.getDeltaMovement().y < 0.0 && tudursvehiclemod$hasGroundClearance(entity);
	}
}
