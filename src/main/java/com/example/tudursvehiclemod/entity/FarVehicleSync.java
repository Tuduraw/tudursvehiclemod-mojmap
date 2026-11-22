package com.example.tudursvehiclemod.entity;

import com.example.tudursvehiclemod.VehicleModServerConfig;
import com.example.tudursvehiclemod.network.FarVehicleRequestPayload;
import com.example.tudursvehiclemod.network.FarVehicleSnapshotPayload;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.joml.Quaternionf;

/** Tells players about vehicles (and projectiles drawn with a model - missiles, bombs, shells) too far away for the game to send them as entities, so a client with an LOD mod (Distant Horizons and the like, which draw terrain far beyond the render distance) can draw them over that distant terrain too (client.FarVehicleProxies) - with their moving parts as they are (FarVehiclePose: landing gear, hatches, turrets, rotors).
 *
 * <p>Both sides have to want it: the server's farVehicleSyncEnabled (off by default - it tells players where far-away vehicles are, which a PvP server may not want), and the client's renderFarVehicles (off by default), which sends the distance it wants (FarVehicleRequestPayload). Every farVehicleSyncIntervalTicks the server sends each such player the vehicles in their world within that distance (and within farVehicleSyncMaxDistance), nearest first, up to farVehicleSyncMaxCount, leaving out the ones close enough to be sent as entities anyway.
 *
 * <p>Only vehicles the server has loaded can be told about: one in a chunk nobody is near (and no chunk loader keeps loaded) isn't there to be seen. */
public final class FarVehicleSync {

	private FarVehicleSync() {
	}

	/** Each player's requested distance (blocks), from their FarVehicleRequestPayload. */
	private static final Map<UUID, Integer> REQUESTED = new ConcurrentHashMap<>();

	/** The vehicles ticking on the server, per world - filled from their own ticks while anyone has asked (an area search over a couple of thousand blocks every few ticks would cost far more). */
	private static final Map<net.minecraft.world.level.Level, java.util.Set<AbstractVehicleEntity>> TICKING = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

	/** From a vehicle's server tick. */
	static void markTicking(AbstractVehicleEntity vehicle) {
		if (!REQUESTED.isEmpty()) {
			TICKING.computeIfAbsent(vehicle.level(), level -> java.util.Collections.newSetFromMap(new ConcurrentHashMap<>())).add(vehicle);
		}
	}

	/** From a vehicle's onRemoval(). */
	static void forget(AbstractVehicleEntity vehicle) {
		java.util.Set<AbstractVehicleEntity> set = TICKING.get(vehicle.level());
		if (set != null) {
			set.remove(vehicle);
		}
	}

	/** The same for projectiles drawn with a model (VehicleModelProjectileEntity): plain bullets are drawn as a speck no one would see that far out, and there are far too many of them. */
	private static final Map<net.minecraft.world.level.Level, java.util.Set<com.example.tudursvehiclemod.entity.projectile.VehicleModelProjectileEntity>> TICKING_PROJECTILES = java.util.Collections.synchronizedMap(new java.util.WeakHashMap<>());

	/** From a model projectile's server tick. */
	public static void markProjectileTicking(com.example.tudursvehiclemod.entity.projectile.VehicleModelProjectileEntity projectile) {
		if (!REQUESTED.isEmpty()) {
			TICKING_PROJECTILES.computeIfAbsent(projectile.level(), level -> java.util.Collections.newSetFromMap(new ConcurrentHashMap<>())).add(projectile);
		}
	}

	/** From a model projectile's onRemoval(). */
	public static void forgetProjectile(com.example.tudursvehiclemod.entity.projectile.VehicleModelProjectileEntity projectile) {
		java.util.Set<com.example.tudursvehiclemod.entity.projectile.VehicleModelProjectileEntity> set = TICKING_PROJECTILES.get(projectile.level());
		if (set != null) {
			set.remove(projectile);
		}
	}

	public static void onRequest(ServerPlayer player, int distance) {
		if (distance > 0) {
			REQUESTED.put(player.getUUID(), distance);
		} else {
			REQUESTED.remove(player.getUUID());
		}
	}

