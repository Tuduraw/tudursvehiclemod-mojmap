package com.example.tudursvehiclemod.network;

import com.example.tudursvehiclemod.VehicleMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client -> server: "deploy this vehicle's own flares/countermeasures now" - disables the guidance of every missile currently homing on this vehicle. Mirrors ToggleSearchLightPayload's own shape exactly (an empty marker payload; the vehicle being acted on is resolved server-side from the sending player's own current vehicle). */
public record DeployFlaresPayload() implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<DeployFlaresPayload> ID =
			new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(VehicleMod.MOD_ID, "deploy_flares"));

	public static final StreamCodec<RegistryFriendlyByteBuf, DeployFlaresPayload> CODEC =
			StreamCodec.unit(new DeployFlaresPayload());

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return ID;
	}
}
