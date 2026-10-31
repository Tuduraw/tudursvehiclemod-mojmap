package com.example.tudursvehiclemod.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

/** The unseen crew a Fire Support Center (block.FireSupportCenterBlockEntity) puts into a linked vehicle's gunner seat to lay its guns.
 *
 * <p>A vehicle's turrets follow whoever sits in the weapon's seat - its yaw and pitch (AbstractVehicleEntity.getWeaponAimYaw()/getWeaponAimPitch()) - so laying a gun on a target point means seating someone there and turning them to face the firing solution. This entity is that someone: the center turns it every tick, and the turret (turret_rotation_speed, aim limits, settling before it fires - all as for a player gunner) and the client's view of the turret (an occupant's rotation is synced like any entity's) follow on their own.
 *
 * <p>Its own class, not a DummyPilotEntity: a dummy pilot is taken to BE the pilot wherever it sits (AbstractVehicleEntity.tudursvehiclemod$getSeatOccupant(0) returns any DummyPilotEntity aboard), which a gunner in seat 3 must not be.
 *
 * <p>Invisible, can't be hurt, doesn't move or collide. It leaves on its own (discards itself) as soon as its center stops renewing it - OWNER_REFRESH_TIMEOUT_TICKS without tudursvehiclemod$refresh() - so a gunner can never outlive the center that placed it: the center deactivated, broken, unloaded, or the vehicle unlinked, whatever the path. Its owner is kept in a command tag (saved with the entity by vanilla), so one loaded back from a save is still recognized by the center that owns it. */
public class FireSupportGunnerEntity extends LivingEntity {

	/** Prefix of the command tag naming the owning center: "tudursvehiclemod_fire_support:x,y,z". */
	private static final String OWNER_TAG_PREFIX = "tudursvehiclemod_fire_support:";

	/** How long a gunner stays without its center renewing it (tudursvehiclemod$refresh()). Longer than a tick or two so a center that skips a tick doesn't make it blink out of the seat, short enough that a stale one is gone before anyone could want the seat. */
	private static final int OWNER_REFRESH_TIMEOUT_TICKS = 40;

	/** Ticks since the center last renewed this gunner. Not saved: one loaded from disk gets the full timeout to be claimed again. */
	private int ticksSinceRefresh;

	public FireSupportGunnerEntity(EntityType<? extends LivingEntity> type, Level world) {
		super(type, world);
		this.setInvisible(true);
		this.setNoGravity(true);
		this.setSilent(true);
	}

	public static AttributeSupplier.Builder createAttributes() {
		return LivingEntity.createLivingAttributes()
				.add(Attributes.MAX_HEALTH, 20.0)
				.add(Attributes.MOVEMENT_SPEED, 0.0);
	}

	/** Marks this gunner as belonging to the center at ownerPos (replacing any earlier owner). */
	public void tudursvehiclemod$setOwner(BlockPos ownerPos) {
		for (String tag : java.util.List.copyOf(this.getTags())) {
			if (tag.startsWith(OWNER_TAG_PREFIX)) {
				this.removeTag(tag);
			}
		}
		this.addTag(OWNER_TAG_PREFIX + ownerPos.getX() + "," + ownerPos.getY() + "," + ownerPos.getZ());
	}

	/** Whether this gunner belongs to the center at ownerPos. */
	public boolean tudursvehiclemod$isOwnedBy(BlockPos ownerPos) {
		return this.getTags().contains(OWNER_TAG_PREFIX + ownerPos.getX() + "," + ownerPos.getY() + "," + ownerPos.getZ());
	}

	/** Called by the owning center every tick it still wants this gunner in its seat. */
	public void tudursvehiclemod$refresh() {
		this.ticksSinceRefresh = 0;
	}

	/** Faces the given direction - the yaw/pitch the vehicle's weapon tracking reads (see this class's doc). */
	public void tudursvehiclemod$lookAt(float yaw, float pitch) {
		this.setYRot(yaw);
		this.setYHeadRot(yaw);
		this.setYBodyRot(yaw);
		this.setXRot(pitch);
		this.yRotO = yaw;
		this.xRotO = pitch;
	}

	@Override
	public void tick() {
		super.tick();
		if (this.level().isClientSide()) {
			return;
		}
		if (!(this.getVehicle() instanceof AbstractVehicleEntity vehicle) || vehicle.tudursvehiclemod$isDestroyed()
				|| ++this.ticksSinceRefresh > OWNER_REFRESH_TIMEOUT_TICKS) {
			this.stopRiding();
			this.discard();
		}
	}

	@Override
	public boolean hurtServer(ServerLevel world, DamageSource source, float amount) {
		return false;
	}

	@Override
	public boolean isInvulnerableTo(ServerLevel world, DamageSource source) {
		return true;
	}

	@Override
	public boolean isPushable() {
		return false;
	}

	@Override
	public void push(net.minecraft.world.entity.Entity entity) {
	}

	@Override
	public boolean canBeHitByProjectile() {
		return false;
	}

	@Override
	public boolean isPickable() {
		return false;
	}

	@Override
	public boolean isAttackable() {
		return false;
	}

	@Override
	public boolean canSimulateMovement() {
		return false;
	}

	@Override
	public net.minecraft.world.item.ItemStack getItemBySlot(net.minecraft.world.entity.EquipmentSlot slot) {
		return net.minecraft.world.item.ItemStack.EMPTY;
	}

	@Override
	public void setItemSlot(net.minecraft.world.entity.EquipmentSlot slot, net.minecraft.world.item.ItemStack stack) {
	}

	@Override
	public HumanoidArm getMainArm() {
		return HumanoidArm.RIGHT;
	}
}
