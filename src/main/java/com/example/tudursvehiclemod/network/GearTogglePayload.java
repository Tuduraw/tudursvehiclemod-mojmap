package com.example.tudursvehiclemod.network;

import com.example.tudursvehiclemod.VehicleMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client -> server: "manually flip my vehicle's landing gear now". */
public record GearTogglePayload() implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<GearTogglePayload> ID =
			new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(VehicleMod.MOD_ID, "gear_toggle"));

	public static final StreamCodec<RegistryFriendlyByteBuf, GearTogglePayload> CODEC =
			StreamCodec.unit(new GearTogglePayload());

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return ID;
	}
}
