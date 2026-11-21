package com.example.tudursvehiclemod.network;

import com.example.tudursvehiclemod.VehicleMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client -> server: "switch my aircraft into or out of standby mode" (AircraftEntity.tudursvehiclemod$toggleStandby(), Alt+G by default). */
public record StandbyTogglePayload() implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<StandbyTogglePayload> ID =
			new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(VehicleMod.MOD_ID, "standby_toggle"));

	public static final StreamCodec<RegistryFriendlyByteBuf, StandbyTogglePayload> CODEC =
			StreamCodec.unit(new StandbyTogglePayload());

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return ID;
	}
}
