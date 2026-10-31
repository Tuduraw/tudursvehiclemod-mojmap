package com.example.tudursvehiclemod.entity.projectile;

import com.example.tudursvehiclemod.registry.ModEntityTypes;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Same firing/hit/damage behavior as VehicleProjectileEntity (extended directly, rather than duplicated). */
public class VehicleModelProjectileEntity extends VehicleProjectileEntity {

	protected static final EntityDataAccessor<String> BULLET_MODEL =
			SynchedEntityData.defineId(VehicleModelProjectileEntity.class, EntityDataSerializers.STRING);
	protected static final EntityDataAccessor<String> BULLET_TEXTURE =
			SynchedEntityData.defineId(VehicleModelProjectileEntity.class, EntityDataSerializers.STRING);
	protected static final EntityDataAccessor<Float> BULLET_SCALE =
			SynchedEntityData.defineId(VehicleModelProjectileEntity.class, EntityDataSerializers.FLOAT);

	/** Required by EntityType deserialization (e.g. */
	public VehicleModelProjectileEntity(EntityType<? extends VehicleProjectileEntity> type, Level world) {
		super(type, world);
	}

	public VehicleModelProjectileEntity(Level world, LivingEntity owner, ItemStack stack, float damage, float gravity,
			float explosionPower, boolean explosionDestroysBlocks, boolean flaming,
			Identifier bulletModel, Identifier bulletTexture, float bulletScale) {
		super(ModEntityTypes.VEHICLE_MODEL_PROJECTILE, world, owner, stack, damage, gravity,
				explosionPower, explosionDestroysBlocks, flaming);
		this.entityData.set(BULLET_MODEL, bulletModel.toString());
		this.entityData.set(BULLET_TEXTURE, bulletTexture.toString());
		this.entityData.set(BULLET_SCALE, bulletScale);
	}

	@Override
	protected void defineSynchedData(SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(BULLET_MODEL, "");
		builder.define(BULLET_TEXTURE, "");
		builder.define(BULLET_SCALE, 1.0f);
	}

	/** Null if not yet synced (e.g. the very first tick or two right after spawning, before the client has received this entity's own DataTracker state). */
	public Identifier getBulletModel() {
		String raw = this.entityData.get(BULLET_MODEL);
		return raw.isEmpty() ? null : Identifier.parse(raw);
	}

	public Identifier getBulletTexture() {
		String raw = this.entityData.get(BULLET_TEXTURE);
		return raw.isEmpty() ? null : Identifier.parse(raw);
	}

	public float getBulletScale() {
		return this.entityData.get(BULLET_SCALE);
	}
}
