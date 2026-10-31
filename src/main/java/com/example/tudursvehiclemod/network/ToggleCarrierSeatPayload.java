package com.example.tudursvehiclemod.network;

import com.example.tudursvehiclemod.VehicleMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client -> server: "switch seats" between a Carrier weapon's own mothership seat and whichever aircraft it most recently launched from that seat - see entity.AbstractVehicleEntity's own tudursvehiclemod$toggleCarrierSeat() doc for the full server-side logic. Sent when the player presses the dedicated Alt+Y combo (see client.VehicleModClient's own carrierSeatSwitchKey doc). No payload data needed - the server determines the correct direction (mothership->aircraft or aircraft->mothership) from whichever vehicle the player is CURRENTLY riding. */
public record ToggleCarrierSeatPayload() implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<ToggleCarrierSeatPayload> ID =
			new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(VehicleMod.MOD_ID, "toggle_carrier_seat"));

	public static final StreamCodec<RegistryFriendlyByteBuf, ToggleCarrierSeatPayload> CODEC =
			StreamCodec.unit(new ToggleCarrierSeatPayload());

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return ID;
	}
}
