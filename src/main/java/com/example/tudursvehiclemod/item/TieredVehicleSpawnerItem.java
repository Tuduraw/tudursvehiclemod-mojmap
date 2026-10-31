package com.example.tudursvehiclemod.item;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** One of these exists per (converter target, tier 1-5) pair.
 *
 * Typed against VehicleConverterTarget rather than the VehicleCategory enum directly, so an ADDON
 * MOD can reuse this item - and the vehicle selection screen it opens - for its own vehicle kinds.
 * VehicleCategory implements that interface, so every built-in item is constructed exactly as before. */
public class TieredVehicleSpawnerItem extends Item {

	/** Set by VehicleModClient at client startup. */
	public static VehicleSelectScreenOpener screenOpener;

	private final VehicleConverterTarget category;
	private final int tier;

	public TieredVehicleSpawnerItem(Properties settings, VehicleConverterTarget category, int tier) {
		super(settings);
		this.category = category;
		this.tier = tier;
	}

	public VehicleConverterTarget getCategory() {
		return category;
	}

	public int getTier() {
		return tier;
	}

	@Override
	public InteractionResult use(Level world, Player user, InteractionHand hand) {
		if (world.isClientSide() && screenOpener != null) {
			screenOpener.open(category, tier);
		}
		return InteractionResult.SUCCESS;
	}

	/** Called (server-side) once the player has actually picked a vehicle from the screen. */
	public static void consumeOne(Player player, InteractionHand hand) {
		if (!player.isCreative()) {
			ItemStack stack = player.getItemInHand(hand);
			stack.shrink(1);
		}
	}
}
