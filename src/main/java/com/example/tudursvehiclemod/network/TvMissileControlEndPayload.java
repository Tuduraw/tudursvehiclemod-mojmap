package com.example.tudursvehiclemod.network;

import com.example.tudursvehiclemod.VehicleMod;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server -> client: "control of whatever TV-guided missile you were steering has just ended (a hit, or it strayed out of range/an unloaded chunk - see entity.projectile.VehicleProjectileEntity's own tudursvehiclemod$releaseTvControl() doc) - revert your own camera back to normal". Paired with TvMissileControlStartPayload for the initial hand-off. */
public record TvMissileControlEndPayload() implements CustomPacketPayload {

	public static final CustomPacketPayload.Type<TvMissileControlEndPayload> ID =
			new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(VehicleMod.MOD_ID, "tv_missile_control_end"));

	public static final StreamCodec<RegistryFriendlyByteBuf, TvMissileControlEndPayload> CODEC =
			StreamCodec.unit(new TvMissileControlEndPayload());

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return ID;
	}
}
