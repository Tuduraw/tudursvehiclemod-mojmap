package com.example.tudursvehiclemod.network;

import com.example.tudursvehiclemod.VehicleMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client -> server: "my dedicated descend key is currently held/released". */
public record DescendPayload(boolean descending) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<DescendPayload> ID =
			new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(VehicleMod.MOD_ID, "descend"));

	public static final StreamCodec<RegistryFriendlyByteBuf, DescendPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.BOOL, DescendPayload::descending,
			DescendPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return ID;
	}
}
