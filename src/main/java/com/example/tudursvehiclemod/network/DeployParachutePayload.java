package com.example.tudursvehiclemod.network;

import com.example.tudursvehiclemod.VehicleMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client -> server: "deploy parachute" - sent when the player presses Space while a parachute is equipped in the chest slot and this player is currently in a "deployable" state (falling, not already deployed, not gliding via elytra - see client.VehicleModClient's own tudursvehiclemod$isParachuteDeployable() doc for the exact client-side check, mirroring elytra's own "can this even be used right now" conditions per that direct request). No payload data needed - the server re-checks the same conditions itself (never trusts the client alone) and, if still valid, deploys THIS player's own parachute; the server is also solely responsible for auto-retracting on landing, so this payload only ever needs to request DEPLOYMENT, never retraction. */
public record DeployParachutePayload() implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<DeployParachutePayload> ID =
			new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(VehicleMod.MOD_ID, "deploy_parachute"));

	public static final StreamCodec<RegistryFriendlyByteBuf, DeployParachutePayload> CODEC =
			StreamCodec.unit(new DeployParachutePayload());

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return ID;
	}
}
