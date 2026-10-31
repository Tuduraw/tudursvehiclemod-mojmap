package com.example.tudursvehiclemod.network;

import com.example.tudursvehiclemod.VehicleMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client -> server: "toggle this vehicle's own search light(s) on/off" - see AbstractVehicleEntity's own tudursvehiclemod$isSearchLightOn() doc. Mirrors ToggleWeaponModePayload's own shape exactly (an empty marker payload; the vehicle being acted on is resolved server-side from the sending player's own current vehicle, same as every other per-vehicle toggle here). */
public record ToggleSearchLightPayload() implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<ToggleSearchLightPayload> ID =
			new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(VehicleMod.MOD_ID, "toggle_search_light"));

	public static final StreamCodec<RegistryFriendlyByteBuf, ToggleSearchLightPayload> CODEC =
			StreamCodec.unit(new ToggleSearchLightPayload());

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return ID;
	}
}
