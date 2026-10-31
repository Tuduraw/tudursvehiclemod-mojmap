package com.example.tudursvehiclemod.network;

import com.example.tudursvehiclemod.VehicleMod;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server -> client: everything the Fire Support Center screen at pos shows (FireSupportCenterBlockEntity.tudursvehiclemod$encodeState()). Sent when the screen opens and then every few ticks while it stays open. */
public record FireSupportStatePayload(BlockPos pos, CompoundTag state) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<FireSupportStatePayload> ID =
			new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(VehicleMod.MOD_ID, "fire_support_state"));

	public static final StreamCodec<RegistryFriendlyByteBuf, FireSupportStatePayload> CODEC = StreamCodec.composite(
			BlockPos.STREAM_CODEC, FireSupportStatePayload::pos,
			ByteBufCodecs.COMPOUND_TAG, FireSupportStatePayload::state,
			FireSupportStatePayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return ID;
	}
}
