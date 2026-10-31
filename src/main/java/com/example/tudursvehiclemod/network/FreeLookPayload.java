package com.example.tudursvehiclemod.network;

import com.example.tudursvehiclemod.VehicleMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client -> server: "my free-look key is currently held/released". Sent once on change (not every tick). */
public record FreeLookPayload(boolean freeLook) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<FreeLookPayload> ID =
			new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(VehicleMod.MOD_ID, "free_look"));

	public static final StreamCodec<RegistryFriendlyByteBuf, FreeLookPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.BOOL, FreeLookPayload::freeLook,
			FreeLookPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return ID;
	}
}
