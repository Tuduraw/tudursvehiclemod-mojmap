package com.example.tudursvehiclemod.network;

import com.example.tudursvehiclemod.VehicleMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client -> server: how far (blocks) this client wants to be told about vehicles beyond its own entity range (entity.FarVehicleSync); 0 for not at all. Sent on joining and whenever the client's setting changes. */
public record FarVehicleRequestPayload(int distance) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<FarVehicleRequestPayload> ID =
			new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(VehicleMod.MOD_ID, "far_vehicle_request"));

	public static final StreamCodec<RegistryFriendlyByteBuf, FarVehicleRequestPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, FarVehicleRequestPayload::distance,
			FarVehicleRequestPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return ID;
	}
}
