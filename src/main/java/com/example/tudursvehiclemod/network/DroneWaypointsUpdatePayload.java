package com.example.tudursvehiclemod.network;

import com.example.tudursvehiclemod.VehicleMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client -> server: "update the Drone Center at (x,y,z)'s own patrol route, Home Point, and return via-point to this" - sent whenever the player adds/removes/edits/records a waypoint, the Home Point, or the return via-point in the detailed settings screen. Always sends everything (not just whichever one changed), same "resend everything" reasoning as DroneCenterConfigUpdatePayload's own doc. encodedViaPoint may be empty (meaning "no via-point configured"), unlike encodedHomePoint which is always non-empty. */
public record DroneWaypointsUpdatePayload(int x, int y, int z, String encodedWaypoints, String encodedHomePoint, String encodedViaPoint) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<DroneWaypointsUpdatePayload> ID =
			new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(VehicleMod.MOD_ID, "drone_waypoints_update"));

	public static final StreamCodec<RegistryFriendlyByteBuf, DroneWaypointsUpdatePayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, DroneWaypointsUpdatePayload::x,
			ByteBufCodecs.VAR_INT, DroneWaypointsUpdatePayload::y,
			ByteBufCodecs.VAR_INT, DroneWaypointsUpdatePayload::z,
			ByteBufCodecs.STRING_UTF8, DroneWaypointsUpdatePayload::encodedWaypoints,
			ByteBufCodecs.STRING_UTF8, DroneWaypointsUpdatePayload::encodedHomePoint,
			ByteBufCodecs.STRING_UTF8, DroneWaypointsUpdatePayload::encodedViaPoint,
			DroneWaypointsUpdatePayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return ID;
	}
}
