package com.example.tudursvehiclemod.network;

import com.example.tudursvehiclemod.VehicleMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client -> server: "please send me the current GROUND route for the Drone Center at (x,y,z) so I can open its detailed settings screen" - the surface-vehicle counterpart to DroneWaypointsRequestPayload (see that payload's own doc for the same round-trip reasoning). which of the two request payloads the simple config screen actually sends is decided server-side, from the bound vehicle's own type (see block.DroneCenterBlockEntity's own tudursvehiclemod$usesGroundRoute() doc). */
public record GroundWaypointsRequestPayload(int x, int y, int z) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<GroundWaypointsRequestPayload> ID =
			new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(VehicleMod.MOD_ID, "ground_waypoints_request"));

	public static final StreamCodec<RegistryFriendlyByteBuf, GroundWaypointsRequestPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, GroundWaypointsRequestPayload::x,
			ByteBufCodecs.VAR_INT, GroundWaypointsRequestPayload::y,
			ByteBufCodecs.VAR_INT, GroundWaypointsRequestPayload::z,
			GroundWaypointsRequestPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return ID;
	}
}
