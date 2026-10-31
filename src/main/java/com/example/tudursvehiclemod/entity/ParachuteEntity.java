package com.example.tudursvehiclemod.entity;

import com.example.tudursvehiclemod.VehicleMod;
import com.example.tudursvehiclemod.asset.VehicleDefinition;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** Unified across every trigger (ejection seat, mob drop, and equipped-parachute jump alike) - ("パラシュートをエンティティとして登録し、搭乗させるように機能を変更しようと思います。射出座席や装備時のパラシュートについても同様の動作に統一してください"): a lightweight AbstractVehicleEntity subclass reusing this project's own already-established model/texture rendering, mesh-based weapon-hit detection (MeshedEntity + AbstractVehicleEntity's own STATIC_BODY_MESH_CACHE machinery - no special hit-detection code of its own is needed here at all, since VehicleProjectileEntity's own hit logic already targets any AbstractVehicleEntity generically), and health/damage/destruction system (VehicleDefinition's own max_health - reaching 0 destroys this entity exactly like any other vehicle, which in turn dismounts whoever was riding it, satisfying the direct request that running out of durability makes the parachute disappear).
 *
 * <p>Deliberately does NOT reuse any of CarEntity/AircraftEntity's own much larger, vehicle-specific movement logic (throttle ramping, fuel, weapons-as-driving-input,..) - a parachute has exactly one job: descend slowly and predictably once airborne, with no pilot-driven propulsion of its own at all. See tudursvehiclemod$updateVehicleMovement() below for the actual physics. */
public class ParachuteEntity extends AbstractVehicleEntity {

	/** The terminal (maximum) downward speed this entity ever settles at, roughly matching what the previous Slow Falling-based mechanism produced. Blocks/tick. */
	private static final double MAX_DESCENT_SPEED = 0.2;
	/** How quickly vertical velocity eases toward -MAX_DESCENT_SPEED each tick - large enough that a parachute deployed mid-fall (already moving fast downward) visibly, promptly slows to the controlled descent speed rather than taking a long time to "catch up". */
	private static final double DESCENT_EASE_RATE = 0.15;
	/** A destroyed parachute's free fall - the same gravity and drag as an ordinary falling entity. */
	private static final double FREE_FALL_GRAVITY = 0.08;
	private static final double FREE_FALL_DRAG = 0.98;
	/** Horizontal momentum retained per tick - a parachute drifts gently rather than either freezing horizontal motion outright or continuing to carry a launch's own full horizontal speed indefinitely. */
	private static final double HORIZONTAL_DRAG_RETENTION = 0.96;

	/** This parachute's own current tint color (ARGB), synced to the client so client.render.VehicleEntityRenderer can multiply it into the OBJ mesh's own vertex colors - the same mechanism that class already uses for a destroyed vehicle's own charred-black tint (see that class's own doc), reused here for dye color instead. Defaults to opaque white (0xFFFFFFFF, no tint) - matches an undyed leather-style item's own vanilla default. */
	private static final net.minecraft.network.syncher.EntityDataAccessor<Integer> TINT_COLOR =
			net.minecraft.network.syncher.SynchedEntityData.defineId(ParachuteEntity.class, net.minecraft.network.syncher.EntityDataSerializers.INT);

