package com.example.tudursvehiclemod.registry;

import com.example.tudursvehiclemod.VehicleMod;
import com.example.tudursvehiclemod.block.FuelRefinerBlock;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

public class ModBlocks {

	public static Block FUEL_REFINER;
	public static Block STATION;
	public static Block DRONE_CENTER;
	public static Block VEHICLE_CONVERTER;
	public static Block FIRE_SUPPORT_CENTER;

	public static void register() {
		ResourceKey<Block> blockKey = ResourceKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(VehicleMod.MOD_ID, "fuel_refiner"));
		FUEL_REFINER = Registry.register(BuiltInRegistries.BLOCK, blockKey,
				new FuelRefinerBlock(BlockBehaviour.Properties.of()
						.setId(blockKey)
						.mapColor(MapColor.METAL)
						.strength(3.5f)
						.requiresCorrectToolForDrops()));

		ResourceKey<Item> itemKey = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(VehicleMod.MOD_ID, "fuel_refiner"));
		Registry.register(BuiltInRegistries.ITEM, itemKey,
				new BlockItem(FUEL_REFINER, new Item.Properties().setId(itemKey).useBlockDescriptionPrefix()));

		// This project's own UAV control station - see block.StationBlock's own doc.
		ResourceKey<Block> stationKey = ResourceKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(VehicleMod.MOD_ID, "station"));
		STATION = Registry.register(BuiltInRegistries.BLOCK, stationKey,
				new com.example.tudursvehiclemod.block.StationBlock(BlockBehaviour.Properties.of()
						.setId(stationKey)
						.mapColor(MapColor.METAL)
						.strength(4.0f)
						.requiresCorrectToolForDrops()));

		ResourceKey<Item> stationItemKey = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(VehicleMod.MOD_ID, "station"));
		Registry.register(BuiltInRegistries.ITEM, stationItemKey,
				new BlockItem(STATION, new Item.Properties().setId(stationItemKey).useBlockDescriptionPrefix()));

		// This project's own target drone control center - see block.DroneCenterBlock's own doc.
		ResourceKey<Block> droneCenterKey = ResourceKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(VehicleMod.MOD_ID, "drone_center"));
		DRONE_CENTER = Registry.register(BuiltInRegistries.BLOCK, droneCenterKey,
				new com.example.tudursvehiclemod.block.DroneCenterBlock(BlockBehaviour.Properties.of()
						.setId(droneCenterKey)
						.mapColor(MapColor.METAL)
						.strength(4.0f)
						.requiresCorrectToolForDrops()));

		ResourceKey<Item> droneCenterItemKey = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(VehicleMod.MOD_ID, "drone_center"));
		Registry.register(BuiltInRegistries.ITEM, droneCenterItemKey,
				new BlockItem(DRONE_CENTER, new Item.Properties().setId(droneCenterItemKey).useBlockDescriptionPrefix()));

		// Converts a tier-based base item into a specific vehicle-category spawner item - see block.VehicleConverterBlockEntity's own doc.
		ResourceKey<Block> vehicleConverterKey = ResourceKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(VehicleMod.MOD_ID, "vehicle_converter"));
		VEHICLE_CONVERTER = Registry.register(BuiltInRegistries.BLOCK, vehicleConverterKey,
				new com.example.tudursvehiclemod.block.VehicleConverterBlock(BlockBehaviour.Properties.of()
						.setId(vehicleConverterKey)
						.mapColor(MapColor.METAL)
						.strength(3.5f)
						.requiresCorrectToolForDrops()));

		ResourceKey<Item> vehicleConverterItemKey = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(VehicleMod.MOD_ID, "vehicle_converter"));
		Registry.register(BuiltInRegistries.ITEM, vehicleConverterItemKey,
				new BlockItem(VEHICLE_CONVERTER, new Item.Properties().setId(vehicleConverterItemKey).useBlockDescriptionPrefix()));

		// Lays linked vehicles' guns on a target point - see block.FireSupportCenterBlockEntity's own doc.
		ResourceKey<Block> fireSupportCenterKey = ResourceKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath(VehicleMod.MOD_ID, "fire_support_center"));
		FIRE_SUPPORT_CENTER = Registry.register(BuiltInRegistries.BLOCK, fireSupportCenterKey,
				new com.example.tudursvehiclemod.block.FireSupportCenterBlock(BlockBehaviour.Properties.of()
						.setId(fireSupportCenterKey)
						.mapColor(MapColor.METAL)
						.strength(4.0f)
						.requiresCorrectToolForDrops()));

		ResourceKey<Item> fireSupportCenterItemKey = ResourceKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath(VehicleMod.MOD_ID, "fire_support_center"));
		Registry.register(BuiltInRegistries.ITEM, fireSupportCenterItemKey,
				new BlockItem(FIRE_SUPPORT_CENTER, new Item.Properties().setId(fireSupportCenterItemKey).useBlockDescriptionPrefix()));
	}
}
