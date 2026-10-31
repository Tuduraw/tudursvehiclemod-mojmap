package com.example.tudursvehiclemod.client.render;

import com.example.tudursvehiclemod.entity.CarrierRunwayPlatformEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;

/** CarrierRunwayPlatformEntity's own EntityType (see ModEntityTypes's own doc) was registered server-side but never had a corresponding client-side renderer registered - every entity type needs one, even one that's always invisible and genuinely has nothing to draw, or the client crashes the instant it tries to render one at all. This renderer does nothing at all (render() is a no-op) - matches this entity's own always-invisible nature (setInvisible(true) is set every time one is created, from AbstractVehicleEntity's own tudursvehiclemod$updateCarrierRunwayPlatform()) exactly; there's genuinely no visual to draw, ever. Method signature matches this project's own VehicleEntityRenderer exactly (MatrixStack/OrderedRenderCommandQueue/CameraRenderState) - an earlier draft of this file was mistakenly written against an older Matrix4f/VertexConsumerProvider/int-light EntityRenderer API that doesn't match what this Minecraft version actually uses. */
public final class CarrierRunwayPlatformEntityRenderer extends EntityRenderer<CarrierRunwayPlatformEntity, EntityRenderState> {

	public CarrierRunwayPlatformEntityRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	public EntityRenderState createRenderState() {
		return new EntityRenderState();
	}

	@Override
	public void submit(EntityRenderState state, PoseStack matrices, SubmitNodeCollector queue, CameraRenderState cameraState) {
		// Per this class's own doc: intentionally empty - this entity is always invisible, with nothing to ever actually draw.
	}
}
