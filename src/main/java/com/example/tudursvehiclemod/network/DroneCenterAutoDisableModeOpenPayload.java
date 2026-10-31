package com.example.tudursvehiclemod.network;

import com.example.tudursvehiclemod.VehicleMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server -> client: "this Drone Center's own current auto-disable resume mode is X" - sent alongside DummyPilotConfigOpenPayload when that screen opens (a separate payload rather than an extra field on that one, which has no room left - PacketCodec.tuple()'s own maximum of 12 field pairs is already fully used there).
 *
 * AutoResume mirrors block.DroneCenterBlockEntity's own AutoDisableResumeMode - true for AUTO_RESUME ("モード2"), false for MANUAL_REENABLE ("モード1", the default). */
public record DroneCenterAutoDisableModeOpenPayload(int x, int y, int z, boolean autoResume) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<DroneCenterAutoDisableModeOpenPayload> ID =
			new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(VehicleMod.MOD_ID, "drone_center_auto_disable_mode_open"));

	public static final StreamCodec<RegistryFriendlyByteBuf, DroneCenterAutoDisableModeOpenPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, DroneCenterAutoDisableModeOpenPayload::x,
			ByteBufCodecs.VAR_INT, DroneCenterAutoDisableModeOpenPayload::y,
			ByteBufCodecs.VAR_INT, DroneCenterAutoDisableModeOpenPayload::z,
			ByteBufCodecs.BOOL, DroneCenterAutoDisableModeOpenPayload::autoResume,
			DroneCenterAutoDisableModeOpenPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return ID;
	}
}
