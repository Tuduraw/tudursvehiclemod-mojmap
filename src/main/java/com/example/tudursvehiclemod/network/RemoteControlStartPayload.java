package com.example.tudursvehiclemod.network;

import com.example.tudursvehiclemod.VehicleMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server -> client: "you're now remote-controlling entityId (a UAV vehicle, via a bound block.StationBlockEntity) - start showing its own pilot view, and redirect your own input to it" - see entity.AbstractVehicleEntity's own tudursvehiclemod$tryEnterRemoteControl() doc, and client.RemoteControlState's own doc for where this actually gets consumed. Paired with RemoteControlEndPayload for the reverse. */
public record RemoteControlStartPayload(int entityId) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<RemoteControlStartPayload> ID =
			new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(VehicleMod.MOD_ID, "remote_control_start"));

	public static final StreamCodec<RegistryFriendlyByteBuf, RemoteControlStartPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.VAR_INT, RemoteControlStartPayload::entityId,
			RemoteControlStartPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return ID;
	}
}