	@Override
	protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(TINT_COLOR, 0xFFFFFFFF);
	}

	public int tudursvehiclemod$getTintColor() {
		return this.getEntityData().get(TINT_COLOR);
	}

	public void tudursvehiclemod$setTintColor(int argb) {
		this.getEntityData().set(TINT_COLOR, argb);
	}

	@Override
	protected void readAdditionalSaveData(net.minecraft.world.level.storage.ValueInput view) {
		super.readAdditionalSaveData(view);
		this.tudursvehiclemod$setTintColor(view.getIntOr("TintColor", 0xFFFFFFFF));
		// Saved so a parachute reloaded from disk still hands its item back - this used to reset to false on every load. An older save without the key keeps the old behavior (no item).
		this.consumedRealItem = view.getBooleanOr("ConsumedRealItem", false);
	}

	@Override
	protected void addAdditionalSaveData(net.minecraft.world.level.storage.ValueOutput view) {
		super.addAdditionalSaveData(view);
		view.putInt("TintColor", this.tudursvehiclemod$getTintColor());
		view.putBoolean("ConsumedRealItem", this.consumedRealItem);
	}

	/** How many ticks after actually settling on the ground the rider is automatically dismounted ("着地から20tick後にパラシュートから自動的に降車"). Counts up from -1 (not yet landed) once isOnGround() first reads true; reaching this value triggers the dismount, but - unlike the previous behavior - does NOT discard this entity itself, which instead survives afterward as a vacant, motionless, recoverable item source (see tudursvehiclemod$interact()'s own doc). */
	private static final int LANDED_DISMOUNT_DELAY_TICKS = 20;
	/** -1 while still airborne/mid-controlled-descent; counts 0, 1, 2,.. once isOnGround() first reads true, until it reaches LANDED_DISMOUNT_DELAY_TICKS (dismount) and then keeps sitting at that value forever afterward (this entity is now vacant and inert - see updateVehicleMovement()'s own doc for why physics stops running once this happens). */
	private int landedTicks = -1;

	/** The horizontal speed steering eases toward, once WASD input is held. Deliberately gentle - real parachute steering is a slow drift, not aircraft-style maneuvering. */
	private static final double HORIZONTAL_STEER_TARGET_SPEED = 0.3;
	/** How quickly horizontal velocity eases toward HORIZONTAL_STEER_TARGET_SPEED each tick, rather than that speed being injected directly as an instantaneous per-tick delta (which a frame-to-frame wobble in the steering direction - itself derived from the pilot's own last-reported view yaw, which can lag slightly behind their actual current view due to ordinary network latency - could otherwise translate directly into visible jitter). */
	private static final double HORIZONTAL_STEER_EASE_RATE = 0.1;

	/** A parachute spawned WITHOUT actually consuming a real item (a virtual grant, e.g. enable_parachuting/mob-drop with nothing equipped) never itself be recoverable as an item at all, automatically or manually ("パラシュート降下などのアイテム消費せずに出現した場合は通常の撤去も含めてアイテム化しないようにしてください") - true only when tudursvehiclemod$spawnAndMount()'s own caller actually cleared a real ParachuteItem off the rider's own chest slot to create this entity. Server-side only (never synced - only ever read here, on the server); saved with the entity. */
	private boolean consumedRealItem = false;

	public boolean tudursvehiclemod$didConsumeRealItem() {
		return this.consumedRealItem;
	}

	/** How many ticks of being landed-and-vacant (see tudursvehiclemod$isLandedAndVacant()'s own doc) trigger this. Only actually produces an item if consumedRealItem is true (see that field's own doc) - a virtual-grant parachute simply discards itself with nothing dropped once this elapses instead. */
	private static final int VACANT_AUTO_ITEMIZE_TICKS = 100;
	/** Counts up once tudursvehiclemod$isLandedAndVacant() first becomes true; -1 beforehand (matches landedTicks' own "-1 until it starts, then counts up" convention). */
	private int vacantTicks = -1;

	public ParachuteEntity(EntityType<?> type, Level world) {
		super(type, world);
	}

	@Override
	protected Identifier defaultDefinitionId() {
		return Identifier.fromNamespaceAndPath(VehicleMod.MOD_ID, "parachute");
	}

	/** A small, fixed hitbox - deliberately NOT scaled from VehicleDefinition's own width/height (unlike most other vehicle types here), since a parachute's own visual canopy is meant to be considerably larger than anything the rider needs to actually collide with; the mesh-based weapon-hit detection (see class-level doc) already lets a shot register against the full visual canopy shape regardless of this small physical hitbox. */
	@Override
	public net.minecraft.world.entity.EntityDimensions getDimensions(net.minecraft.world.entity.Pose pose) {
		return net.minecraft.world.entity.EntityDimensions.scalable(0.6f, 0.6f);
	}

	/** True once this entity has actually landed AND the LANDED_DISMOUNT_DELAY_TICKS delay has fully elapsed (the rider, if any, has already been dismounted by now) - see landedTicks' own doc. Used both to stop this entity's own physics (updateVehicleMovement()) and to gate tudursvehiclemod$interact()'s own recovery behavior. */
	private boolean tudursvehiclemod$isLandedAndVacant() {
		return this.landedTicks >= LANDED_DISMOUNT_DELAY_TICKS;
	}

	@Override
	protected void updateVehicleMovement(VehicleDefinition def) {
		// A destroyed (shot-through) canopy no longer works: the rider is dropped to fall on their own, at the normal speed and with the normal fall damage (a vehicle doesn't pass its fall damage on - see AbstractVehicleEntity.handleFallDamage()), and the wreck falls like any loose object and is gone once it lands - no item back.
		if (this.tudursvehiclemod$isDestroyed()) {
			if (!this.level().isClientSide()) {
				for (Entity passenger : this.tudursvehiclemod$getRealPassengerList()) {
					passenger.stopRiding();
				}
			}
			if (this.tudursvehiclemod$isSinking()) {
				// Came down on water: the ordinary wreck sinking (AbstractVehicleEntity.tudursvehiclemod$applySinkingMotion()) takes over.
				this.move(MoverType.SELF, this.getDeltaMovement());
				return;
			}
			Vec3 velocity = this.getDeltaMovement();
			this.setDeltaMovement(velocity.x * FREE_FALL_DRAG, (velocity.y - FREE_FALL_GRAVITY) * FREE_FALL_DRAG, velocity.z * FREE_FALL_DRAG);
			this.move(MoverType.SELF, this.getDeltaMovement());
			if (this.onGround() && !this.level().isClientSide()) {
				this.discard();
			}
			return;
		}
		// Per LANDED_DISMOUNT_DELAY_TICKS' own doc: once this entity has fully finished its own landing sequence, it's an inert, vacant leftover - no descent physics run at all any more, it just sits exactly where it settled until someone actually interacts with it (recovery), it auto-itemizes/discards after VACANT_AUTO_ITEMIZE_TICKS, or it happens to be destroyed by ordinary vehicle damage.
		if (this.tudursvehiclemod$isLandedAndVacant()) {
			if (!this.level().isClientSide()) {
				this.vacantTicks++;
				if (this.vacantTicks >= VACANT_AUTO_ITEMIZE_TICKS) {
					// Per consumedRealItem's own doc: a virtual-grant parachute (never actually consumed a real item to exist) has nothing to hand back - simply vanishes. A real one drops the same recovered stack tudursvehiclemod$interact() would have handed to a player, at this entity's own current position, since nobody's here to insert it into an inventory directly.
					if (this.consumedRealItem && this.level() instanceof net.minecraft.server.level.ServerLevel serverWorld) {
						net.minecraft.world.entity.item.ItemEntity itemEntity = new net.minecraft.world.entity.item.ItemEntity(
								serverWorld, this.getX(), this.getY(), this.getZ(), this.tudursvehiclemod$createRecoveredItemStack());
						serverWorld.addFreshEntity(itemEntity);
					}
					this.discard();
				}
			}
			return;
		}
		if (this.landedTicks < 0) {
			Vec3 velocity = this.getDeltaMovement();
			double targetVerticalSpeed = -MAX_DESCENT_SPEED;
			double newVerticalSpeed = velocity.y + (targetVerticalSpeed - velocity.y) * DESCENT_EASE_RATE;
			double newHorizontalX = velocity.x * HORIZONTAL_DRAG_RETENTION;
			double newHorizontalZ = velocity.z * HORIZONTAL_DRAG_RETENTION;
			// Per HORIZONTAL_STEER_ACCEL's own doc: this entity has no facing of its own, so steering is applied relative to the CONTROLLING RIDER's own current view yaw instead - matches how a real parachutist steers by shifting their own weight/body, not by the canopy itself turning to face a direction.
			if (this.getControllingPassenger() instanceof net.minecraft.world.entity.player.Player player) {
				double throttleInput = this.getSyncedThrottleInput();
				double sidewaysInput = this.getSyncedSidewaysInput();
				if (throttleInput != 0.0 || sidewaysInput != 0.0) {
					double yawRad = Math.toRadians(player.getYRot());
					double forwardX = -Math.sin(yawRad);
					double forwardZ = Math.cos(yawRad);
					double rightX = Math.cos(yawRad);
					double rightZ = Math.sin(yawRad);
					double targetVelX = (forwardX * throttleInput + rightX * sidewaysInput) * HORIZONTAL_STEER_TARGET_SPEED;
					double targetVelZ = (forwardZ * throttleInput + rightZ * sidewaysInput) * HORIZONTAL_STEER_TARGET_SPEED;
					newHorizontalX += (targetVelX - newHorizontalX) * HORIZONTAL_STEER_EASE_RATE;
					newHorizontalZ += (targetVelZ - newHorizontalZ) * HORIZONTAL_STEER_EASE_RATE;
				}
			}
			this.setDeltaMovement(newHorizontalX, newVerticalSpeed, newHorizontalZ);
			this.move(MoverType.SELF, this.getDeltaMovement());
			if (this.onGround()) {
				this.landedTicks = 0;
			}
			return;
		}
		// Landed, but the LANDED_DISMOUNT_DELAY_TICKS grace period hasn't fully elapsed yet - held motionless (no further descent physics at all) rather than continuing to ease towards MAX_DESCENT_SPEED, which would otherwise keep nudging it downward into the ground for as long as this delay lasts.
		this.setDeltaMovement(0.0, 0.0, 0.0);
		if (!this.level().isClientSide()) {
			this.landedTicks++;
			if (this.landedTicks >= LANDED_DISMOUNT_DELAY_TICKS) {
				for (Entity passenger : this.tudursvehiclemod$getRealPassengerList()) {
					passenger.stopRiding();
				}
			}
		}
	}

	/** A landed, vacant (nobody riding) parachute hands back an actual ParachuteItem stack - carrying this entity's own current dye color (see TINT_COLOR's own doc), matching whatever color it was deployed with - and discards itself, rather than mounting/pack-up/every other ordinary vehicle interaction. Per consumedRealItem's own doc, this ONLY applies to a parachute that actually consumed a real item to exist in the first place - a virtual-grant one falls through to normal vehicle interaction instead, same as if it were still occupied/airborne. Falls through to the normal AbstractVehicleEntity behavior (mount, sneak-to-pack-up, etc.) for every other case too - still airborne, still occupied, or a landed-but-still-within-the-dismount-delay parachute someone happens to click during that brief window. */
	@Override
	public net.minecraft.world.InteractionResult interact(net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand) {
		if (this.consumedRealItem && this.tudursvehiclemod$isLandedAndVacant() && this.tudursvehiclemod$getRealPassengerList().isEmpty()) {
			if (!this.level().isClientSide()) {
				net.minecraft.world.item.ItemStack recovered = this.tudursvehiclemod$createRecoveredItemStack();
				if (!player.getInventory().add(recovered)) {
					player.drop(recovered, false);
				}
				this.discard();
			}
			return net.minecraft.world.InteractionResult.SUCCESS;
		}
		return super.interact(player, hand);
	}

	/** Shared by tudursvehiclemod$interact()'s own manual recovery and updateVehicleMovement()'s own automatic cleanup after VACANT_AUTO_ITEMIZE_TICKS - a fresh ParachuteItem stack carrying this entity's own current dye color. */
	private net.minecraft.world.item.ItemStack tudursvehiclemod$createRecoveredItemStack() {
		net.minecraft.world.item.ItemStack recovered = new net.minecraft.world.item.ItemStack(com.example.tudursvehiclemod.item.ModItems.PARACHUTE);
		recovered.set(net.minecraft.core.component.DataComponents.DYED_COLOR,
				new net.minecraft.world.item.component.DyedItemColor(this.tudursvehiclemod$getTintColor()));
		return recovered;
	}

	/** Spawns a fresh ParachuteEntity at the given position and immediately mounts rider onto it - the single, shared entry point entity.AbstractVehicleEntity's own tudursvehiclemod$tryEjectSeat()/tudursvehiclemod$tryParachuteJump()/tudursvehiclemod$dropNextMobDropPassenger(), and network.ModNetworking's own equipped-parachute-jump handler, all now use instead of each separately managing a status effect. colorArgb is this spawn's own tint color (see TINT_COLOR's own doc) - callers with an actual dyed parachute item pass that item's own dye color; every other caller passes the default white (0xFFFFFFFF). Does nothing (silently) if spawning somehow fails (matches this project's own established convention for this kind of failure - e.g. entity.AbstractVehicleEntity's own tudursvehiclemod$spawnOneCarrierAircraft() doc). */
	public static void tudursvehiclemod$spawnAndMount(net.minecraft.world.entity.LivingEntity rider,
			net.minecraft.server.level.ServerLevel world, Vec3 position, int colorArgb, boolean consumedRealItem) {
		Entity spawned = com.example.tudursvehiclemod.registry.ModEntityTypes.PARACHUTE.create(world, entity -> {
			if (entity instanceof AbstractVehicleEntity vehicle) {
				vehicle.setVehicleDefinitionId(Identifier.fromNamespaceAndPath(VehicleMod.MOD_ID, "parachute"));
			}
			if (entity instanceof ParachuteEntity parachute) {
				parachute.tudursvehiclemod$setTintColor(colorArgb);
				parachute.consumedRealItem = consumedRealItem;
			}
		}, net.minecraft.core.BlockPos.containing(position.x, position.y, position.z),
				net.minecraft.world.entity.EntitySpawnReason.TRIGGERED, false, false);
		if (spawned == null) {
			return;
		}
		spawned.setPos(position.x, position.y, position.z);
		world.addFreshEntity(spawned);
		boolean mounted = spawned instanceof AbstractVehicleEntity vehicle && vehicle.tudursvehiclemod$mountToSeat(rider, 0);
	}
}
