package com.example.tudursvehiclemod.entity;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;

/** DIRECT test confirming isPushable()-based push-apart collision genuinely does NOT occur against a bare Entity subclass (even with isPushable() explicitly returning true) - only against a LivingEntity/MobEntity-based one (this project's own earlier Shulker-style attempt, before that was replaced due to an UNRELATED sizing constraint - see below): the actual push-resolution logic that makes "standing on it, doesn't sink through" work at all is apparently invoked specifically as part of LivingEntity's own movement/collision code, not generically available to any bare Entity that merely declares itself pushable. This class is back to extending PathAwareEntity (LivingEntity's own descendant) for that reason.
 *
 * The tradeoff this reintroduces: calculateBoundingBox() (Entity) AND getDimensions() (LivingEntity) are BOTH confirmed final in this Minecraft version - a LivingEntity subclass genuinely cannot have a per-instance-mutable size at all, unlike a bare Entity (which CAN override getDimensions(), but apparently doesn't get real push-collision). Rather than fight this again, this entity now uses a FIXED size, set once at EntityType registration time (see ModEntityTypes's own doc) and never changed afterward - AbstractVehicleEntity's own runway-tiling logic tiles a 2-D grid of these FIXED-size tiles (both across the runway's own width AND along its own length) to cover an arbitrary runway shape, rather than trying to give any single tile a custom size at all. */
public class CarrierRunwayPlatformEntity extends PathfinderMob {

	/** Which vehicle this tile belongs to, checked periodically (see tick() below) as a defense-in-depth self-cleanup, independent of the mothership's own onRemove() handling. Persisted (see readCustomData/writeCustomData below) so this self-check survives a world reload too. Null until set (see tudursvehiclemod$setMothership() below) - a tile with no mothership set at all never self-checks (shouldn't normally happen, but fails safe rather than immediately self-discarding). Deliberately a plain, non-synced field (unlike MOTHERSHIP_ENTITY_ID below) - this specific self-check is server-only, so a client-side copy was never needed for IT. */
	private java.util.UUID mothershipUuid;
	private int ticksSinceMothershipCheck;

	/** This tile's own identity - which mothership (by entity id, not UUID - a client world's own getEntityById() lookup is the natural/cheap way to resolve a locally-known entity, unlike a UUID which would need a full-world UUID scan), which of that mothership's own runways, and which grid index within that runway - synced via DataTracker so the client actually knows it. -1 (MOTHERSHIP_ENTITY_ID's own sentinel) until tudursvehiclemod$setTileIdentity() is called, right after spawning (same timing as tudursvehiclemod$setMothership() above) - the client-side tick() branch below no-ops entirely until then. */
	private static final net.minecraft.network.syncher.EntityDataAccessor<Integer> MOTHERSHIP_ENTITY_ID =
			net.minecraft.network.syncher.SynchedEntityData.defineId(CarrierRunwayPlatformEntity.class, net.minecraft.network.syncher.EntityDataSerializers.INT);
	private static final net.minecraft.network.syncher.EntityDataAccessor<Integer> RUNWAY_INDEX =
			net.minecraft.network.syncher.SynchedEntityData.defineId(CarrierRunwayPlatformEntity.class, net.minecraft.network.syncher.EntityDataSerializers.INT);
	private static final net.minecraft.network.syncher.EntityDataAccessor<Integer> TILE_INDEX =
			net.minecraft.network.syncher.SynchedEntityData.defineId(CarrierRunwayPlatformEntity.class, net.minecraft.network.syncher.EntityDataSerializers.INT);

	@Override
	protected void defineSynchedData(net.minecraft.network.syncher.SynchedEntityData.Builder builder) {
		super.defineSynchedData(builder);
		builder.define(MOTHERSHIP_ENTITY_ID, -1);
		builder.define(RUNWAY_INDEX, 0);
		builder.define(TILE_INDEX, 0);
	}

