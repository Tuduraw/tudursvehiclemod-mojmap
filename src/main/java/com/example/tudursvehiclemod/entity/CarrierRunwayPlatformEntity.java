package com.example.tudursvehiclemod.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

/** A runway tile, from before runway surfaces became collision shapes (see RunwayDeckCollision).
 *
 * <p>No longer created. The entity types stay registered so a world saved with tiles in it still loads, and the class stays so addons that check for it keep working; a tile that does load removes itself on its first tick. */
public class CarrierRunwayPlatformEntity extends PathfinderMob {

	public CarrierRunwayPlatformEntity(EntityType<? extends PathfinderMob> entityType, Level world) {
		super(entityType, world);
		this.setInvisible(true);
		this.setNoAi(true);
		this.noPhysics = true;
	}

	/** @deprecated tiles are no longer created; does nothing */
	@Deprecated
	public void tudursvehiclemod$setTileIdentity(int mothershipEntityId, int runwayIndex, int tileIndex) {
	}

	/** @deprecated tiles are no longer created; does nothing */
	@Deprecated
	public void tudursvehiclemod$setMothership(java.util.UUID mothershipUuid) {
	}

	@Override
	public void tick() {
		if (!this.level().isClientSide()) {
			this.discard();
		}
	}

	@Override
	public boolean canBeCollidedWith(net.minecraft.world.entity.Entity other) {
		return false;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public boolean isAttackable() {
		return false;
	}

	@Override
	public boolean hurtServer(ServerLevel world, DamageSource source, float amount) {
		return false;
	}

	@Override
	protected void dropCustomDeathLoot(ServerLevel world, DamageSource source, boolean causedByPlayer) {
	}

	public static AttributeSupplier.Builder createAttributes() {
		return PathfinderMob.createMobAttributes().add(Attributes.MAX_HEALTH, 1.0);
	}
}
