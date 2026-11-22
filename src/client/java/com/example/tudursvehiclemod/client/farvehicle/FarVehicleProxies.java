package com.example.tudursvehiclemod.client.farvehicle;

import com.example.tudursvehiclemod.client.VehicleModClient;
import com.example.tudursvehiclemod.client.VehicleModConfig;
import com.example.tudursvehiclemod.entity.AbstractVehicleEntity;
import com.example.tudursvehiclemod.network.FarVehicleRequestPayload;
import com.example.tudursvehiclemod.network.FarVehicleSnapshotPayload;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;

/** Vehicles beyond this client's entity range, drawn over an LOD mod's distant terrain (VehicleModConfig.renderFarVehicles; the server must allow it too - entity.FarVehicleSync).
 *
 * <p>Each one the server reports is a stand-in: a vehicle entity of the same type and definition, made on this client but never added to the world, so it doesn't tick, collide or make a sound - it is only drawn (mixin.LevelRendererFarVehicleMixin), at the place, heading and attitude last reported, carried on smoothly between reports by the motion that came with them. It goes away as soon as the real vehicle comes into entity range (the real one is drawn instead), or when the server stops reporting it.
 *
 * <p>Its moving parts are drawn as the server last reported them (entity.FarVehiclePose): landing gear, hatches, canopies, weapon bays and folding wings at their reported progress, turrets at their reported aim (both eased towards each report), propellers and rotors turning at the reported rate, and a wreck as a wreck. What only the client works out for a vehicle near it (wheel and track turning, recoil, wakes, search light beams, smoke) isn't drawn. Its engine sound plays (client.sound.VehicleEngineSoundManager), as do its weapons' fire sounds (sent to far listeners by entity.FarVehicleSync.farListeners()). It is lit as by open sky.
 *
 * <p>Projectiles drawn with a model (missiles, bombs, shells) get stand-ins the same way, carried on between reports by their motion and gravity. One that has hit something goes on for up to one report interval (4 ticks by default) before the next report drops it. */
public final class FarVehicleProxies {

	private FarVehicleProxies() {
	}

	/** One stand-in: the entity drawn, and the last report about it. */
	private static final class Proxy {
		final AbstractVehicleEntity entity;
		Vec3 reportedPos;
		Vec3 motion;
		final Quaternionf reportedOrientation = new Quaternionf();
		int ticksSinceReport;
		/** How far the drawn position was from the new report when it came in; eased away over a few ticks instead of jumping. */
		Vec3 correction = Vec3.ZERO;

		Proxy(AbstractVehicleEntity entity) {
			this.entity = entity;
		}
	}

	/** A projectile's stand-in and the last report about it. */
	private static final class ProjectileProxy {
		final com.example.tudursvehiclemod.entity.projectile.VehicleModelProjectileEntity entity;
		Vec3 reportedPos;
		Vec3 motion;
		double gravity;
		float yaw;
		float pitch;
		int ticksSinceReport;
		Vec3 correction = Vec3.ZERO;

		ProjectileProxy(com.example.tudursvehiclemod.entity.projectile.VehicleModelProjectileEntity entity) {
			this.entity = entity;
		}
	}

	/** By the projectile's entity id on the server. */
	private static final Map<Integer, ProjectileProxy> PROJECTILES = new HashMap<>();

	/** By the vehicle's entity id on the server. */
	private static final Map<Integer, Proxy> PROXIES = new HashMap<>();
	private static ClientLevel proxyLevel;
	/** The distance last asked of the server (-1: nothing asked yet on this connection). */
	private static int requestedDistance = -1;

	/** Ticks over which a correction from a new report is eased away. */
	private static final double CORRECTION_TICKS = 5.0;

