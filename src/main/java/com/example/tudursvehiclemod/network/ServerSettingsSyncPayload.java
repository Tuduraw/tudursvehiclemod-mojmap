package com.example.tudursvehiclemod.network;

import com.example.tudursvehiclemod.VehicleMod;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server -> client: the server settings a client needs to move vehicles the way the server does - the vehicle speed multipliers, common and per entity type (entity.VehicleSpeedScaling). Sent on joining and whenever they change. */
public record ServerSettingsSyncPayload(float vehicleSpeedMultiplier, Map<String, Float> vehicleSpeedMultiplierByType) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<ServerSettingsSyncPayload> ID =
			new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(VehicleMod.MOD_ID, "server_settings_sync"));

	public static final StreamCodec<RegistryFriendlyByteBuf, ServerSettingsSyncPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.FLOAT, ServerSettingsSyncPayload::vehicleSpeedMultiplier,
			ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.FLOAT), ServerSettingsSyncPayload::vehicleSpeedMultiplierByType,
			ServerSettingsSyncPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return ID;
	}
}
