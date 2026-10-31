package com.example.tudursvehiclemod.network;

import com.example.tudursvehiclemod.VehicleMod;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server -> client: "open your own dummy pilot settings screen for the block at (x,y,z)" - the reply to DummyPilotConfigRequestPayload.
 *
 * Carries availableSkinIds (rather than letting the client scan for itself) for the reason given in that request payload's own doc: the addon folders that supply extra skins are read server-side, so on a dedicated server the authoritative list is the server's. The client uses this list verbatim to build the selection UI - see client.screen.DummyPilotConfigScreen.
 *
 * nameVisible/name carry the requested nametag settings (visibility toggle + arbitrary custom name) alongside the original enabled/skin fields.
 *
 * This screen's own combat settings (weapon selection, attack altitudes, search range) moved to a dedicated sub-screen (see network.DummyPilotWeaponConfigOpenPayload) reached via its own "Weapon Settings" button, since this screen's own layout was already at its practical display limit - this payload itself went back to carrying only the original look/name fields it had before that combat feature was ever added here. */
public record DummyPilotConfigOpenPayload(
		int x, int y, int z, boolean enabled, String skinId, List<String> availableSkinIds,
		boolean nameVisible, String name
) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<DummyPilotConfigOpenPayload> ID =
			new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(VehicleMod.MOD_ID, "dummy_pilot_config_open"));

	public static final StreamCodec<RegistryFriendlyByteBuf, DummyPilotConfigOpenPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, DummyPilotConfigOpenPayload::x,
			ByteBufCodecs.VAR_INT, DummyPilotConfigOpenPayload::y,
			ByteBufCodecs.VAR_INT, DummyPilotConfigOpenPayload::z,
			ByteBufCodecs.BOOL, DummyPilotConfigOpenPayload::enabled,
			ByteBufCodecs.STRING_UTF8, DummyPilotConfigOpenPayload::skinId,
			ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()), DummyPilotConfigOpenPayload::availableSkinIds,
			ByteBufCodecs.BOOL, DummyPilotConfigOpenPayload::nameVisible,
			ByteBufCodecs.STRING_UTF8, DummyPilotConfigOpenPayload::name,
			DummyPilotConfigOpenPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return ID;
	}
}
