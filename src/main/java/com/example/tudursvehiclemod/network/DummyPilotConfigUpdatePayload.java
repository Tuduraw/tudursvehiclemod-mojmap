package com.example.tudursvehiclemod.network;

import com.example.tudursvehiclemod.VehicleMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client -> server: "apply these dummy pilot settings to the Drone Center at (x,y,z)". Sent whenever the player toggles the option, picks a different skin, toggles the nametag, or edits its custom name, so a change takes effect immediately (see DroneCenterBlockEntity's own tudursvehiclemod$setDummyPilotConfig()) rather than only when the screen closes.
 *
 * skinId is validated server-side against the actual available list rather than trusted - see DummyPilotSkinRegistry's own tudursvehiclemod$resolveOrDefault(). name is likewise length-capped server-side rather than trusted as-is.
 *
 * This payload itself went back to carrying only the original look/name fields it had before that combat feature was ever added here. */
public record DummyPilotConfigUpdatePayload(
		int x, int y, int z, boolean enabled, String skinId, boolean nameVisible, String name
) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<DummyPilotConfigUpdatePayload> ID =
			new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(VehicleMod.MOD_ID, "dummy_pilot_config_update"));

	public static final StreamCodec<RegistryFriendlyByteBuf, DummyPilotConfigUpdatePayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, DummyPilotConfigUpdatePayload::x,
			ByteBufCodecs.VAR_INT, DummyPilotConfigUpdatePayload::y,
			ByteBufCodecs.VAR_INT, DummyPilotConfigUpdatePayload::z,
			ByteBufCodecs.BOOL, DummyPilotConfigUpdatePayload::enabled,
			ByteBufCodecs.STRING_UTF8, DummyPilotConfigUpdatePayload::skinId,
			ByteBufCodecs.BOOL, DummyPilotConfigUpdatePayload::nameVisible,
			ByteBufCodecs.STRING_UTF8, DummyPilotConfigUpdatePayload::name,
			DummyPilotConfigUpdatePayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return ID;
	}
}