	public static void onDisconnect(ServerPlayer player) {
		REQUESTED.remove(player.getUUID());
	}

	public static void onEndServerTick(MinecraftServer server) {
		if (REQUESTED.isEmpty()) {
			return;
		}
		VehicleModServerConfig config = VehicleModServerConfig.get();
		if (!config.farVehicleSyncEnabled) {
			return;
		}
		boolean sendVehicles = server.getTickCount() % Math.max(1, config.farVehicleSyncIntervalTicks) == 0;
		boolean sendProjectiles = config.farVehicleSyncMaxProjectiles > 0 && server.getTickCount() % Math.max(1, config.farVehicleSyncProjectileIntervalTicks) == 0;
		if (!sendVehicles && !sendProjectiles) {
			return;
		}
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			Integer requested = REQUESTED.get(player.getUUID());
			if (requested == null || !ServerPlayNetworking.canSend(player, FarVehicleSnapshotPayload.ID)) {
				continue;
			}
			int distance = Math.min(requested, Math.max(0, config.farVehicleSyncMaxDistance));
			ServerPlayNetworking.send(player, new FarVehicleSnapshotPayload(
					sendVehicles, sendVehicles ? collect(player, distance, Math.max(0, config.farVehicleSyncMaxCount)) : List.of(),
					sendProjectiles, sendProjectiles ? collectProjectiles(player, distance, config.farVehicleSyncMaxProjectiles) : List.of()));
		}
	}

	/** Beyond this, a thing is far enough from player not to be sent to it as an entity (the server's view distance, or the type's own tracking range if shorter) - with a margin, so nothing falls in a gap between the two at the edge. A thing that is both is drawn once: the client prefers the entity. */
	private static double nearSq(ServerPlayer player, net.minecraft.world.entity.EntityType<?> type) {
		double viewRange = player.level().getServer().getPlayerList().getViewDistance() * 16.0 * 0.75;
		double trackRange = type.clientTrackingRange() * 16.0 * 0.75;
		double near = Math.min(viewRange, trackRange);
		return near * near;
	}

	private static List<FarVehicleSnapshotPayload.ProjectileEntry> collectProjectiles(ServerPlayer player, int distance, int maxCount) {
		if (distance <= 0 || maxCount <= 0) {
			return List.of();
		}
		ServerLevel level = player.level();
		java.util.Set<com.example.tudursvehiclemod.entity.projectile.VehicleModelProjectileEntity> ticking = TICKING_PROJECTILES.get(level);
		if (ticking == null || ticking.isEmpty()) {
			return List.of();
		}
		double maxSq = (double) distance * distance;
		List<com.example.tudursvehiclemod.entity.projectile.VehicleModelProjectileEntity> found = new ArrayList<>();
		for (var projectile : ticking) {
			if (projectile.isRemoved() || projectile.level() != level) {
				ticking.remove(projectile);
				continue;
			}
			double sq = projectile.distanceToSqr(player);
			if (sq <= maxSq && sq > nearSq(player, projectile.getType()) && projectile.getBulletModel() != null) {
				found.add(projectile);
			}
		}
		found.sort(Comparator.comparingDouble(projectile -> projectile.distanceToSqr(player)));
		List<FarVehicleSnapshotPayload.ProjectileEntry> entries = new ArrayList<>(Math.min(found.size(), maxCount));
		for (var projectile : found) {
			if (entries.size() >= maxCount) {
				break;
			}
			net.minecraft.world.phys.Vec3 motion = projectile.getDeltaMovement();
			net.minecraft.resources.Identifier texture = projectile.getBulletTexture();
			entries.add(new FarVehicleSnapshotPayload.ProjectileEntry(projectile.getId(), projectile.getBulletModel(),
					texture != null ? texture : projectile.getBulletModel(), projectile.getBulletScale(), projectile.tudursvehiclemod$getBulletColor(),
					projectile.getX(), projectile.getY(), projectile.getZ(), (float) motion.x, (float) motion.y, (float) motion.z,
					(float) projectile.getGravity(), projectile.getYRot(), projectile.getXRot()));
		}
		return entries;
	}

	private static List<FarVehicleSnapshotPayload.Entry> collect(ServerPlayer player, int distance, int maxCount) {
		if (distance <= 0 || maxCount <= 0) {
			return List.of();
		}
		ServerLevel level = player.level();
		double maxSq = (double) distance * distance;
		java.util.Set<AbstractVehicleEntity> ticking = TICKING.get(level);
		if (ticking == null) {
			return List.of();
		}
		List<AbstractVehicleEntity> found = new ArrayList<>();
		for (AbstractVehicleEntity vehicle : ticking) {
			if (vehicle.isRemoved() || vehicle.level() != level) {
				ticking.remove(vehicle);
				continue;
			}
			double sq = vehicle.distanceToSqr(player);
			if (sq <= maxSq && sq > nearSq(player, vehicle.getType())
					&& !vehicle.isInvisible() && vehicle.getRootVehicle() != player.getRootVehicle()) {
				found.add(vehicle);
			}
		}
		found.sort(Comparator.comparingDouble(vehicle -> vehicle.distanceToSqr(player)));
		List<FarVehicleSnapshotPayload.Entry> entries = new ArrayList<>(Math.min(found.size(), maxCount));
		for (AbstractVehicleEntity vehicle : found) {
			if (entries.size() >= maxCount) {
				break;
			}
			net.minecraft.resources.Identifier definition = vehicle.getVehicleDefinitionId();
			if (definition == null) {
				continue;
			}
			Quaternionf q = vehicle.tudursvehiclemod$getBodyOrientation();
			entries.add(new FarVehicleSnapshotPayload.Entry(vehicle.getId(), BuiltInRegistries.ENTITY_TYPE.getKey(vehicle.getType()), definition,
					vehicle.getX(), vehicle.getY(), vehicle.getZ(),
					(float) (vehicle.getX() - vehicle.xo), (float) (vehicle.getY() - vehicle.yo), (float) (vehicle.getZ() - vehicle.zo),
					q.x, q.y, q.z, q.w, vehicle.tudursvehiclemod$captureFarPose()));
		}
		return entries;
	}

	/** The players drawing far vehicles (farVehicleSyncEnabled on, and they asked) in level within reach blocks of pos - and within the distance they draw far vehicles to - leaving out those in exclude (the ones already told as tracking players). For a far vehicle's sounds, which would otherwise reach only the players it is sent to as an entity. */
	public static List<ServerPlayer> farListeners(ServerLevel level, net.minecraft.world.phys.Vec3 pos, double reach, java.util.Collection<ServerPlayer> exclude) {
		if (REQUESTED.isEmpty() || reach <= 0 || !VehicleModServerConfig.get().farVehicleSyncEnabled) {
			return List.of();
		}
		int maxDistance = Math.max(0, VehicleModServerConfig.get().farVehicleSyncMaxDistance);
		List<ServerPlayer> found = new ArrayList<>();
		for (ServerPlayer player : level.players()) {
			Integer requested = REQUESTED.get(player.getUUID());
			if (requested == null || exclude.contains(player) || !ServerPlayNetworking.canSend(player, FarVehicleSnapshotPayload.ID)) {
				continue;
			}
			double limit = Math.min(reach, Math.min(requested, maxDistance));
			if (player.distanceToSqr(pos) <= limit * limit) {
				found.add(player);
			}
		}
		return found;
	}

	/** Registers the payloads and server hooks (from ModNetworking). */
	public static void register() {
		net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playC2S().register(FarVehicleRequestPayload.ID, FarVehicleRequestPayload.CODEC);
		net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry.playS2C().register(FarVehicleSnapshotPayload.ID, FarVehicleSnapshotPayload.CODEC);
		ServerPlayNetworking.registerGlobalReceiver(FarVehicleRequestPayload.ID, (payload, context) ->
				context.server().execute(() -> onRequest(context.player(), payload.distance())));
		net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> onDisconnect(handler.player));
		net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_SERVER_TICK.register(FarVehicleSync::onEndServerTick);
	}
}
