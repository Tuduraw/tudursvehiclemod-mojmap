package com.example.tudursvehiclemod.network;

import com.example.tudursvehiclemod.VehicleMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Client -> server: "my forward/backward and left/right movement input are currently these values".. */
public record ThrottleInputPayload(float forwardInput, float sidewaysInput) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<ThrottleInputPayload> ID =
			new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(VehicleMod.MOD_ID, "throttle_input"));

	public static final StreamCodec<RegistryFriendlyByteBuf, ThrottleInputPayload> CODEC = StreamCodec.composite(
			ByteBufCodecs.FLOAT, ThrottleInputPayload::forwardInput,
			ByteBufCodecs.FLOAT, ThrottleInputPayload::sidewaysInput,
			ThrottleInputPayload::new
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return ID;
	}
}
