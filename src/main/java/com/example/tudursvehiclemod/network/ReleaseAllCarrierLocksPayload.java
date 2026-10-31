package com.example.tudursvehiclemod.network;

import com.example.tudursvehiclemod.VehicleMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client -> server: "release every wingman's own current target lock" for a Carrier formation's own lead aircraft, returning all of them to ordinary formation-follow immediately - see entity.AbstractVehicleEntity's own tudursvehiclemod$releaseAllCarrierLocks() doc for the full server-side logic. Sent when the player presses the dedicated toggle key WITH Alt held (see client.VehicleModClient's own carrierLockToggleKey doc - held alone instead sends ToggleCarrierLockModePayload). No payload data needed - the server releases every wingman currently following whichever vehicle the player is CURRENTLY riding. */
public record ReleaseAllCarrierLocksPayload() implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<ReleaseAllCarrierLocksPayload> ID =
			new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(VehicleMod.MOD_ID, "release_all_carrier_locks"));

	public static final StreamCodec<RegistryFriendlyByteBuf, ReleaseAllCarrierLocksPayload> CODEC =
			StreamCodec.unit(new ReleaseAllCarrierLocksPayload());

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return ID;
	}
}
