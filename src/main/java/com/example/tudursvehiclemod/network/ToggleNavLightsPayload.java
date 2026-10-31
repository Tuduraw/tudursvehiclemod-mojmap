package com.example.tudursvehiclemod.network;

import com.example.tudursvehiclemod.VehicleMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client -> server: "toggle this vehicle's own nav light(s) on/off" - see AbstractVehicleEntity's own tudursvehiclemod$areNavLightsOn() doc. Mirrors ToggleSearchLightPayload's own shape exactly (an empty marker payload; the vehicle being acted on is resolved server-side from the sending player's own current vehicle, same as every other per-vehicle toggle here). */
public record ToggleNavLightsPayload() implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<ToggleNavLightsPayload> ID =
			new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(VehicleMod.MOD_ID, "toggle_nav_lights"));

	public static final StreamCodec<RegistryFriendlyByteBuf, ToggleNavLightsPayload> CODEC =
			StreamCodec.unit(new ToggleNavLightsPayload());

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return ID;
	}
}
