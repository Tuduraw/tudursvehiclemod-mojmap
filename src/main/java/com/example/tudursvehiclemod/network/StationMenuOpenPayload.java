package com.example.tudursvehiclemod.network;

import com.example.tudursvehiclemod.VehicleMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server -> client: "open your own station menu screen for the station at this position" - sent whenever a player right-clicks a block.StationBlock (see that class's own doc). and to consolidate the remote-control toggle and the item slots into one reachable screen (client.screen.StationMenuScreen) rather than the remote-control toggle firing immediately on every right-click as before. */
public record StationMenuOpenPayload(int x, int y, int z) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<StationMenuOpenPayload> ID =
			new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(VehicleMod.MOD_ID, "station_menu_open"));

	public static final StreamCodec<RegistryFriendlyByteBuf, StationMenuOpenPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.INT, StationMenuOpenPayload::x,
			ByteBufCodecs.INT, StationMenuOpenPayload::y,
			ByteBufCodecs.INT, StationMenuOpenPayload::z,
			StationMenuOpenPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return ID;
	}
}
