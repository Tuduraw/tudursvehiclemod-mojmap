package com.example.tudursvehiclemod.network;

import com.example.tudursvehiclemod.VehicleMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client -> server: "update the Drone Center at (x,y,z) to this formation config" - sent whenever the player adjusts a value in client.screen.DroneCenterFormationConfigScreen. those controls moved to their own dedicated screen, which has no access to that other screen's own speed/orbitAltitude/radiusMultiplier/active/redstoneControlEnabled values at all - a separate, formation-only payload avoids needing to thread all of that unrelated state through just to keep reusing DroneCenterConfigUpdatePayload.
 *
 * formationConfig - same "formationSize,formationType,formationSpacing,formationElementSpacing" compact format DroneCenterConfigOpenPayload/UpdatePayload's own identical field already uses. */
public record DroneCenterFormationConfigUpdatePayload(int x, int y, int z, String formationConfig) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<DroneCenterFormationConfigUpdatePayload> ID =
			new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(VehicleMod.MOD_ID, "drone_center_formation_config_update"));

	public static final StreamCodec<RegistryFriendlyByteBuf, DroneCenterFormationConfigUpdatePayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, DroneCenterFormationConfigUpdatePayload::x,
			ByteBufCodecs.VAR_INT, DroneCenterFormationConfigUpdatePayload::y,
			ByteBufCodecs.VAR_INT, DroneCenterFormationConfigUpdatePayload::z,
			ByteBufCodecs.STRING_UTF8, DroneCenterFormationConfigUpdatePayload::formationConfig,
			DroneCenterFormationConfigUpdatePayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return ID;
	}
}
