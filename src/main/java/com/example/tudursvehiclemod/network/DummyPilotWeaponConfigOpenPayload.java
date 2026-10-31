package com.example.tudursvehiclemod.network;

import com.example.tudursvehiclemod.VehicleMod;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server -> client: "open your own dummy pilot WEAPON settings screen for the block at (x,y,z)" - the reply to DummyPilotWeaponConfigRequestPayload.
 *
 * WeaponDisplayNames carries the currently-bound vehicle's own weapon list (each entry's own DisplayName, or empty for a vehicle with no weapons at all/none currently bound - see block.DroneCenterBlockEntity's own tudursvehiclemod$getDummyPilotWeaponDisplayName() doc for why this has to be server-resolved: the vehicle's own definition, and so its own weapon list, is server-side data the client has no independent way to look up). weaponIndex is the currently-selected slot (-1 if none/invalid). attackStartAltitude/attackStopAltitude carry the Drone-Center-configured attack-altitude thresholds (default 200/40, matching CAS/Carrier's own weapon-file defaults). searchRange carries how far (blocks) the dummy pilot looks for a hostile mob to engage (default 64.0, matching entity.DummyPilotEntity's own previous hardcoded value). */
public record DummyPilotWeaponConfigOpenPayload(
		int x, int y, int z, List<String> weaponDisplayNames, int weaponIndex,
		float attackStartAltitude, float attackStopAltitude, float searchRange, float diveTargetYOffset
) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<DummyPilotWeaponConfigOpenPayload> ID =
			new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(VehicleMod.MOD_ID, "dummy_pilot_weapon_config_open"));

	public static final StreamCodec<RegistryFriendlyByteBuf, DummyPilotWeaponConfigOpenPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, DummyPilotWeaponConfigOpenPayload::x,
			ByteBufCodecs.VAR_INT, DummyPilotWeaponConfigOpenPayload::y,
			ByteBufCodecs.VAR_INT, DummyPilotWeaponConfigOpenPayload::z,
			ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), DummyPilotWeaponConfigOpenPayload::weaponDisplayNames,
			ByteBufCodecs.VAR_INT, DummyPilotWeaponConfigOpenPayload::weaponIndex,
			ByteBufCodecs.FLOAT, DummyPilotWeaponConfigOpenPayload::attackStartAltitude,
			ByteBufCodecs.FLOAT, DummyPilotWeaponConfigOpenPayload::attackStopAltitude,
			ByteBufCodecs.FLOAT, DummyPilotWeaponConfigOpenPayload::searchRange,
			ByteBufCodecs.FLOAT, DummyPilotWeaponConfigOpenPayload::diveTargetYOffset,
			DummyPilotWeaponConfigOpenPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return ID;
	}
}
