package com.example.tudursvehiclemod.network;

import com.example.tudursvehiclemod.VehicleMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client -> server: "try to toggle my vehicle's wing fold now" - see
 * AbstractVehicleEntity#tryToggleWingFold()'s own doc for why this can be
 * rejected (a non-variable-sweep wing only folds/unfolds while stationary). */
public record WingFoldTogglePayload() implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<WingFoldTogglePayload> ID =
			new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(VehicleMod.MOD_ID, "wing_fold_toggle"));

	public static final StreamCodec<RegistryFriendlyByteBuf, WingFoldTogglePayload> CODEC =
			StreamCodec.unit(new WingFoldTogglePayload());

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return ID;
	}
}
