package com.example.tudursvehiclemod.client.sound;

import com.example.tudursvehiclemod.entity.AbstractVehicleEntity;
import org.lwjgl.openal.AL10;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** One looping OpenAL source per vehicle entity that has an engine_sound configured (see VehicleDefinition.engineSound()) and is currently loaded in the client - or drawn beyond its entity range as a far vehicle (client.farvehicle.FarVehicleProxies), with the engine's state the server reports. */
public final class VehicleEngineSoundManager {

	/** Gain at zero throttle. */
	private static final float IDLE_GAIN = 0.0f;
	private static final float FULL_GAIN = 1.0f;
	private static final float IDLE_PITCH = 0.8f;
	private static final float FULL_PITCH = 1.3f;
	/** How quickly the AUDIBLE gain/pitch follow throttle changes, per tick. */
	private static final float SOUND_SMOOTHING = 0.2f;

	private static final Map<Integer, Integer> engineSources = new HashMap<>();
	private static final Map<Integer, Float> smoothedThrottle = new HashMap<>();

	private VehicleEngineSoundManager() {
	}

	public static void tick(Minecraft client) {
		if (client.level == null) {
			stopAll();
			return;
		}

		Set<Integer> stillPresent = new HashSet<>();
		// Vehicles in this client's world, then the far vehicles drawn beyond it (client.farvehicle.FarVehicleProxies) - keyed by the server's entity id either way, so a vehicle crossing between the two keeps its sound going.
		Map<Integer, AbstractVehicleEntity> vehicles = new HashMap<>();
		for (Entity entity : client.level.entitiesForRendering()) {
			if (entity instanceof AbstractVehicleEntity vehicle) {
				vehicles.put(vehicle.getId(), vehicle);
			}
		}
		com.example.tudursvehiclemod.client.farvehicle.FarVehicleProxies.visibleById(client.level).forEach(vehicles::putIfAbsent);
		for (Map.Entry<Integer, AbstractVehicleEntity> present : vehicles.entrySet()) {
			AbstractVehicleEntity vehicle = present.getValue();
			var engineSoundName = vehicle.getDefinition().engineSound();
			if (engineSoundName.isEmpty()) {
				continue;
			}
			Integer bufferId = AddonSoundLoader.getBuffer(engineSoundName.get());
			if (bufferId == null) {
				continue;
			}

			int entityId = present.getKey();
			stillPresent.add(entityId);
			int source = engineSources.computeIfAbsent(entityId, id -> createLoopingSource(bufferId));
			updateSource(source, vehicle, entityId);
		}

		// Stop/clean up sources for any vehicle no longer present (unloaded, removed, or its engine_sound/buffer went away on a resource reload).
		engineSources.entrySet().removeIf(entry -> {
			if (!stillPresent.contains(entry.getKey())) {
				AL10.alSourceStop(entry.getValue());
				AL10.alDeleteSources(entry.getValue());
				smoothedThrottle.remove(entry.getKey());
				return true;
			}
			return false;
		});
	}

	private static int createLoopingSource(int bufferId) {
		int source = AL10.alGenSources();
		AL10.alSourcei(source, AL10.AL_BUFFER, bufferId);
		AL10.alSourcei(source, AL10.AL_LOOPING, AL10.AL_TRUE);
		// Engine sound remained faintly audible
		// from far beyond where it should have fully faded out: OpenAL's
		// own DEFAULT distance model is AL_INVERSE_DISTANCE (NOT the
		// _CLAMPED variant), whose own gain formula asymptotically
		// approaches (but mathematically never actually reaches) zero as
		// distance grows - and, critically, that formula doesn't even
		// reference AL_MAX_DISTANCE at all (only the _CLAMPED distance
		// models actually use it to cut attenuation off at that point).
		// Setting AL_ROLLOFF_FACTOR to 0 here disables OpenAL's own
		// built-in distance attenuation for this source ENTIRELY (this is
		// a per-source property, so this can't affect any other sound in
		// the game) - distance-based gain is instead computed by hand in
		// updateSource() below (see VanillaStyleSoundAttenuation's own
		// doc), independent of whatever the game's own global AL distance
		// model happens to be set to.
		AL10.alSourcef(source, AL10.AL_ROLLOFF_FACTOR, 0.0f);
		AL10.alSourcePlay(source);
		return source;
	}

	/** "Un-scaled" (engine_sound_volume=1.0 exactly) reference/max distances - doubled from their
	 * original values (4.0/64.0). A vehicle whose own engine_sound_volume resolves to 1.0 (see
	 * VehicleDefinition's own tudursvehiclemod$effectiveEngineSoundVolume() doc for which vehicle
	 * types default there) is audible at exactly this doubled range; one that resolves to 3.0 (the
	 * other default, or any explicitly-authored value) gets these further multiplied by 3, and so
	 * on for any other explicit value - see VanillaStyleSoundAttenuation's own doc for exactly how. */
	private static final float BASE_REFERENCE_DISTANCE = 8.0f;
	private static final float BASE_MAX_DISTANCE = 128.0f;

	private static void updateSource(int source, AbstractVehicleEntity vehicle, int entityId) {
		Vec3 pos = vehicle.position();
		AL10.alSource3f(source, AL10.AL_POSITION, (float) pos.x, (float) pos.y, (float) pos.z);
		Vec3 velocity = vehicle.getDeltaMovement();
		AL10.alSource3f(source, AL10.AL_VELOCITY, (float) velocity.x, (float) velocity.y, (float) velocity.z);

		float rawThrottle = vehicle.tudursvehiclemod$getEngineSoundThrottle();
		float previous = smoothedThrottle.getOrDefault(entityId, rawThrottle);
		float smoothed = previous + (rawThrottle - previous) * SOUND_SMOOTHING;
		smoothedThrottle.put(entityId, smoothed);

		float throttleGain = Mth.lerp(smoothed, IDLE_GAIN, FULL_GAIN);
		float pitch = Mth.lerp(smoothed, IDLE_PITCH, FULL_PITCH);

		// Follows MC Heli's own documented
		// SoundVolume convention (see VanillaStyleSoundAttenuation's own
		// doc), applied to this vehicle's own configured
		// engine_sound_volume (3.0 default) - using the actual
		// camera/listener position (not necessarily the same as the
		// player entity's own feet position - e.g. a spectator or a
		// detached free-look camera).
		float engineSoundVolume = vehicle.getDefinition().tudursvehiclemod$effectiveEngineSoundVolume();
		Minecraft client = Minecraft.getInstance();
		Vec3 listenerPos = client.gameRenderer.getMainCamera().position();
		double distance = pos.distanceTo(listenerPos);
		float distanceGain = VanillaStyleSoundAttenuation.computeGain(distance, engineSoundVolume, BASE_REFERENCE_DISTANCE, BASE_MAX_DISTANCE);

		AL10.alSourcef(source, AL10.AL_GAIN, throttleGain * distanceGain);
		AL10.alSourcef(source, AL10.AL_PITCH, pitch);
	}

	/** Stops and releases every active engine sound source. */
	public static void stopAll() {
		for (int source : engineSources.values()) {
			AL10.alSourceStop(source);
			AL10.alDeleteSources(source);
		}
		engineSources.clear();
		smoothedThrottle.clear();
	}
}
