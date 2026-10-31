package com.example.tudursvehiclemod.network;

import com.example.tudursvehiclemod.VehicleMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client -> server: "apply these dummy pilot combat settings to the Drone Center at (x,y,z)" - sent from client.screen.DummyPilotWeaponConfigScreen whenever the player cycles the weapon or edits an attack altitude/the search range, so a change takes effect immediately (see DroneCenterBlockEntity's own tudursvehiclemod$setDummyPilotCombatConfig()) rather than only when the screen closes.
 *
 * weaponIndex is re-validated server-side against the actual bound vehicle's own current weapon list (a client's own idea of "how many weapons" could be stale) rather than trusted as-is. */
public record DummyPilotWeaponConfigUpdatePayload(
		int x, int y, int z, int weaponIndex, float attackStartAltitude, float attackStopAltitude, float searchRange, float diveTargetYOffset
) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<DummyPilotWeaponConfigUpdatePayload> ID =
			new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(VehicleMod.MOD_ID, "dummy_pilot_weapon_config_update"));

	public static final StreamCodec<RegistryFriendlyByteBuf, DummyPilotWeaponConfigUpdatePayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, DummyPilotWeaponConfigUpdatePayload::x,
			ByteBufCodecs.VAR_INT, DummyPilotWeaponConfigUpdatePayload::y,
			ByteBufCodecs.VAR_INT, DummyPilotWeaponConfigUpdatePayload::z,
			ByteBufCodecs.VAR_INT, DummyPilotWeaponConfigUpdatePayload::weaponIndex,
			ByteBufCodecs.FLOAT, DummyPilotWeaponConfigUpdatePayload::attackStartAltitude,
			ByteBufCodecs.FLOAT, DummyPilotWeaponConfigUpdatePayload::attackStopAltitude,
			ByteBufCodecs.FLOAT, DummyPilotWeaponConfigUpdatePayload::searchRange,
			ByteBufCodecs.FLOAT, DummyPilotWeaponConfigUpdatePayload::diveTargetYOffset,
			DummyPilotWeaponConfigUpdatePayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return ID;
	}
}
