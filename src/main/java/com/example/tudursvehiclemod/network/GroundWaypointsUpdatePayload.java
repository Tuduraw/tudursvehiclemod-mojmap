package com.example.tudursvehiclemod.network;

import com.example.tudursvehiclemod.VehicleMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client -> server: "update the Drone Center at (x,y,z)'s own GROUND route to this" - the surface-vehicle counterpart to DroneWaypointsUpdatePayload (see that payload's own doc). Always sends the full current route, same "resend everything" reasoning. */
public record GroundWaypointsUpdatePayload(int x, int y, int z, String encodedWaypoints) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<GroundWaypointsUpdatePayload> ID =
			new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(VehicleMod.MOD_ID, "ground_waypoints_update"));

	public static final StreamCodec<RegistryFriendlyByteBuf, GroundWaypointsUpdatePayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, GroundWaypointsUpdatePayload::x,
			ByteBufCodecs.VAR_INT, GroundWaypointsUpdatePayload::y,
			ByteBufCodecs.VAR_INT, GroundWaypointsUpdatePayload::z,
			ByteBufCodecs.STRING_UTF8, GroundWaypointsUpdatePayload::encodedWaypoints,
			GroundWaypointsUpdatePayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return ID;
	}
}
