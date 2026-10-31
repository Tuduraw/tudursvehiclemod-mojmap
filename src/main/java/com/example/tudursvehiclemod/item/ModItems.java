package com.example.tudursvehiclemod.item;

import com.example.tudursvehiclemod.VehicleMod;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;

public class ModItems {

	/** [category][tier-1]. */
	public static final Map<VehicleCategory, Item[]> TIERED_SPAWNERS = new EnumMap<>(VehicleCategory.class);
	/** [tier-1], no VehicleCategory of its own - see item.TieredVehicleBaseItem's own doc. */
	public static final Item[] TIERED_BASE_ITEMS = new Item[5];
	public static Item FUEL_CAN;
	public static Item CREATIVE_FUEL_CAN;
	public static Item DRONE_CONTROL_STICK;
	public static Item DRONE_ROUTE_BOOK;
	public static Item PARACHUTE;
	public static Item FIRE_SUPPORT_DESIGNATOR;

	public static void register() {
		ResourceKey<Item> fuelCanKey = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(VehicleMod.MOD_ID, "fuel_can"));
		FUEL_CAN = Registry.register(BuiltInRegistries.ITEM, fuelCanKey,
				new com.example.tudursvehiclemod.item.FuelCanItem(new Item.Properties().setId(fuelCanKey).stacksTo(1)));

		// Deliberately no recipe at all.
		ResourceKey<Item> creativeFuelCanKey =
				ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(VehicleMod.MOD_ID, "creative_fuel_can"));
		CREATIVE_FUEL_CAN = Registry.register(BuiltInRegistries.ITEM, creativeFuelCanKey,
				new com.example.tudursvehiclemod.item.CreativeFuelCanItem(
						new Item.Properties().setId(creativeFuelCanKey).stacksTo(1)));

		// The dedicated stick that registers/binds a UAV vehicle to a StationBlock - see DroneControlStickItem's own doc. maxCount(1) - it isn't meaningfully stackable (each stack can only ever be registered to ONE vehicle at a time).
		ResourceKey<Item> droneControlStickKey =
				ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(VehicleMod.MOD_ID, "drone_control_stick"));
		DRONE_CONTROL_STICK = Registry.register(BuiltInRegistries.ITEM, droneControlStickKey,
				new com.example.tudursvehiclemod.item.DroneControlStickItem(
						new Item.Properties().setId(droneControlStickKey).stacksTo(1)));

		// A dedicated waypoint-recording tool for a Drone Center's own patrol route - see DroneRouteBookItem's own doc. maxCount(1), same reasoning as the control stick just above (each stack holds its own distinct recorded route).
		ResourceKey<Item> droneRouteBookKey =
				ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(VehicleMod.MOD_ID, "drone_route_book"));
		DRONE_ROUTE_BOOK = Registry.register(BuiltInRegistries.ITEM, droneRouteBookKey,
				new com.example.tudursvehiclemod.item.DroneRouteBookItem(
						new Item.Properties().setId(droneRouteBookKey).stacksTo(1)));

		// Equippable in the chest slot and dyeable like leather armor (default white, DyedColorComponent's own default meaning is "undyed" but this project's own item model always applies SOME tint, so an explicit default of white - 0xFFFFFF - is set here rather than leaving it to whatever DyedColorComponent's own DEFAULT_COLOR happens to be, matching "デフォルトは白" exactly). See ParachuteItem's own doc for why it's equippable/dyeable but otherwise has no behavior of its own.
		ResourceKey<Item> parachuteKey = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(VehicleMod.MOD_ID, "parachute"));
		PARACHUTE = Registry.register(BuiltInRegistries.ITEM, parachuteKey,
				new com.example.tudursvehiclemod.item.ParachuteItem(new Item.Properties().setId(parachuteKey).stacksTo(1)
						.equippable(net.minecraft.world.entity.EquipmentSlot.CHEST)
						.component(net.minecraft.core.component.DataComponents.DYED_COLOR,
								new net.minecraft.world.item.component.DyedItemColor(0xFFFFFF))));

		// Remote handset for a Fire Support Center - see FireSupportDesignatorItem's own doc.
		ResourceKey<Item> fireSupportDesignatorKey =
				ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(VehicleMod.MOD_ID, "fire_support_designator"));
		FIRE_SUPPORT_DESIGNATOR = Registry.register(BuiltInRegistries.ITEM, fireSupportDesignatorKey,
				new com.example.tudursvehiclemod.item.FireSupportDesignatorItem(
						new Item.Properties().setId(fireSupportDesignatorKey).stacksTo(1)));

		for (VehicleCategory category : VehicleCategory.values()) {
			Item[] tiers = new Item[5];
			for (int tier = 1; tier <= 5; tier++) {
				String path = category.name().toLowerCase() + "_spawner_t" + tier;
				ResourceKey<Item> tierKey = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(VehicleMod.MOD_ID, path));
				Item item = new TieredVehicleSpawnerItem(
						new Item.Properties().setId(tierKey).stacksTo(1), category, tier);
				tiers[tier - 1] = Registry.register(BuiltInRegistries.ITEM, tierKey, item);
			}
			TIERED_SPAWNERS.put(category, tiers);
		}

		// One per tier, no VehicleCategory - see item.TieredVehicleBaseItem's own doc. Named "vehicle_base_tN" - distinct from any of the "<category>_spawner_tN" names above, since this isn't scoped to any one category at all.
		for (int tier = 1; tier <= 5; tier++) {
			String path = "vehicle_base_t" + tier;
			ResourceKey<Item> baseItemKey = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(VehicleMod.MOD_ID, path));
			Item item = new TieredVehicleBaseItem(new Item.Properties().setId(baseItemKey).stacksTo(64), tier);
			TIERED_BASE_ITEMS[tier - 1] = Registry.register(BuiltInRegistries.ITEM, baseItemKey, item);
		}
	}
}
