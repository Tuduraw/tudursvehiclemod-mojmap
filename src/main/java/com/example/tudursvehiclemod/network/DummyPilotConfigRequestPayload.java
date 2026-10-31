package com.example.tudursvehiclemod.network;

import com.example.tudursvehiclemod.VehicleMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client -> server: "open the dummy pilot settings for the Drone Center at (x,y,z)" - sent when the player presses that button on the Drone Center config screen. The server replies with DummyPilotConfigOpenPayload.
 *
 * A round trip rather than the client just opening the screen directly (the way a purely client-side sub-screen could) because the available skin list is a SERVER-side scan (see DummyPilotSkinRegistry's own doc - addon folders live on the server too, and on a dedicated server the client may not have the same ones), so the client can't populate the selection UI on its own. */
public record DummyPilotConfigRequestPayload(int x, int y, int z) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<DummyPilotConfigRequestPayload> ID =
			new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(VehicleMod.MOD_ID, "dummy_pilot_config_request"));

	public static final StreamCodec<RegistryFriendlyByteBuf, DummyPilotConfigRequestPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, DummyPilotConfigRequestPayload::x,
			ByteBufCodecs.VAR_INT, DummyPilotConfigRequestPayload::y,
			ByteBufCodecs.VAR_INT, DummyPilotConfigRequestPayload::z,
			DummyPilotConfigRequestPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return ID;
	}
}
