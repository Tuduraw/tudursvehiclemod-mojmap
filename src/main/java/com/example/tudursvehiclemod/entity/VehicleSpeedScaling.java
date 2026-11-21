package com.example.tudursvehiclemod.entity;

import com.example.tudursvehiclemod.VehicleModServerConfig;
import com.example.tudursvehiclemod.network.ServerSettingsSyncPayload;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;

/** The server's vehicle speed multiplier: every vehicle's top speed - max_speed, and the other speeds given in absolute terms (a folded swing wing's SweepWingSpeed, a submarine's dive_max_speed, a catapult launch's speed boost) - times this. Speeds given as a fraction of the top speed (throttle steps, stall speed, autopilot cruise) follow along by themselves; acceleration, turning and everything else about handling stay as the vehicle defines them.
 *
 * <p>One multiplier for every vehicle (VehicleModServerConfig.vehicleSpeedMultiplier), and optionally one per entity type (vehicleSpeedMultiplierByType, keyed by entity type id: "tudursvehiclemod:aircraft", or "aircraft" for this mod's own; an addon's vehicle types work the same way). A type without its own entry uses the common one.
 *
 * <p>A vehicle's movement is worked out on the pilot's client as well as on the server, so the client has to use the same multipliers: the server sends them when a player joins and whenever they change (ServerSettingsSyncPayload), and a client uses the last ones it was sent (1 for everything until then). */
public final class VehicleSpeedScaling {

	private VehicleSpeedScaling() {
	}

	/** Lowest multiplier used, whatever the file says: at 0 nothing could move at all, and several speed calculations divide by the top speed. */
	public static final float MIN = 0.05f;

	/** The multipliers in effect: the common one, and those for types that have their own (keys are full entity type ids). */
	public static final class Settings {

		static final Settings DEFAULT = new Settings(1f, Map.of());

		private final float common;
		private final Map<String, Float> byType;
		/** Looked-up types, so a vehicle's many speed calculations each tick don't each look its id up again. */
		private final Map<EntityType<?>, Float> resolved = new ConcurrentHashMap<>();

		public Settings(float common, Map<String, Float> byType) {
			this.common = clamp(common, 1f);
			Map<String, Float> clean = new HashMap<>();
			if (byType != null) {
				for (Map.Entry<String, Float> entry : byType.entrySet()) {
					if (entry.getKey() != null && entry.getValue() != null && Float.isFinite(entry.getValue())) {
						clean.put(normalizeKey(entry.getKey()), clamp(entry.getValue(), this.common));
					}
				}
			}
			this.byType = Map.copyOf(clean);
		}

		/** The common multiplier: for every type without its own. */
		public float common() {
			return this.common;
		}

		/** The per-type multipliers, by full entity type id. */
		public Map<String, Float> byType() {
			return this.byType;
		}

		/** The multiplier for vehicles of type. */
		public float forType(EntityType<?> type) {
			if (this.byType.isEmpty() || type == null) {
				return this.common;
			}
			return this.resolved.computeIfAbsent(type, t -> this.byType.getOrDefault(BuiltInRegistries.ENTITY_TYPE.getKey(t).toString(), this.common));
		}

		@Override
		public boolean equals(Object other) {
			return other instanceof Settings that && this.common == that.common && this.byType.equals(that.byType);
		}

		@Override
		public int hashCode() {
			return Float.hashCode(this.common) * 31 + this.byType.hashCode();
		}
	}

	/** "Aircraft" -> "tudursvehiclemod:aircraft"; a full id is kept as it is (lower case). */
	public static String normalizeKey(String key) {
		String trimmed = key.trim().toLowerCase(Locale.ROOT);
		return trimmed.contains(":") ? trimmed : "tudursvehiclemod:" + trimmed;
	}

	private static float clamp(float value, float fallback) {
		return Float.isFinite(value) ? Math.max(MIN, value) : fallback;
	}

	/** What the server last sent this client. */
	private static volatile Settings client = Settings.DEFAULT;
	/** The server's settings as last read from its config (re-read every few ticks - onEndServerTick()). */
	private static volatile Settings server;

	/** The multiplier for vehicle, on its side: the server's own settings, or on a client what the server sent. */
	public static float multiplier(Entity vehicle) {
		return settings(vehicle.level()).forType(vehicle.getType());
	}

	/** The multiplier for vehicles of type in level. */
	public static float multiplier(Level level, EntityType<?> type) {
		return settings(level).forType(type);
	}

	/** The common multiplier on level's side - the one for every type without its own. */
	public static float multiplier(Level level) {
		return settings(level).common();
	}

	/** speed (blocks/tick or km/h - any unit) times vehicle's multiplier. */
	public static float scale(Entity vehicle, float speed) {
		return speed * multiplier(vehicle);
	}

	/** The settings on level's side. */
	public static Settings settings(Level level) {
		return level != null && level.isClientSide() ? client : serverSettings();
	}

	/** The server's settings. */
	public static Settings serverSettings() {
		Settings current = server;
		if (current == null) {
			current = readConfig();
			server = current;
		}
		return current;
	}

	private static Settings readConfig() {
		VehicleModServerConfig config = VehicleModServerConfig.get();
		Map<String, Float> byType = new HashMap<>();
		if (config.vehicleSpeedMultiplierByType != null) {
			for (Map.Entry<String, Double> entry : config.vehicleSpeedMultiplierByType.entrySet()) {
				if (entry.getValue() != null) {
					byType.put(entry.getKey(), entry.getValue().floatValue());
				}
			}
		}
		return new Settings((float) config.vehicleSpeedMultiplier, byType);
	}

	/** From the client's receiver of ServerSettingsSyncPayload (and back to 1 for everything on leaving a server). */
	public static void setClientSettings(Settings settings) {
		client = settings != null ? settings : Settings.DEFAULT;
	}

	/** A player joining: tell them the current settings. */
	public static void sendTo(ServerPlayer player) {
		if (ServerPlayNetworking.canSend(player, ServerSettingsSyncPayload.ID)) {
			Settings current = serverSettings();
			ServerPlayNetworking.send(player, new ServerSettingsSyncPayload(current.common(), current.byType()));
		}
	}

	/** Every 10 server ticks: re-reads the config, and when the settings have changed (the server settings screen, an edit by another mod) tells every player. */
	public static void onEndServerTick(MinecraftServer server) {
		if (server.getTickCount() % 10 != 0) {
			return;
		}
		Settings now = readConfig();
		if (now.equals(VehicleSpeedScaling.server)) {
			return;
		}
		VehicleSpeedScaling.server = now;
		for (ServerPlayer player : server.getPlayerList().getPlayers()) {
			sendTo(player);
		}
	}

	/** The server stopping (a single-player world closing): the next server starts afresh. */
	public static void onServerStopped(MinecraftServer server) {
		VehicleSpeedScaling.server = null;
	}
}
