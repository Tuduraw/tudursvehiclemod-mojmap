package com.example.tudursvehiclemod.network;

import com.example.tudursvehiclemod.VehicleMod;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client -> server: one action on the Fire Support Center at pos, from its screen. action is one of FireSupportCenterBlockEntity's ACTION_* codes; a, b, c its arguments (ACTION_SET_RELATIVE: x, y, z; ACTION_STEP_WAYPOINT: a = direction; ACTION_CYCLE_WEAPON: a = unit slot; the rest take none). */
public record FireSupportActionPayload(BlockPos pos, int action, int a, int b, int c) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<FireSupportActionPayload> ID =
			new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(VehicleMod.MOD_ID, "fire_support_action"));

	public static final StreamCodec<RegistryFriendlyByteBuf, FireSupportActionPayload> CODEC = StreamCodec.composite(
			BlockPos.STREAM_CODEC, FireSupportActionPayload::pos,
			ByteBufCodecs.VAR_INT, FireSupportActionPayload::action,
			ByteBufCodecs.VAR_INT, FireSupportActionPayload::a,
			ByteBufCodecs.VAR_INT, FireSupportActionPayload::b,
			ByteBufCodecs.VAR_INT, FireSupportActionPayload::c,
			FireSupportActionPayload::new);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return ID;
	}
}
