package com.example.tudursvehiclemod.client.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Hides the player's own first-person arm/held
 * item entirely while riding any vehicle (inventory GUI/HUD are both
 * untouched - this only ever affects the in-world, first-person hand
 * rendering specifically, nothing else). */
@Mixin(ItemInHandRenderer.class)
public class HeldItemRendererMixin {

	@Inject(method = "renderHandsWithItems(FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/player/LocalPlayer;I)V", at = @At("HEAD"), cancellable = true)
	private void tudursvehiclemod$hideWhileRiding(float tickProgress, PoseStack matrices, SubmitNodeCollector orderedRenderCommandQueue,
												   LocalPlayer player, int light, CallbackInfo ci) {
		if (player.getVehicle() instanceof com.example.tudursvehiclemod.entity.AbstractVehicleEntity) {
			ci.cancel();
		}
	}
}