	/** Per MOTHERSHIP_ENTITY_ID's own doc: sets this tile's own client-mirroring identity. Called alongside (not instead of) tudursvehiclemod$setMothership() above - that method's own UUID is still needed for the existing server-only self-heal check in tick() below, entirely separate from this. */
	public void tudursvehiclemod$setTileIdentity(int mothershipEntityId, int runwayIndex, int tileIndex) {
		this.entityData.set(MOTHERSHIP_ENTITY_ID, mothershipEntityId);
		this.entityData.set(RUNWAY_INDEX, runwayIndex);
		this.entityData.set(TILE_INDEX, tileIndex);
	}

	public CarrierRunwayPlatformEntity(EntityType<? extends PathfinderMob> entityType, Level world) {
		super(entityType, world);
	}

	/** Orphaned platform tiles could occur - see mothershipUuid's own doc. Set once, right after spawning (AbstractVehicleEntity's own tudursvehiclemod$updateCarrierRunwayPlatform()). */
	public void tudursvehiclemod$setMothership(java.util.UUID mothershipUuid) {
		this.mothershipUuid = mothershipUuid;
	}

	/** Every 100 ticks (5 seconds - frequent enough to clean up promptly, cheap enough to cost nothing meaningful), verifies the owning mothership is still actually present; self-discards if not, rather than persisting forever as an invisible, inert leftover. */
	@Override
	public void tick() {
		super.tick();
		if (this.level().isClientSide()) {
			// Independently recomputes this tile's own position every client tick (same deterministic formula the server uses), rather than relying on synced position packets - reduces per-tile network cost and eliminates client-side "briefly falls through, then snaps back" flicker in local physics prediction. Server-side authority is unchanged.
			int mothershipEntityId = this.entityData.get(MOTHERSHIP_ENTITY_ID);
			if (mothershipEntityId < 0) {
				return;
			}
			if (!(this.level().getEntity(mothershipEntityId) instanceof AbstractVehicleEntity mothership)) {
				return;
			}
			int runwayIndex = this.entityData.get(RUNWAY_INDEX);
			java.util.List<com.example.tudursvehiclemod.asset.RunwayDefinition> runways = mothership.getDefinition().runways();
			if (runwayIndex < 0 || runwayIndex >= runways.size()) {
				return;
			}
			com.example.tudursvehiclemod.asset.RunwayDefinition runway = runways.get(runwayIndex)
					.tudursvehiclemod$withHatchProgress(mothership.tudursvehiclemod$getRunwayHatchProgress(runwayIndex));
			net.minecraft.world.phys.Vec3 tileWorldPos = AbstractVehicleEntity.tudursvehiclemod$computeCarrierRunwayTileWorldPosClientSide(
					mothership, runway, this.getBbWidth(), this.entityData.get(TILE_INDEX));
			if (tileWorldPos == null) {
				return;
			}
			this.setPos(tileWorldPos.x, tileWorldPos.y - this.getBbHeight(), tileWorldPos.z);
			return;
		}
		if (this.mothershipUuid == null) {
			return;
		}
		this.ticksSinceMothershipCheck++;
		if (this.ticksSinceMothershipCheck < 100) {
			return;
		}
		this.ticksSinceMothershipCheck = 0;
		if (this.level() instanceof ServerLevel serverWorld && serverWorld.getEntity(this.mothershipUuid) == null) {
			this.discard();
		}
	}

	/** Per this class's own doc: intentionally empty - no goals of any kind, so this entity never moves, looks around, or does anything at all on its own initiative between the explicit setPosition() calls that actually control it. */
	@Override
	protected void registerGoals() {
	}

	/** But standing on top (genuine vertical support, not falling through) did not - a further, more careful bytecode comparison found isCollidable() overridden specifically and ONLY by ShulkerEntity among Entity/LivingEntity/MobEntity/ArmorStandEntity (none of the others touch it at all). This, not isPushable()/pushAwayFrom() (a separate mechanism that only ever resolves horizontal overlap between entities), is what actually gates whether another entity's own bounding box is treated as a genuine solid obstacle for movement/standing purposes at all - explaining exactly the reported split (horizontal push worked via the LivingEntity default, vertical support did not since nothing had ever overridden this specific method). */
	@Override
	public boolean canBeCollidedWith(net.minecraft.world.entity.Entity other) {
		return true;
	}

