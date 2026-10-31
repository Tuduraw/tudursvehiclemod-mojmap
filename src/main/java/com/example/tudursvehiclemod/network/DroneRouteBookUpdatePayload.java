package com.example.tudursvehiclemod.network;

import com.example.tudursvehiclemod.VehicleMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client -> server: "update the route book I'm holding in this hand to this route" - sent whenever the player adds/removes/edits/records a waypoint in the drone route book screen. Always sends the full current route, same "resend everything" reasoning as this project's other drone-config payloads. */
/** Client -> server: "update the route book I'm holding in this hand to this route (and this coordinate offset)" - sent whenever the player adds/removes/edits/records a waypoint, or edits the offset, in the drone route book screen. Always sends the full current route, same "resend everything" reasoning as this project's other drone-config payloads. encodedOffset is "x,y,z" (see item.DroneRouteBookItem's own tudursvehiclemod$getOffset()/setOffset() doc) - empty string means no offset (0,0,0). */
public record DroneRouteBookUpdatePayload(boolean mainHand, String encodedWaypoints, String encodedOffset) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<DroneRouteBookUpdatePayload> ID =
			new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(VehicleMod.MOD_ID, "drone_route_book_update"));

	public static final StreamCodec<RegistryFriendlyByteBuf, DroneRouteBookUpdatePayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.BOOL, DroneRouteBookUpdatePayload::mainHand,
			ByteBufCodecs.STRING_UTF8, DroneRouteBookUpdatePayload::encodedWaypoints,
			ByteBufCodecs.STRING_UTF8, DroneRouteBookUpdatePayload::encodedOffset,
			DroneRouteBookUpdatePayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return ID;
	}
}
