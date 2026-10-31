package com.example.tudursvehiclemod.network;

import com.example.tudursvehiclemod.VehicleMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client -> server: "open the item slot container UI for the block at this position" - sent from a button inside block.DroneCenterBlockEntity's own config screen or block.StationBlockEntity's own menu screen (see each block's own doc). the server re-validates that the block at this position still actually is one of this project's own slot-bearing block entities before opening anything. */
public record OpenBlockSlotsPayload(int x, int y, int z) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<OpenBlockSlotsPayload> ID =
			new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(VehicleMod.MOD_ID, "open_block_slots"));

	public static final StreamCodec<RegistryFriendlyByteBuf, OpenBlockSlotsPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.INT, OpenBlockSlotsPayload::x,
			ByteBufCodecs.INT, OpenBlockSlotsPayload::y,
			ByteBufCodecs.INT, OpenBlockSlotsPayload::z,
			OpenBlockSlotsPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return ID;
	}
}
