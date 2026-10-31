package com.example.tudursvehiclemod.network;

import com.example.tudursvehiclemod.VehicleMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server -> client: "open your own Drone Center detailed (waypoint route) settings screen for the block at (x,y,z), currently configured with this route, this Home Point, and this return via-point" - sent when a player presses "Detailed Settings.." on the simple config screen. The route/Home Point/via-point are all sent as single encoded strings (see block.DroneWaypoint's own tudursvehiclemod$encode()/decode() doc) rather than as proper record fields, since this project's own network payloads elsewhere only use simple scalar codec types (int/float/boolean) - encoding each as one string reuses that same simple approach instead of introducing a new, unverified list-codec pattern. encodedViaPoint may be empty (meaning "no via-point configured"), unlike encodedHomePoint which is always non-empty. */
public record DroneWaypointsOpenPayload(int x, int y, int z, String encodedWaypoints, String encodedHomePoint, String encodedViaPoint) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<DroneWaypointsOpenPayload> ID =
			new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(VehicleMod.MOD_ID, "drone_waypoints_open"));

	public static final StreamCodec<RegistryFriendlyByteBuf, DroneWaypointsOpenPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, DroneWaypointsOpenPayload::x,
			ByteBufCodecs.VAR_INT, DroneWaypointsOpenPayload::y,
			ByteBufCodecs.VAR_INT, DroneWaypointsOpenPayload::z,
			ByteBufCodecs.STRING_UTF8, DroneWaypointsOpenPayload::encodedWaypoints,
			ByteBufCodecs.STRING_UTF8, DroneWaypointsOpenPayload::encodedHomePoint,
			ByteBufCodecs.STRING_UTF8, DroneWaypointsOpenPayload::encodedViaPoint,
			DroneWaypointsOpenPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return ID;
	}
}
