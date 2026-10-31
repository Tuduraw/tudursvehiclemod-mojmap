package com.example.tudursvehiclemod.network;

import com.example.tudursvehiclemod.VehicleMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client -> server: "toggle drone route recording mode" - sent when the player presses the dedicated recording-toggle key (see client.VehicleModClient's own droneRecordingToggleKey doc). recording mode is only actually usable while the player has a DroneRouteBookItem in their own OFFHAND (validated server-side, not here) - while active, every 100 ticks (5 seconds) this records the player's own currently-ridden vehicle's own position/speed/roll into that same offhand book (see server.DroneRecordingManager's own doc for the full tick-by-tick logic), automatically stopping if the key is pressed again OR the book leaves the offhand. No payload data needed at all - this is purely a toggle request, all the actual state (who's recording, when the next record is due) lives server-side. */
public record ToggleDroneRecordingPayload() implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<ToggleDroneRecordingPayload> ID =
			new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(VehicleMod.MOD_ID, "toggle_drone_recording"));

	public static final StreamCodec<RegistryFriendlyByteBuf, ToggleDroneRecordingPayload> CODEC =
			StreamCodec.unit(new ToggleDroneRecordingPayload());

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return ID;
	}
}
