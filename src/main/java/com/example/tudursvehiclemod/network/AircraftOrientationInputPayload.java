package com.example.tudursvehiclemod.network;

import com.example.tudursvehiclemod.VehicleMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client -> server: raw (pre-vanilla-clamp) mouse yaw/pitch deltas accumulated since the last tick, while piloting an aircraft. */
public record AircraftOrientationInputPayload(float yawDelta, float pitchDelta) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<AircraftOrientationInputPayload> ID =
			new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(VehicleMod.MOD_ID, "aircraft_orientation_input"));

	public static final StreamCodec<RegistryFriendlyByteBuf, AircraftOrientationInputPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.FLOAT, AircraftOrientationInputPayload::yawDelta,
			ByteBufCodecs.FLOAT, AircraftOrientationInputPayload::pitchDelta,
			AircraftOrientationInputPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return ID;
	}
}
