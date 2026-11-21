package com.example.tudursvehiclemod.network;

import com.example.tudursvehiclemod.VehicleMod;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Server -> client: the vehicles and projectiles the client asked to be told about (FarVehicleRequestPayload) that are beyond its own entity range - where each is, how it is moving and which way it faces, enough to draw it. Each list, when sent (hasVehicles/hasProjectiles - they are sent at different rates), is the whole current list: one missing from it is no longer to be shown (entity.FarVehicleSync, client.farvehicle.FarVehicleProxies). */
public record FarVehicleSnapshotPayload(boolean hasVehicles, List<Entry> vehicles, boolean hasProjectiles, List<ProjectileEntry> projectiles) implements CustomPacketPayload {

	/** One vehicle: its entity id on the server, entity type and vehicle definition ids, position, motion per tick, and body orientation quaternion. */
	public record Entry(int entityId, Identifier entityType, Identifier definition, double x, double y, double z,
			float vx, float vy, float vz, float qx, float qy, float qz, float qw) {
	}

	/** One projectile drawn with a model: its entity id on the server, model, texture, scale and tint, position, motion per tick, gravity (blocks/tick², to carry the motion on between reports), and heading. */
	public record ProjectileEntry(int entityId, Identifier model, Identifier texture, float scale, int color, double x, double y, double z,
			float vx, float vy, float vz, float gravity, float yaw, float pitch) {
	}

	public static final CustomPacketPayload.Type<FarVehicleSnapshotPayload> ID =
			new CustomPacketPayload.Type<>(Identifier.fromNamespaceAndPath(VehicleMod.MOD_ID, "far_vehicle_snapshot"));

	public static final StreamCodec<RegistryFriendlyByteBuf, FarVehicleSnapshotPayload> CODEC = StreamCodec.of(
			(buf, payload) -> {
				buf.writeBoolean(payload.hasVehicles());
				if (payload.hasVehicles()) {
					buf.writeVarInt(payload.vehicles().size());
					for (Entry e : payload.vehicles()) {
						buf.writeVarInt(e.entityId());
						buf.writeIdentifier(e.entityType());
						buf.writeIdentifier(e.definition());
						buf.writeDouble(e.x());
						buf.writeDouble(e.y());
						buf.writeDouble(e.z());
						buf.writeFloat(e.vx());
						buf.writeFloat(e.vy());
						buf.writeFloat(e.vz());
						buf.writeFloat(e.qx());
						buf.writeFloat(e.qy());
						buf.writeFloat(e.qz());
						buf.writeFloat(e.qw());
					}
				}
				buf.writeBoolean(payload.hasProjectiles());
				if (payload.hasProjectiles()) {
					buf.writeVarInt(payload.projectiles().size());
					for (ProjectileEntry e : payload.projectiles()) {
						buf.writeVarInt(e.entityId());
						buf.writeIdentifier(e.model());
						buf.writeIdentifier(e.texture());
						buf.writeFloat(e.scale());
						buf.writeInt(e.color());
						buf.writeDouble(e.x());
						buf.writeDouble(e.y());
						buf.writeDouble(e.z());
						buf.writeFloat(e.vx());
						buf.writeFloat(e.vy());
						buf.writeFloat(e.vz());
						buf.writeFloat(e.gravity());
						buf.writeFloat(e.yaw());
						buf.writeFloat(e.pitch());
					}
				}
			},
			buf -> {
				boolean hasVehicles = buf.readBoolean();
				List<Entry> vehicles = new ArrayList<>();
				if (hasVehicles) {
					int count = buf.readVarInt();
					for (int i = 0; i < count; i++) {
						vehicles.add(new Entry(buf.readVarInt(), buf.readIdentifier(), buf.readIdentifier(), buf.readDouble(), buf.readDouble(), buf.readDouble(),
								buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat()));
					}
				}
				boolean hasProjectiles = buf.readBoolean();
				List<ProjectileEntry> projectiles = new ArrayList<>();
				if (hasProjectiles) {
					int count = buf.readVarInt();
					for (int i = 0; i < count; i++) {
						projectiles.add(new ProjectileEntry(buf.readVarInt(), buf.readIdentifier(), buf.readIdentifier(), buf.readFloat(), buf.readInt(),
								buf.readDouble(), buf.readDouble(), buf.readDouble(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat(), buf.readFloat()));
					}
				}
				return new FarVehicleSnapshotPayload(hasVehicles, vehicles, hasProjectiles, projectiles);
			});

	@Override
	public Type<? extends CustomPacketPayload> type() {
		return ID;
	}
}
