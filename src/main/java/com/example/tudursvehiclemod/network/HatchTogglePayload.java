package com.example.tudursvehiclemod.network;

import com.example.tudursvehiclemod.VehicleMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client -> server: "toggle my vehicle's hatch/canopy now". */
public record HatchTogglePayload() implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<HatchTogglePayload> ID =
			new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(VehicleMod.MOD_ID, "hatch_toggle"));

	public static final StreamCodec<RegistryFriendlyByteBuf, HatchTogglePayload> CODEC =
			StreamCodec.unit(new HatchTogglePayload());

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return ID;
	}
}
