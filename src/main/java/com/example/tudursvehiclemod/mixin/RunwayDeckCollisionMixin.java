package com.example.tudursvehiclemod.mixin;

import com.example.tudursvehiclemod.entity.RunwayDeckCollision;
import java.util.List;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.EntityGetter;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Adds the runways' surfaces (entity.RunwayDeckCollision) to what the game collides entities with. EntityGetter.getEntityCollisions() is where every entity-based collision shape is looked up - movement (Entity.collide()), "is there room here" checks (noCollision(): sneaking at an edge, spawning, standing up) - on both sides. */
@Mixin(EntityGetter.class)
public interface RunwayDeckCollisionMixin {

	@Inject(method = "getEntityCollisions", at = @At("RETURN"), cancellable = true)
	private void tudursvehiclemod$addRunwayDecks(Entity entity, AABB box, CallbackInfoReturnable<List<VoxelShape>> cir) {
		if (this instanceof net.minecraft.world.level.Level level && box.getSize() >= 1.0E-7) {
			cir.setReturnValue(RunwayDeckCollision.withRunways(level, entity, box, cir.getReturnValue()));
		}
	}
}
