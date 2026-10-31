package com.example.tudursvehiclemod.network;

import com.example.tudursvehiclemod.VehicleMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client -> server: "trigger a mob-drop sequence on my current vehicle" - supports MC Heli's own MobDropOption (see asset.MobDropOption's own doc), sent when the pilot of a vehicle whose own VehicleDefinition has a MobDropOption configured presses the assigned key (see client.VehicleModClient's own key-handling). No payload data needed - the server resolves "this player's own current vehicle" itself (never trusts a client-supplied target) and re-validates that this player is actually the pilot (seat 0) and a MobDropOption is actually configured, via entity.AbstractVehicleEntity's own tudursvehiclemod$tryTriggerMobDrop(). */
public record MobDropTriggerPayload() implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<MobDropTriggerPayload> ID =
			new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(VehicleMod.MOD_ID, "mob_drop_trigger"));

	public static final StreamCodec<RegistryFriendlyByteBuf, MobDropTriggerPayload> CODEC =
			StreamCodec.unit(new MobDropTriggerPayload());

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return ID;
	}
}
