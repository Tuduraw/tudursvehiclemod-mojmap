package com.example.tudursvehiclemod.network;

import com.example.tudursvehiclemod.VehicleMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client -> server: "my brake key (Space, by default the vanilla jump key -
 * unused by any vehicle here otherwise) is currently held/released" - per a
 * direct request, CarEntity-specific: applies a decelerating force
 * independent of the current throttle value (see CarEntity's own
 * tudursvehiclemod$applyBrake() doc). Same "boolean, sent only on change"
 * pattern as VerticalLevelInputPayload, for the same reason
 * (PlayerEntity#input isn't reliably readable server-side for a riding
 * entity, so this needs its own explicit sync). */
public record BrakeInputPayload(boolean braking) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<BrakeInputPayload> ID =
			new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(VehicleMod.MOD_ID, "brake_input"));

	public static final StreamCodec<RegistryFriendlyByteBuf, BrakeInputPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.BOOL, BrakeInputPayload::braking,
			BrakeInputPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return ID;
	}
}
