package com.example.tudursvehiclemod.network;

import com.example.tudursvehiclemod.VehicleMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client -> server: "toggle my VTOL between helicopter and aircraft mode now". */
public record VtolModeTogglePayload() implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<VtolModeTogglePayload> ID =
			new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(VehicleMod.MOD_ID, "vtol_mode_toggle"));

	public static final StreamCodec<RegistryFriendlyByteBuf, VtolModeTogglePayload> CODEC =
			StreamCodec.unit(new VtolModeTogglePayload());

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return ID;
	}
}
