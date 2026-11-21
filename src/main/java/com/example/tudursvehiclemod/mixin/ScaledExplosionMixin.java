package com.example.tudursvehiclemod.mixin;

import com.example.tudursvehiclemod.entity.DamageScaling;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.ServerExplosion;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** The block destruction multiplier of an explosion made by entity.DamageScaling.explode(): its rays through the blocks (calculateExplodedPositions() - which blocks break, and where fire is set) start that much weaker, so they reach that much less far. Its reach for entities (hurtEntities()), sound and particles keep the full radius. Any other explosion is untouched (multiplier 1). */
@Mixin(ServerExplosion.class)
public abstract class ScaledExplosionMixin {

	@Shadow
	@Final
	private ExplosionDamageCalculator damageCalculator;

	@Redirect(method = "calculateExplodedPositions", at = @At(value = "FIELD", target = "Lnet/minecraft/world/level/ServerExplosion;radius:F"))
	private float tudursvehiclemod$scaleBlockRadius(ServerExplosion explosion) {
		return explosion.radius() * DamageScaling.blockScaleOf(this.damageCalculator);
	}
}
