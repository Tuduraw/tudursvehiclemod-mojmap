package com.example.tudursvehiclemod.network;

import com.example.tudursvehiclemod.VehicleMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client -> server: "open the dummy pilot WEAPON settings for the Drone Center at (x,y,z)" - sent when the player presses the new "Weapon Settings" button on client.screen.DummyPilotConfigScreen. The server replies with DummyPilotWeaponConfigOpenPayload.
 *
 * A separate round trip, same reasoning as DummyPilotConfigRequestPayload's own doc - the bound vehicle's own weapon list is server-side data (its own definition), so the client can't populate the weapon selector on its own. */
public record DummyPilotWeaponConfigRequestPayload(int x, int y, int z) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<DummyPilotWeaponConfigRequestPayload> ID =
			new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(VehicleMod.MOD_ID, "dummy_pilot_weapon_config_request"));

	public static final StreamCodec<RegistryFriendlyByteBuf, DummyPilotWeaponConfigRequestPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, DummyPilotWeaponConfigRequestPayload::x,
			ByteBufCodecs.VAR_INT, DummyPilotWeaponConfigRequestPayload::y,
			ByteBufCodecs.VAR_INT, DummyPilotWeaponConfigRequestPayload::z,
			DummyPilotWeaponConfigRequestPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return ID;
	}
}
