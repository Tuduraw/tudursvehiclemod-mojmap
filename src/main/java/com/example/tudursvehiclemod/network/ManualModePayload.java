package com.example.tudursvehiclemod.network;

import com.example.tudursvehiclemod.VehicleMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client -> server: toggle manual mode (disables the automatic level-flight assist) on the aircraft the sender is piloting. Held state, sent only on change. */
public record ManualModePayload(boolean enabled) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<ManualModePayload> ID =
			new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(VehicleMod.MOD_ID, "manual_mode"));

	public static final StreamCodec<RegistryFriendlyByteBuf, ManualModePayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.BOOL, ManualModePayload::enabled,
			ManualModePayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return ID;
	}
}
