package com.example.tudursvehiclemod.item;

import com.example.tudursvehiclemod.VehicleMod;
import net.fabricmc.fabric.api.itemgroup.v1.FabricItemGroup;
import net.fabricmc.fabric.api.itemgroup.v1.ItemGroupEvents;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** A dedicated creative tab listing all 25 tiered spawner items (5 categories x 5 tiers). */
public class ModItemGroups {

	public static final ResourceKey<CreativeModeTab> VEHICLES =
			ResourceKey.create(Registries.CREATIVE_MODE_TAB, Identifier.fromNamespaceAndPath(VehicleMod.MOD_ID, "vehicles"));

	public static void register() {
		Registry.register(BuiltInRegistries.CREATIVE_MODE_TAB, VEHICLES, FabricItemGroup.builder()
				.icon(() -> new ItemStack(ModItems.TIERED_SPAWNERS.get(VehicleCategory.CAR)[0]))
				.title(Component.translatable("itemGroup.tudursvehiclemod.vehicles"))
				.build());

		ItemGroupEvents.modifyEntriesEvent(VEHICLES).register(entries -> {
			for (VehicleCategory category : VehicleCategory.values()) {
				for (Item item : ModItems.TIERED_SPAWNERS.get(category)) {
					entries.accept(item);
				}
			}
			for (Item item : ModItems.TIERED_BASE_ITEMS) {
				entries.accept(item);
			}
			entries.accept(ModItems.FUEL_CAN);
			entries.accept(ModItems.CREATIVE_FUEL_CAN);
			entries.accept(com.example.tudursvehiclemod.registry.ModBlocks.FUEL_REFINER);
			// These two were missing from this
			// manual list entirely - see item.DroneControlStickItem's/
			// block.StationBlock's own doc.
			entries.accept(ModItems.DRONE_CONTROL_STICK);
			entries.accept(ModItems.DRONE_ROUTE_BOOK);
			entries.accept(ModItems.PARACHUTE);
			entries.accept(com.example.tudursvehiclemod.registry.ModBlocks.STATION);
			entries.accept(com.example.tudursvehiclemod.registry.ModBlocks.DRONE_CENTER);
			entries.accept(com.example.tudursvehiclemod.registry.ModBlocks.VEHICLE_CONVERTER);
			entries.accept(com.example.tudursvehiclemod.registry.ModBlocks.FIRE_SUPPORT_CENTER);
			entries.accept(ModItems.FIRE_SUPPORT_DESIGNATOR);
		});
	}
}
