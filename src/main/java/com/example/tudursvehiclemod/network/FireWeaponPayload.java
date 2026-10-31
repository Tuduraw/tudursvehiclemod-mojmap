package com.example.tudursvehiclemod.network;

import com.example.tudursvehiclemod.VehicleMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client -> server: "fire the weapon mounted at this index on the vehicle I'm currently riding". */
public record FireWeaponPayload(int weaponIndex) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<FireWeaponPayload> ID =
			new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(VehicleMod.MOD_ID, "fire_weapon"));

	public static final StreamCodec<RegistryFriendlyByteBuf, FireWeaponPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, FireWeaponPayload::weaponIndex,
			FireWeaponPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return ID;
	}
}
