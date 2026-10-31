package com.example.tudursvehiclemod.network;

import com.example.tudursvehiclemod.VehicleMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client -> server: "parachute-jump from my current vehicle seat" - supports MC Heli's own EnableParachuting (adapted to a per-seat setting - see asset.SeatDefinition's own enableParachuting doc), sent when a non-pilot passenger of a seat with that flag set presses Space while riding (see client.VehicleModClient's own key-handling for the exact client-side trigger, distinct from the plain Space-to-deploy-parachute-on-foot action, which explicitly requires NOT riding anything). No payload data needed - the server resolves "this player's own current vehicle/seat" itself (never trusts a client-supplied target) and re-validates the seat's own enableParachuting/non-pilot status before actually acting, via entity.AbstractVehicleEntity's own tudursvehiclemod$tryParachuteJump(). */
public record ParachuteJumpPayload() implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<ParachuteJumpPayload> ID =
			new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(VehicleMod.MOD_ID, "parachute_jump"));

	public static final StreamCodec<RegistryFriendlyByteBuf, ParachuteJumpPayload> CODEC =
			StreamCodec.unit(new ParachuteJumpPayload());

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return ID;
	}
}
