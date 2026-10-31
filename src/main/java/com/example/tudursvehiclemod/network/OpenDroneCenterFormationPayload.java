package com.example.tudursvehiclemod.network;

import com.example.tudursvehiclemod.VehicleMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client -> server: "open the formation wingman-slot UI for the Drone Center at this position" - sent from a button inside client.screen.DroneCenterConfigScreen (see that class's own doc), for the Drone Center formation-flying feature. Separate from OpenBlockSlotsPayload (which always opens DroneCenterBlockEntity's own default 2-slot menu, since that block entity IS its own NamedScreenHandlerFactory) - this instead opens screen.DroneCenterFormationScreenHandler specifically, via a dedicated wrapper factory constructed at the handler site. */
public record OpenDroneCenterFormationPayload(int x, int y, int z) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<OpenDroneCenterFormationPayload> ID =
			new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(VehicleMod.MOD_ID, "open_drone_center_formation"));

	public static final StreamCodec<RegistryFriendlyByteBuf, OpenDroneCenterFormationPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.INT, OpenDroneCenterFormationPayload::x,
			ByteBufCodecs.INT, OpenDroneCenterFormationPayload::y,
			ByteBufCodecs.INT, OpenDroneCenterFormationPayload::z,
			OpenDroneCenterFormationPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return ID;
	}
}
