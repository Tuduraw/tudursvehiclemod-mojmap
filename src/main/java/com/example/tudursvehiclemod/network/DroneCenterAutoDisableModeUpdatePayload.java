package com.example.tudursvehiclemod.network;

import com.example.tudursvehiclemod.VehicleMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client -> server: "set this Drone Center's own auto-disable resume mode" - sent when the player presses the toggle button on client.screen.DummyPilotConfigScreen. See DroneCenterAutoDisableModeOpenPayload's own doc for why this is a separate payload rather than an extra field on an existing one. */
public record DroneCenterAutoDisableModeUpdatePayload(int x, int y, int z, boolean autoResume) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<DroneCenterAutoDisableModeUpdatePayload> ID =
			new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(VehicleMod.MOD_ID, "drone_center_auto_disable_mode_update"));

	public static final StreamCodec<RegistryFriendlyByteBuf, DroneCenterAutoDisableModeUpdatePayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, DroneCenterAutoDisableModeUpdatePayload::x,
			ByteBufCodecs.VAR_INT, DroneCenterAutoDisableModeUpdatePayload::y,
			ByteBufCodecs.VAR_INT, DroneCenterAutoDisableModeUpdatePayload::z,
			ByteBufCodecs.BOOL, DroneCenterAutoDisableModeUpdatePayload::autoResume,
			DroneCenterAutoDisableModeUpdatePayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return ID;
	}
}
