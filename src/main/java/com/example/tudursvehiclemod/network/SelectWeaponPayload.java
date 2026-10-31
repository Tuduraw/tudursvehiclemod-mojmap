package com.example.tudursvehiclemod.network;

import com.example.tudursvehiclemod.VehicleMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client -> server: "I've selected this weapon index on the vehicle I'm riding" - sent whenever the
 * locally-cycled selection changes, not just on fire, so AddPartWeaponBay-style parts (see
 * TogglePart's own "weapon_bay" trigger) can open for everyone, not just the selecting player's
 * own client. */
public record SelectWeaponPayload(int weaponIndex) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<SelectWeaponPayload> ID =
			new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(VehicleMod.MOD_ID, "select_weapon"));

	public static final StreamCodec<RegistryFriendlyByteBuf, SelectWeaponPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, SelectWeaponPayload::weaponIndex,
			SelectWeaponPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return ID;
	}
}
