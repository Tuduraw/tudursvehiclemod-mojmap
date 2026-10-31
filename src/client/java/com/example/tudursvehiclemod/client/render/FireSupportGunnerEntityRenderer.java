package com.example.tudursvehiclemod.client.render;

import com.example.tudursvehiclemod.entity.FireSupportGunnerEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.CameraRenderState;

/** Draws nothing: a Fire Support gunner is never seen (see FireSupportGunnerEntity's own doc). Every entity type needs a renderer registered all the same, as CarrierRunwayPlatformEntityRenderer's doc explains. */
public final class FireSupportGunnerEntityRenderer extends EntityRenderer<FireSupportGunnerEntity, EntityRenderState> {

	public FireSupportGunnerEntityRenderer(EntityRendererProvider.Context context) {
		super(context);
	}

	@Override
	public EntityRenderState createRenderState() {
		return new EntityRenderState();
	}

	@Override
	public void submit(EntityRenderState state, PoseStack matrices, SubmitNodeCollector queue, CameraRenderState cameraState) {
	}
}
