package com.example.tudursvehiclemod.client.render;

import com.example.tudursvehiclemod.entity.projectile.VehicleModelProjectileEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.CameraRenderState;

/** Only used for VehicleModelProjectileEntity (weapons with a bullet_model/ bullet_texture configured. */
public class VehicleProjectileRenderer extends EntityRenderer<VehicleModelProjectileEntity, VehicleProjectileRenderState> {

	/** Logged at most once per distinct missing model, rather than every
	 * single render call, so a genuinely broken bullet_model reference is
	 * still noticeable without spamming the log every frame. */
	private static final java.util.Set<net.minecraft.resources.Identifier> loggedMissingModels =
			java.util.concurrent.ConcurrentHashMap.newKeySet();

	public VehicleProjectileRenderer(EntityRendererProvider.Context ctx) {
		super(ctx);
	}

	@Override
	public boolean shouldRender(VehicleModelProjectileEntity entity, net.minecraft.client.renderer.culling.Frustum frustum,
			double x, double y, double z) {
		Boolean override = RenderDistanceHelper.tudursvehiclemod$overrideShouldRender(
				entity.getX(), entity.getY(), entity.getZ(), x, y, z);
		if (override != null) {
			return override;
		}
		return super.shouldRender(entity, frustum, x, y, z);
	}

	@Override
	public VehicleProjectileRenderState createRenderState() {
		return new VehicleProjectileRenderState();
	}

	@Override
	public void extractRenderState(VehicleModelProjectileEntity entity, VehicleProjectileRenderState state, float tickProgress) {
		this.updateRenderState(entity, state, tickProgress);
	}

	/** Keeps its Yarn name on purpose: addon mods built against the release (Yarn) jar call this typed method directly, e.g. super.updateRenderState(...) from their own renderer. In the release jar only the bridge method gets the intermediary name; this typed body stays "updateRenderState". The Mojang-named override above just forwards here. */
	public void updateRenderState(VehicleModelProjectileEntity entity, VehicleProjectileRenderState state, float tickProgress) {
		super.extractRenderState(entity, state, tickProgress);
		state.model = entity.getBulletModel();
		state.texture = entity.getBulletTexture();
		state.scale = entity.getBulletScale();
		state.yaw = entity.getViewYRot(tickProgress);
		state.pitch = entity.getViewXRot(tickProgress);
		state.light = this.getPackedLightCoords(entity, tickProgress);
		// Per Readme_Weapon.txt's own BulletColor/BulletColorInWater doc.
		state.tintColor = entity.tudursvehiclemod$getBulletColor();
	}

	@Override
	public void submit(VehicleProjectileRenderState state, PoseStack matrices, SubmitNodeCollector queue, CameraRenderState cameraState) {
		this.render(state, matrices, queue, cameraState);
	}

	/** Keeps its Yarn name on purpose: addon mods built against the release (Yarn) jar call this typed method directly, e.g. super.render(...) from their own renderer. In the release jar only the bridge method gets the intermediary name; this typed body stays "render". The Mojang-named override above just forwards here. */
	public void render(VehicleProjectileRenderState state, PoseStack matrices, SubmitNodeCollector queue, CameraRenderState cameraState) {
		if (state.model != null && state.texture != null) {
			matrices.pushPose();
			// Same convention as VehicleEntityRenderer.
			matrices.mulPose(Axis.YP.rotationDegrees(-state.yaw));
			matrices.mulPose(Axis.XP.rotationDegrees(state.pitch));
			matrices.scale(state.scale, state.scale, state.scale);

			var modelOpt = ObjModelLoader.get(state.model);
			if (modelOpt.isEmpty() && loggedMissingModels.add(state.model)) {
				org.slf4j.LoggerFactory.getLogger("VehicleMod/ProjectileRender").warn(
						"Bullet model {} not found by ObjModelLoader at render time (scale={})",
						state.model, state.scale);
			}
			modelOpt.ifPresent(model -> {
				RenderType layer = com.example.tudursvehiclemod.client.render.DitherCutoutLayers.entityDitherCutout(state.texture);
				VehicleEntityRenderer.renderTriangles(queue, matrices, layer,
						model.getTrianglesExcluding(java.util.Set.of()), state.light, state.tintColor);
			});
			matrices.popPose();
		}
		super.submit(state, matrices, queue, cameraState);
	}
}
