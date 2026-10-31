package com.example.tudursvehiclemod.network;

import com.example.tudursvehiclemod.VehicleMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server -> client: "play this weapon's fire sound at this position". */
public record WeaponFireSoundPayload(
		String soundName, double x, double y, double z, float volume, float pitch, float pitchRandom
) implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<WeaponFireSoundPayload> ID =
			new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(VehicleMod.MOD_ID, "weapon_fire_sound"));

	// Manual encode/decode rather than PacketCodec.tuple().
	public static final StreamCodec<RegistryFriendlyByteBuf, WeaponFireSoundPayload> CODEC = StreamCodec.ofMember(
			(value, buf) -> {
				buf.writeUtf(value.soundName());
				buf.writeDouble(value.x());
				buf.writeDouble(value.y());
				buf.writeDouble(value.z());
				buf.writeFloat(value.volume());
				buf.writeFloat(value.pitch());
				buf.writeFloat(value.pitchRandom());
			},
			buf -> new WeaponFireSoundPayload(
					buf.readUtf(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
					buf.readFloat(), buf.readFloat(), buf.readFloat())
	);

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return ID;
	}
}
