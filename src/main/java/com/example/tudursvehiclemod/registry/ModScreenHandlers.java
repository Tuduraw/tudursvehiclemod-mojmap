package com.example.tudursvehiclemod.registry;

import com.example.tudursvehiclemod.VehicleMod;
import com.example.tudursvehiclemod.screen.FuelRefinerScreenHandler;
import com.example.tudursvehiclemod.screen.VehicleMenuScreenHandler;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.flag.FeatureFlagSet;
import net.minecraft.world.inventory.MenuType;

/** ScreenHandlerType's own constructor is private in vanilla, but Fabric Loom's own transitive access widener (bundled with Fabric API) widens it to be callable.. */
public class ModScreenHandlers {

	public static MenuType<FuelRefinerScreenHandler> FUEL_REFINER;
	public static MenuType<VehicleMenuScreenHandler> VEHICLE_MENU;
	public static MenuType<com.example.tudursvehiclemod.screen.DroneCenterScreenHandler> DRONE_CENTER;
	public static MenuType<com.example.tudursvehiclemod.screen.DroneCenterFormationScreenHandler> DRONE_CENTER_FORMATION;
	public static MenuType<com.example.tudursvehiclemod.screen.StationScreenHandler> STATION;
	public static MenuType<com.example.tudursvehiclemod.screen.VehicleConverterScreenHandler> VEHICLE_CONVERTER;
	public static MenuType<com.example.tudursvehiclemod.screen.FireSupportScreenHandler> FIRE_SUPPORT_CENTER;

	public static void register() {
		FUEL_REFINER = Registry.register(
				BuiltInRegistries.MENU,
				Identifier.fromNamespaceAndPath(VehicleMod.MOD_ID, "fuel_refiner"),
				new MenuType<>(FuelRefinerScreenHandler::new, FeatureFlagSet.of()));

		VEHICLE_MENU = Registry.register(
				BuiltInRegistries.MENU,
				Identifier.fromNamespaceAndPath(VehicleMod.MOD_ID, "vehicle_menu"),
				new MenuType<>(VehicleMenuScreenHandler::new, FeatureFlagSet.of()));

		DRONE_CENTER = Registry.register(
				BuiltInRegistries.MENU,
				Identifier.fromNamespaceAndPath(VehicleMod.MOD_ID, "drone_center"),
				new MenuType<>(com.example.tudursvehiclemod.screen.DroneCenterScreenHandler::new, FeatureFlagSet.of()));

		DRONE_CENTER_FORMATION = Registry.register(
				BuiltInRegistries.MENU,
				Identifier.fromNamespaceAndPath(VehicleMod.MOD_ID, "drone_center_formation"),
				new MenuType<>(com.example.tudursvehiclemod.screen.DroneCenterFormationScreenHandler::new, FeatureFlagSet.of()));

		STATION = Registry.register(
				BuiltInRegistries.MENU,
				Identifier.fromNamespaceAndPath(VehicleMod.MOD_ID, "station"),
				new MenuType<>(com.example.tudursvehiclemod.screen.StationScreenHandler::new, FeatureFlagSet.of()));

		VEHICLE_CONVERTER = Registry.register(
				BuiltInRegistries.MENU,
				Identifier.fromNamespaceAndPath(VehicleMod.MOD_ID, "vehicle_converter"),
				new MenuType<>(com.example.tudursvehiclemod.screen.VehicleConverterScreenHandler::new, FeatureFlagSet.of()));

		FIRE_SUPPORT_CENTER = Registry.register(
				BuiltInRegistries.MENU,
				Identifier.fromNamespaceAndPath(VehicleMod.MOD_ID, "fire_support_center"),
				new MenuType<>(com.example.tudursvehiclemod.screen.FireSupportScreenHandler::new, FeatureFlagSet.of()));
	}
}
