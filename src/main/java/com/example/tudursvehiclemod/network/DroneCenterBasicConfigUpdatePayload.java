package com.example.tudursvehiclemod.network;

import com.example.tudursvehiclemod.VehicleMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client -> server: "update the Drone Center at (x,y,z) to these basic (speed/altitude/radius) settings" - sent whenever the player adjusts a value in client.screen.DroneCenterBasicConfigScreen. the cruise speed/orbit altitude/turn radius rows moved to their own dedicated screen too, mirroring network.DroneCenterFormationConfigUpdatePayload's own separation reasoning - that new screen has no access to active/redstoneControlEnabled/formationConfig at all, so a dedicated, basic-settings-only payload avoids needing to thread all of that unrelated state through just to keep reusing DroneCenterConfigUpdatePayload. */
public record DroneCenterBasicConfigUpdatePayload(int x, int y, int z, float speedFraction, float orbitAltitude, float radiusMultiplier) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<DroneCenterBasicConfigUpdatePayload> ID =
			new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(VehicleMod.MOD_ID, "drone_center_basic_config_update"));

	public static final StreamCodec<RegistryFriendlyByteBuf, DroneCenterBasicConfigUpdatePayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, DroneCenterBasicConfigUpdatePayload::x,
			ByteBufCodecs.VAR_INT, DroneCenterBasicConfigUpdatePayload::y,
			ByteBufCodecs.VAR_INT, DroneCenterBasicConfigUpdatePayload::z,
			ByteBufCodecs.FLOAT, DroneCenterBasicConfigUpdatePayload::speedFraction,
			ByteBufCodecs.FLOAT, DroneCenterBasicConfigUpdatePayload::orbitAltitude,
			ByteBufCodecs.FLOAT, DroneCenterBasicConfigUpdatePayload::radiusMultiplier,
			DroneCenterBasicConfigUpdatePayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return ID;
	}
}
