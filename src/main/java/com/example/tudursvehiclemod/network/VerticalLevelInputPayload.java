package com.example.tudursvehiclemod.network;

import com.example.tudursvehiclemod.VehicleMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client -> server: "my level-ascend/level-descend keys (arrow up/down) are
 * currently held/released" - see SubmarineEntity's own doc for what these do
 * (move vertically in world space while keeping the current pitch, unlike
 * Space/Ctrl which change pitch itself). */
public record VerticalLevelInputPayload(boolean ascending, boolean descending) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<VerticalLevelInputPayload> ID =
			new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(VehicleMod.MOD_ID, "vertical_level_input"));

	public static final StreamCodec<RegistryFriendlyByteBuf, VerticalLevelInputPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.BOOL, VerticalLevelInputPayload::ascending,
			ByteBufCodecs.BOOL, VerticalLevelInputPayload::descending,
			VerticalLevelInputPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return ID;
	}
}
