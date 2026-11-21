package com.example.tudursvehiclemod.entity;

import com.example.tudursvehiclemod.VehicleModServerConfig;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.EntityBasedExplosionDamageCalculator;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

/** The server's damage and explosion multipliers (VehicleModServerConfig.weaponDamageMultiplier, explosionBlastMultiplier, explosionBlockMultiplier), in one place, for this mod's weapons and explosions - and for addons that want theirs to follow the same settings.
 *
 * <ul>
 * <li>Weapon damage: a projectile hitting an entity or a vehicle. At 0 a hit does no damage; a living target is still knocked back as by the hit ({@link #hitEntity}).</li>
 * <li>Explosion blast: the damage an explosion does to entities and vehicles. At 0 it does none, and still pushes them away ({@link #explode}).</li>
 * <li>Explosion block destruction: the radius within which an explosion breaks blocks (and sets fire). At 0 it breaks none.</li>
 * </ul>
 * Read fresh every time, so a change in the server config screen takes effect immediately. Crash, collision and drowning damage is not weapon damage and isn't scaled. */
public final class DamageScaling {

	private DamageScaling() {
	}

	/** The weapon damage multiplier (0 or more). */
	public static float weaponDamage() {
		return nonNegative(VehicleModServerConfig.get().weaponDamageMultiplier);
	}

	/** The explosion blast (entity damage) multiplier (0 or more). */
	public static float explosionBlast() {
		return nonNegative(VehicleModServerConfig.get().explosionBlastMultiplier);
	}

	/** The explosion block destruction multiplier (0 or more). */
	public static float explosionBlock() {
		return nonNegative(VehicleModServerConfig.get().explosionBlockMultiplier);
	}

	private static float nonNegative(double value) {
		return Double.isFinite(value) && value > 0.0 ? (float) value : 0f;
	}

	/** A weapon's damage after the multiplier. */
	public static float scaleWeaponDamage(float damage) {
		return damage * weaponDamage();
	}

	/** Damages target by a projectile's hit - damage already scaled ({@link #scaleWeaponDamage}). When the multiplier has brought a hit that would have done damage down to none, a living target is only knocked back, as vanilla knocks back for a projectile hit (strength 0.4, along the projectile's horizontal motion), with no damage, hurt flash or invulnerability time. Returns whether the target was damaged. */
	public static boolean hitEntity(ServerLevel level, Entity target, net.minecraft.world.damagesource.DamageSource source, float baseDamage, Entity projectile) {
		float damage = baseDamage * weaponDamage();
		if (damage > 0f || baseDamage <= 0f) {
			// A weapon that does no damage of its own is left exactly as before.
			return target.hurtServer(level, source, damage);
		}
		if (target instanceof LivingEntity living && projectile != null && !source.is(net.minecraft.tags.DamageTypeTags.NO_KNOCKBACK)) {
			knockBackOnly(living, projectile);
		}
		return false;
	}

	/** Vanilla's projectile knockback (LivingEntity.hurtServer() with Projectile.calculateHorizontalHurtKnockbackDirection()), without the damage. */
	public static void knockBackOnly(LivingEntity target, Entity projectile) {
		Vec3 motion = projectile.getDeltaMovement();
		double x = motion.x;
		double z = motion.z;
		if (x * x + z * z < 1.0E-8) {
			x = target.getX() - projectile.getX();
			z = target.getZ() - projectile.getZ();
		}
		if (x * x + z * z < 1.0E-8) {
			return;
		}
		// knockback() pushes AWAY from the point (x, z) is the direction to: the projectile travels toward the target, so the push follows its motion.
		target.knockback(0.4, -x, -z);
		target.hurtMarked = true;
	}

	/** Explodes as Level.explode(source, x, y, z, radius, fire, interaction) does, with the server's blast and block destruction multipliers applied: entity damage scaled (none at 0 - entities are still pushed), the radius within which blocks are broken and fire is set scaled (none at 0). The radius itself - how far the explosion reaches entities, and its sound and particles - stays as given. */
	public static void explode(Level level, Entity source, double x, double y, double z, float radius, boolean fire, Level.ExplosionInteraction interaction) {
		float block = explosionBlock();
		if (block <= 0f) {
			fire = false;
			interaction = Level.ExplosionInteraction.NONE;
		}
		level.explode(source, null, new Calculator(source, explosionBlast(), block), x, y, z, radius, fire, interaction);
	}

	/** The block destruction multiplier an explosion is using: its Calculator's, or 1 for any other explosion (mixin.ScaledExplosionMixin). */
	public static float blockScaleOf(ExplosionDamageCalculator calculator) {
		return calculator instanceof Calculator scaled ? scaled.blockScale : 1f;
	}

	/** The damage calculator of an explosion made by {@link #explode}: the same as vanilla's for an explosion with a source entity (EntityBasedExplosionDamageCalculator), with the entity damage scaled. */
	public static final class Calculator extends ExplosionDamageCalculator {

		private final ExplosionDamageCalculator base;
		private final float blastScale;
		private final float blockScale;

		public Calculator(Entity source, float blastScale, float blockScale) {
			this.base = source != null ? new EntityBasedExplosionDamageCalculator(source) : new ExplosionDamageCalculator();
			this.blastScale = blastScale;
			this.blockScale = blockScale;
		}

		@Override
		public Optional<Float> getBlockExplosionResistance(Explosion explosion, BlockGetter level, BlockPos pos, BlockState state, FluidState fluid) {
			return this.base.getBlockExplosionResistance(explosion, level, pos, state, fluid);
		}

		@Override
		public boolean shouldBlockExplode(Explosion explosion, BlockGetter level, BlockPos pos, BlockState state, float power) {
			return this.base.shouldBlockExplode(explosion, level, pos, state, power);
		}

		/** At 0 no damage: vanilla still pushes an entity it doesn't damage (ServerExplosion.hurtEntities()). */
		@Override
		public boolean shouldDamageEntity(Explosion explosion, Entity entity) {
			return this.blastScale > 0f && this.base.shouldDamageEntity(explosion, entity);
		}

		@Override
		public float getEntityDamageAmount(Explosion explosion, Entity entity, float exposure) {
			return this.base.getEntityDamageAmount(explosion, entity, exposure) * this.blastScale;
		}

		@Override
		public float getKnockbackMultiplier(Entity entity) {
			return this.base.getKnockbackMultiplier(entity);
		}
	}
}
