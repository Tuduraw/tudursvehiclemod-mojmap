package com.example.tudursvehiclemod.network;

import com.example.tudursvehiclemod.VehicleMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client -> server: "eject from my current vehicle seat" - supports MC Heli's own EnableEjectionSeat, sent when the player is riding a vehicle whose own VehicleDefinition has enableEjectionSeat set and presses Alt+Space (see client.VehicleModClient's own key-handling for the exact client-side trigger). No payload data needed - the server resolves "this player's own current vehicle" itself (never trusts a client-supplied target) and re-validates enableEjectionSeat before actually acting, via entity.AbstractVehicleEntity's own tudursvehiclemod$tryEjectSeat(). */
public record EjectSeatPayload() implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<EjectSeatPayload> ID =
			new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(VehicleMod.MOD_ID, "eject_seat"));

	public static final StreamCodec<RegistryFriendlyByteBuf, EjectSeatPayload> CODEC =
			StreamCodec.unit(new EjectSeatPayload());

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return ID;
	}
}
