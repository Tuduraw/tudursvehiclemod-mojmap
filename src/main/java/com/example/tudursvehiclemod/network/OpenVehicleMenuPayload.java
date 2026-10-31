package com.example.tudursvehiclemod.network;

import com.example.tudursvehiclemod.VehicleMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client -> server: "open the vehicle menu for whatever I'm currently riding". Sent once per key press (see VehicleModClient's own openVehicleMenuKey). */
public record OpenVehicleMenuPayload() implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<OpenVehicleMenuPayload> ID =
			new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(VehicleMod.MOD_ID, "open_vehicle_menu"));

	public static final StreamCodec<RegistryFriendlyByteBuf, OpenVehicleMenuPayload> CODEC =
			StreamCodec.unit(new OpenVehicleMenuPayload());

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return ID;
	}
}
