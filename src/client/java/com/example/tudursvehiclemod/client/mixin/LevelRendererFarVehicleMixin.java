package com.example.tudursvehiclemod.client.mixin;

import com.example.tudursvehiclemod.client.farvehicle.FarVehicleProxies;
import com.example.tudursvehiclemod.entity.AbstractVehicleEntity;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.LevelRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Draws vehicles past the render distance, over an LOD mod's distant terrain (client.farvehicle.FarVehicleProxies, VehicleModConfig.renderFarVehicles).
 *
 * <p>After vanilla has picked the entities to draw this frame, adds: the stand-ins for vehicles the server reported beyond this client's entity range; and real vehicles in this client's world that vanilla left out only because the terrain under them isn't drawn by the game (beyond the render distance, where an LOD mod draws it instead). Both within farVehicleRenderDistance and in view. Lit as by open sky: the game has no light data that far out. */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererFarVehicleMixin {

	@Shadow
	private ClientLevel level;

	@Shadow
	protected abstract EntityRenderState extractEntity(Entity entity, float tickProgress);

	@Shadow
	public abstract boolean isSectionCompiledAndVisible(BlockPos pos);

	@Inject(method = "extractVisibleEntities", at = @At("TAIL"))
	private void tudursvehiclemod$addFarVehicles(Camera camera, Frustum frustum, DeltaTracker deltaTracker, LevelRenderState renderState, CallbackInfo ci) {
		if (this.level == null || !FarVehicleProxies.enabled()) {
			return;
		}
		double maxDistance = FarVehicleProxies.distance();
		if (maxDistance <= 0) {
			return;
		}
		Vec3 cam = camera.position();
		double maxSq = maxDistance * maxDistance;
		float tickProgress = deltaTracker.getGameTimeDeltaPartialTick(true);
		// Real vehicles vanilla left out because no terrain is drawn under them.
		for (Entity entity : this.level.entitiesForRendering()) {
			if (!(entity instanceof AbstractVehicleEntity vehicle) || vehicle.isInvisible()) {
				continue;
			}
			BlockPos pos = vehicle.blockPosition();
			if (this.level.isOutsideBuildHeight(pos.getY()) || this.isSectionCompiledAndVisible(pos)) {
				continue;
			}
			this.tudursvehiclemod$add(vehicle, frustum, cam, maxSq, tickProgress, renderState);
		}
		for (AbstractVehicleEntity proxy : FarVehicleProxies.visible(this.level)) {
			this.tudursvehiclemod$add(proxy, frustum, cam, maxSq, tickProgress, renderState);
		}
		for (var projectile : FarVehicleProxies.visibleProjectiles(this.level)) {
			if (projectile.distanceToSqr(cam) > maxSq || !frustum.isVisible(projectile.getBoundingBox().inflate(4.0))) {
				continue;
			}
			EntityRenderState state = this.extractEntity(projectile, tickProgress);
			state.lightCoords = LightTexture.pack(0, 15);
			if (state instanceof com.example.tudursvehiclemod.client.render.VehicleProjectileRenderState projectileState) {
				projectileState.light = state.lightCoords;
			}
			state.shadowRadius = 0f;
			state.shadowPieces.clear();
			renderState.entityRenderStates.add(state);
		}
	}

	private void tudursvehiclemod$add(AbstractVehicleEntity vehicle, Frustum frustum, Vec3 cam, double maxSq, float tickProgress, LevelRenderState renderState) {
		if (vehicle.distanceToSqr(cam) > maxSq) {
			return;
		}
		// The entity's own box is far smaller than the model: test a box the size of the model's extent instead, so it doesn't vanish while still on screen.
		var def = vehicle.getDefinition();
		double radius = Math.max(16.0, 2.0 * Math.max(def.width(), def.height()));
		for (var runway : def.runways()) {
			radius = Math.max(radius, Math.max(Math.abs(runway.startZ()), Math.abs(runway.endZ())) + 8.0);
		}
		AABB box = new AABB(vehicle.getX() - radius, vehicle.getY() - radius, vehicle.getZ() - radius, vehicle.getX() + radius, vehicle.getY() + radius, vehicle.getZ() + radius);
		if (!frustum.isVisible(box)) {
			return;
		}
		EntityRenderState state = this.extractEntity(vehicle, tickProgress);
		state.lightCoords = LightTexture.pack(0, 15);
		if (state instanceof com.example.tudursvehiclemod.client.render.VehicleRenderState vehicleState) {
			vehicleState.light = state.lightCoords;
		}
		state.shadowRadius = 0f;
		state.shadowPieces.clear();
		renderState.entityRenderStates.add(state);
	}
}