	public static void register() {
		ClientPlayNetworking.registerGlobalReceiver(FarVehicleSnapshotPayload.ID, (payload, context) ->
				context.client().execute(() -> onSnapshot(context.client(), payload)));
		net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> client.execute(FarVehicleProxies::reset));
		net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(FarVehicleProxies::onEndClientTick);
		// A far vehicle coming into entity range: the real one carries on with the parts as its stand-in had them (AbstractVehicleEntity.tudursvehiclemod$setPendingFarPartsSource()).
		net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientEntityEvents.ENTITY_LOAD.register((entity, level) -> {
			if (entity instanceof AbstractVehicleEntity vehicle && level == proxyLevel) {
				Proxy proxy = PROXIES.get(vehicle.getId());
				if (proxy != null) {
					vehicle.tudursvehiclemod$setPendingFarPartsSource(proxy.entity);
				}
			}
		});
	}

	private static void reset() {
		PROXIES.clear();
		PROJECTILES.clear();
		proxyLevel = null;
		requestedDistance = -1;
	}

	/** Whether this client draws far vehicles at all (its own setting). */
	public static boolean enabled() {
		VehicleModConfig config = VehicleModClient.getConfig();
		return config != null && config.renderFarVehicles;
	}

	/** The distance (blocks) this client asks for and draws to. */
	public static int distance() {
		VehicleModConfig config = VehicleModClient.getConfig();
		return config == null ? 0 : Math.max(0, config.farVehicleRenderDistance);
	}

	/** The stand-ins to draw this frame (only those whose real vehicle isn't in this client's world). */
	public static Collection<AbstractVehicleEntity> visible(ClientLevel level) {
		if (level != proxyLevel || PROXIES.isEmpty()) {
			return java.util.List.of();
		}
		java.util.List<AbstractVehicleEntity> list = new java.util.ArrayList<>(PROXIES.size());
		for (Map.Entry<Integer, Proxy> entry : PROXIES.entrySet()) {
			if (level.getEntity(entry.getKey()) == null) {
				list.add(entry.getValue().entity);
			}
		}
		return list;
	}

	/** The same as visible(), by the vehicle's entity id on the server (for its engine sound - client.sound.VehicleEngineSoundManager). */
	public static Map<Integer, AbstractVehicleEntity> visibleById(ClientLevel level) {
		if (level != proxyLevel || PROXIES.isEmpty()) {
			return Map.of();
		}
		Map<Integer, AbstractVehicleEntity> map = new HashMap<>();
		for (Map.Entry<Integer, Proxy> entry : PROXIES.entrySet()) {
			if (level.getEntity(entry.getKey()) == null) {
				map.put(entry.getKey(), entry.getValue().entity);
			}
		}
		return map;
	}

	/** The projectile stand-ins to draw this frame (only those whose real projectile isn't in this client's world). */
	public static Collection<com.example.tudursvehiclemod.entity.projectile.VehicleModelProjectileEntity> visibleProjectiles(ClientLevel level) {
		if (level != proxyLevel || PROJECTILES.isEmpty()) {
			return java.util.List.of();
		}
		java.util.List<com.example.tudursvehiclemod.entity.projectile.VehicleModelProjectileEntity> list = new java.util.ArrayList<>(PROJECTILES.size());
		for (Map.Entry<Integer, ProjectileProxy> entry : PROJECTILES.entrySet()) {
			if (level.getEntity(entry.getKey()) == null) {
				list.add(entry.getValue().entity);
			}
		}
		return list;
	}

	public static boolean hasAny() {
		return !PROXIES.isEmpty() || !PROJECTILES.isEmpty();
	}

	private static void onSnapshot(Minecraft client, FarVehicleSnapshotPayload payload) {
		ClientLevel level = client.level;
		if (level == null || !enabled()) {
			PROXIES.clear();
			PROJECTILES.clear();
			return;
		}
		if (level != proxyLevel) {
			PROXIES.clear();
			PROJECTILES.clear();
			proxyLevel = level;
		}
		if (payload.hasProjectiles()) {
			onProjectiles(level, payload.projectiles());
		}
		if (!payload.hasVehicles()) {
			return;
		}
		Set<Integer> seen = new HashSet<>();
		for (FarVehicleSnapshotPayload.Entry entry : payload.vehicles()) {
			seen.add(entry.entityId());
			Proxy proxy = PROXIES.get(entry.entityId());
			if (proxy != null && !entry.definition().equals(proxy.entity.getVehicleDefinitionId())) {
				proxy = null;
			}
			Vec3 pos = new Vec3(entry.x(), entry.y(), entry.z());
			Quaternionf orientation = new Quaternionf(entry.qx(), entry.qy(), entry.qz(), entry.qw()).normalize();
			if (proxy == null) {
				AbstractVehicleEntity entity = create(level, entry);
				if (entity == null) {
					continue;
				}
				proxy = new Proxy(entity);
				entity.tudursvehiclemod$applyExternalPose(pos, orientation);
				entity.setOldPosAndRot();
				entity.tudursvehiclemod$setFarPose(entry.pose(), true);
				PROXIES.put(entry.entityId(), proxy);
			} else {
				proxy.correction = proxy.entity.position().subtract(pos);
				proxy.entity.tudursvehiclemod$setFarPose(entry.pose(), false);
			}
			proxy.reportedPos = pos;
			proxy.motion = new Vec3(entry.vx(), entry.vy(), entry.vz());
			proxy.reportedOrientation.set(orientation);
			proxy.ticksSinceReport = 0;
		}
		PROXIES.keySet().retainAll(seen);
	}

	private static void onProjectiles(ClientLevel level, java.util.List<FarVehicleSnapshotPayload.ProjectileEntry> entries) {
		Set<Integer> seen = new HashSet<>();
		for (FarVehicleSnapshotPayload.ProjectileEntry entry : entries) {
			seen.add(entry.entityId());
			ProjectileProxy proxy = PROJECTILES.get(entry.entityId());
			Vec3 pos = new Vec3(entry.x(), entry.y(), entry.z());
			if (proxy == null) {
				var entity = com.example.tudursvehiclemod.registry.ModEntityTypes.VEHICLE_MODEL_PROJECTILE.create(level, EntitySpawnReason.LOAD);
				if (entity == null) {
					continue;
				}
				entity.tudursvehiclemod$setBulletAppearance(entry.model(), entry.texture(), entry.scale());
				entity.tudursvehiclemod$setBulletColors(entry.color(), entry.color());
				entity.setPos(pos);
				entity.setYRot(entry.yaw());
				entity.setXRot(entry.pitch());
				entity.setOldPosAndRot();
				proxy = new ProjectileProxy(entity);
				PROJECTILES.put(entry.entityId(), proxy);
			} else {
				proxy.correction = proxy.entity.position().subtract(pos);
			}
			proxy.reportedPos = pos;
			proxy.motion = new Vec3(entry.vx(), entry.vy(), entry.vz());
			proxy.gravity = entry.gravity();
			proxy.yaw = entry.yaw();
			proxy.pitch = entry.pitch();
			proxy.ticksSinceReport = 0;
		}
		PROJECTILES.keySet().retainAll(seen);
	}

	private static AbstractVehicleEntity create(ClientLevel level, FarVehicleSnapshotPayload.Entry entry) {
		EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getOptional(entry.entityType()).orElse(null);
		if (type == null) {
			return null;
		}
		Entity entity;
		try {
			entity = type.create(level, EntitySpawnReason.LOAD);
		} catch (RuntimeException e) {
			return null;
		}
		if (!(entity instanceof AbstractVehicleEntity vehicle)) {
			return null;
		}
		vehicle.setVehicleDefinitionId(entry.definition());
		return vehicle;
	}

	private static void onEndClientTick(Minecraft client) {
		if (client.level == null || client.player == null) {
			return;
		}
		// Ask the server (again) when the setting changes - on joining too.
		int wanted = enabled() ? distance() : 0;
		if (wanted != requestedDistance && ClientPlayNetworking.canSend(FarVehicleRequestPayload.ID)) {
			ClientPlayNetworking.send(new FarVehicleRequestPayload(wanted));
			requestedDistance = wanted;
			if (wanted == 0) {
				PROXIES.clear();
				PROJECTILES.clear();
			}
		}
		if (client.level != proxyLevel) {
			return;
		}
		for (ProjectileProxy proxy : PROJECTILES.values()) {
			proxy.ticksSinceReport++;
			int t = Math.min(proxy.ticksSinceReport, 20);
			double ease = Math.max(0.0, 1.0 - proxy.ticksSinceReport / CORRECTION_TICKS);
			// Its motion and gravity carry it on, as they do the real one (a guided missile's turns show at the next report).
			Vec3 pos = proxy.reportedPos.add(proxy.motion.scale(t)).add(0, -0.5 * proxy.gravity * t * t, 0).add(proxy.correction.scale(ease));
			proxy.entity.setOldPosAndRot();
			proxy.entity.setPos(pos);
			Vec3 heading = proxy.motion.add(0, -proxy.gravity * t, 0);
			if (heading.lengthSqr() > 1.0E-6) {
				// Vanilla projectile angles: yaw from the horizontal motion, pitch from the climb.
				proxy.entity.setYRot((float) Math.toDegrees(Math.atan2(heading.x, heading.z)));
				proxy.entity.setXRot((float) Math.toDegrees(Math.atan2(heading.y, heading.horizontalDistance())));
			} else {
				proxy.entity.setYRot(proxy.yaw);
				proxy.entity.setXRot(proxy.pitch);
			}
			proxy.entity.tickCount++;
		}
		for (Proxy proxy : PROXIES.values()) {
			proxy.ticksSinceReport++;
			// Gone quiet (the server stopped reporting without a fresh list, e.g. after a long stall): stop at the last place instead of sliding on.
			Vec3 motion = proxy.ticksSinceReport > 60 ? Vec3.ZERO : proxy.motion;
			double ease = Math.max(0.0, 1.0 - proxy.ticksSinceReport / CORRECTION_TICKS);
			Vec3 pos = proxy.reportedPos.add(motion.scale(Math.min(proxy.ticksSinceReport, 60))).add(proxy.correction.scale(ease));
			Quaternionf orientation = proxy.entity.tudursvehiclemod$getBodyOrientation().slerp(proxy.reportedOrientation, 0.35f);
			proxy.entity.setOldPosAndRot();
			proxy.entity.tudursvehiclemod$applyExternalPose(pos, orientation);
			proxy.entity.tudursvehiclemod$stepFarPose();
			proxy.entity.tickCount++;
		}
	}
}
