package com.example.tudursvehiclemod.client.mixin;

import com.example.tudursvehiclemod.client.farvehicle.FarVehicleProxies;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** With far vehicles drawn (VehicleModConfig.renderFarVehicles), the view reaches at least as far as they are drawn: the game's own far clipping plane is four times the render distance, so at a short render distance a far vehicle would otherwise be cut off before it got there. */
@Mixin(GameRenderer.class)
public abstract class GameRendererFarPlaneMixin {

	@Inject(method = "getDepthFar", at = @At("RETURN"), cancellable = true)
	private void tudursvehiclemod$reachFarVehicles(CallbackInfoReturnable<Float> cir) {
		if (FarVehicleProxies.enabled()) {
			float wanted = FarVehicleProxies.distance() + 64f;
			if (wanted > cir.getReturnValueF()) {
				cir.setReturnValue(wanted);
			}
		}
	}
}
