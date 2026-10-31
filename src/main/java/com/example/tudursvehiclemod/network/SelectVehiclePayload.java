package com.example.tudursvehiclemod.network;

import com.example.tudursvehiclemod.VehicleMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client -> server: "I picked this vehicle from the selection screen". Which hand is included since the player could be holding a valid tiered spawner in either. */
public record SelectVehiclePayload(Identifier vehicleId, boolean mainHand) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<SelectVehiclePayload> ID =
			new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(VehicleMod.MOD_ID, "select_vehicle"));

	public static final StreamCodec<RegistryFriendlyByteBuf, SelectVehiclePayload> CODEC = StreamCodec.composite(
			Identifier.STREAM_CODEC, SelectVehiclePayload::vehicleId,
			ByteBufCodecs.BOOL, SelectVehiclePayload::mainHand,
			SelectVehiclePayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return ID;
	}
}
