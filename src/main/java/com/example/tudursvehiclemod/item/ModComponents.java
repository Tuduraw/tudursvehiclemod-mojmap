package com.example.tudursvehiclemod.item;

import com.example.tudursvehiclemod.VehicleMod;
import com.mojang.serialization.Codec;
import net.minecraft.core.Registry;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;

/** This mod's own custom ItemStack data components. */
public class ModComponents {

	/** Current stored fuel amount for a FuelCanItem stack. */
	public static final DataComponentType<Integer> FUEL_AMOUNT = Registry.register(
			BuiltInRegistries.DATA_COMPONENT_TYPE,
			Identifier.fromNamespaceAndPath(VehicleMod.MOD_ID, "fuel_amount"),
			DataComponentType.<Integer>builder().persistent(Codec.INT).build());

	/** item.DroneControlStickItem's own registered
	 * vehicle (this vehicle's own persistent UUID, NOT its entity ID -
	 * entity IDs aren't stable across a server restart/entity reload,
	 * UUIDs are) - absent entirely on a freshly-crafted, unregistered
	 * stick. */
	public static final DataComponentType<java.util.UUID> REGISTERED_VEHICLE_ID = Registry.register(
			BuiltInRegistries.DATA_COMPONENT_TYPE,
			Identifier.fromNamespaceAndPath(VehicleMod.MOD_ID, "registered_vehicle_id"),
			DataComponentType.<java.util.UUID>builder().persistent(net.minecraft.core.UUIDUtil.AUTHLIB_CODEC).build());

	/** This stack's own recorded patrol route, encoded the same way block.DroneCenterBlockEntity's own internal route is (see block.DroneRouteBookWaypoint's own tudursvehiclemod$encode()/decode() doc) - absent entirely on a freshly-crafted, empty book. */
	public static final DataComponentType<String> DRONE_ROUTE_WAYPOINTS = Registry.register(
			BuiltInRegistries.DATA_COMPONENT_TYPE,
			Identifier.fromNamespaceAndPath(VehicleMod.MOD_ID, "drone_route_waypoints"),
			DataComponentType.<String>builder().persistent(Codec.STRING).build());

	/** This stack's own coordinate offset ("x,y,z", each an int) - added to every recorded waypoint's own absolute coordinates once this book is actually read by a Drone Center (see item.DroneRouteBookItem's own tudursvehiclemod$getOffset()/setOffset() doc), letting the SAME recorded route be reused at a shifted position without re-recording it. Absent entirely (treated as 0,0,0 - no shift at all) on a freshly-crafted book. */
	public static final DataComponentType<String> DRONE_ROUTE_OFFSET = Registry.register(
			BuiltInRegistries.DATA_COMPONENT_TYPE,
			Identifier.fromNamespaceAndPath(VehicleMod.MOD_ID, "drone_route_offset"),
			DataComponentType.<String>builder().persistent(Codec.STRING).build());

	/** The Fire Support Center a FireSupportDesignatorItem is linked to (dimension and position) - absent until linked. */
	public static final DataComponentType<net.minecraft.core.GlobalPos> FIRE_SUPPORT_LINK = Registry.register(
			BuiltInRegistries.DATA_COMPONENT_TYPE,
			Identifier.fromNamespaceAndPath(VehicleMod.MOD_ID, "fire_support_link"),
			DataComponentType.<net.minecraft.core.GlobalPos>builder().persistent(net.minecraft.core.GlobalPos.CODEC)
					.networkSynchronized(net.minecraft.core.GlobalPos.STREAM_CODEC).build());

	public static void register() {
		// Referencing FUEL_AMOUNT above is enough to trigger its own static init/registration.
	}
}
