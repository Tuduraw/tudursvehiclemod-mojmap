package com.example.tudursvehiclemod.entity.projectile;

import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;

/** Events an addon mod can listen to for this mod's projectiles. Server side. */
public final class VehicleProjectileEvents {

	private VehicleProjectileEvents() {
	}

	/** Asked when a projectile is about to explode, and when it is about to break through a block: return false to keep it from changing the world - its explosion then breaks no blocks and starts no fires, and it doesn't pierce or break the block it struck. Damage to entities and vehicles is not affected. One listener returning false is enough.
	 *
	 * <p>For rules such as "shots fired by AI-controlled vehicles don't destroy terrain": projectile.tudursvehiclemod$getFiringVehicle() is the vehicle that fired it (null for one fired by something else). */
	public static final Event<AllowBlockDamage> ALLOW_BLOCK_DAMAGE = EventFactory.createArrayBacked(AllowBlockDamage.class,
			listeners -> projectile -> {
				for (AllowBlockDamage listener : listeners) {
					if (!listener.allowBlockDamage(projectile)) {
						return false;
					}
				}
				return true;
			});

	@FunctionalInterface
	public interface AllowBlockDamage {
		boolean allowBlockDamage(VehicleProjectileEntity projectile);
	}
}
