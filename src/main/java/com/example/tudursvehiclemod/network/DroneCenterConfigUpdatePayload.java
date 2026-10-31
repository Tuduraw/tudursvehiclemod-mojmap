package com.example.tudursvehiclemod.network;

import com.example.tudursvehiclemod.VehicleMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client -> server: "update the Drone Center at (x,y,z) to these config values" - sent whenever the player adjusts a value or toggles active/inactive in client.screen.DroneCenterConfigScreen. Always sends the full current set of values (not just whichever one changed) - simpler than tracking individual deltas, and this screen's own values change rarely enough (button presses, not continuous dragging) that resending everything each time has no real cost.
 *
 * formationConfig - see DroneCenterConfigOpenPayload's own identical field doc. */
public record DroneCenterConfigUpdatePayload(
		int x, int y, int z, float speedFraction, float orbitAltitude, float radiusMultiplier, boolean active,
		boolean redstoneControlEnabled, String formationConfig
) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<DroneCenterConfigUpdatePayload> ID =
			new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(VehicleMod.MOD_ID, "drone_center_config_update"));

	public static final StreamCodec<RegistryFriendlyByteBuf, DroneCenterConfigUpdatePayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, DroneCenterConfigUpdatePayload::x,
			ByteBufCodecs.VAR_INT, DroneCenterConfigUpdatePayload::y,
			ByteBufCodecs.VAR_INT, DroneCenterConfigUpdatePayload::z,
			ByteBufCodecs.FLOAT, DroneCenterConfigUpdatePayload::speedFraction,
			ByteBufCodecs.FLOAT, DroneCenterConfigUpdatePayload::orbitAltitude,
			ByteBufCodecs.FLOAT, DroneCenterConfigUpdatePayload::radiusMultiplier,
			ByteBufCodecs.BOOL, DroneCenterConfigUpdatePayload::active,
			ByteBufCodecs.BOOL, DroneCenterConfigUpdatePayload::redstoneControlEnabled,
			ByteBufCodecs.STRING_UTF8, DroneCenterConfigUpdatePayload::formationConfig,
			DroneCenterConfigUpdatePayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return ID;
	}
}