	/** Confirmed via debug display: visible position variance/sway occurred in the runway tile entities themselves while the mothership moves. This class's own isPushable()=true (LivingEntity's own default, confirmed elsewhere in this class's own doc as needed for OTHER entities' own physics) means overlapping runway tiles (deliberately sized to overlap adjacent ones - see AbstractVehicleEntity's own tiling doc) constantly push against EACH OTHER too, not just against other entity types - fought (and eventually overwhelmed) each tick by this vehicle's own repeated setPosition() call resetting each tile back to its own intended position, but visibly swaying the tiles in the meantime. Skips pushing specifically when the other entity is ALSO a runway tile; still pushes normally against any other entity type, preserving that confirmed-working "standing on it" behavior. */
	@Override
	public void push(net.minecraft.world.entity.Entity entity) {
		if (entity instanceof CarrierRunwayPlatformEntity) {
			return;
		}
		super.push(entity);
	}

	/** This entity never equips/drops anything - explicitly empty (overrides the vanilla default, which would otherwise roll for random equipment drops on death, irrelevant here since this entity is invulnerable and never dies, but kept explicit for clarity). */
	@Override
	protected void dropCustomDeathLoot(ServerLevel world, DamageSource source, boolean causedByPlayer) {
	}

	/** Per a further direct report that this entity could still be attacked (with visible hit effects) despite setInvulnerable(true) already being set by the caller: isAttackable() and damage() are both overridden here to reject any attack/damage attempt outright, at the earliest point the game itself checks - rather than relying only on the generic invulnerable flag, which apparently doesn't suppress the attack ATTEMPT itself (and its own visual/audio hit feedback) even when it does suppress the resulting damage. */
	@Override
	public boolean isAttackable() {
		return false;
	}

	@Override
	public boolean hurtServer(ServerLevel world, DamageSource source, float amount) {
		return false;
	}

	/** Never persisted - a fresh one is simply respawned (see AbstractVehicleEntity's own tudursvehiclemod$updateCarrierRunwayPlatform()) the next time its own mothership ticks after a world reload, same "in-memory-only, re-established as needed" convention as several other pieces of transient vehicle-tracking state elsewhere in this project - EXCEPT mothershipUuid, which IS persisted (see that field's own doc for why). */
	@Override
	protected void readAdditionalSaveData(net.minecraft.world.level.storage.ValueInput view) {
		super.readAdditionalSaveData(view);
		String uuidString = view.getStringOr("MothershipUuid", "");
		if (!uuidString.isEmpty()) {
			this.mothershipUuid = java.util.UUID.fromString(uuidString);
		}
	}

	@Override
	protected void addAdditionalSaveData(net.minecraft.world.level.storage.ValueOutput view) {
		super.addAdditionalSaveData(view);
		if (this.mothershipUuid != null) {
			view.putString("MothershipUuid", this.mothershipUuid.toString());
		}
	}

	/** Standard Fabric registration pattern (see ModEntityTypes's own doc for where this is actually called) - a minimal attribute set is still required for ANY LivingEntity subclass to exist at all without crashing, even though none of these values are ever meaningfully exercised (this entity is invulnerable, and never moves/attacks under its own power). */
	public static AttributeSupplier.Builder createAttributes() {
		return LivingEntity.createLivingAttributes()
				.add(Attributes.MAX_HEALTH, 1.0)
				.add(Attributes.MOVEMENT_SPEED, 0.0)
				.add(Attributes.FOLLOW_RANGE, 0.0)
				.add(Attributes.ATTACK_DAMAGE, 0.0)
				.add(Attributes.ARMOR, 0.0)
				.add(Attributes.ARMOR_TOUGHNESS, 0.0)
				.add(Attributes.KNOCKBACK_RESISTANCE, 1.0)
				.add(Attributes.SAFE_FALL_DISTANCE, 0.0);
	}
}
