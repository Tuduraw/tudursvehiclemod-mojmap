package com.example.tudursvehiclemod.entity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;

/** Every projectile - any ProjectileEntity, vanilla arrows and tridents as well as this mod's own - each world currently has loaded. Kept from Fabric's ServerEntityEvents.ENTITY_LOAD/ENTITY_UNLOAD (registered in VehicleMod), which fire as an entity enters or leaves a world for any reason.
 *
 * <p>This is where a vehicle's hit detection gets its candidates (AbstractVehicleEntity.tudursvehiclemod$updateCustomHitDetection()), and it lets that be skipped outright while a world has none at all. It used to be only a count, with the candidates found by a world query around each vehicle - a box the size of the vehicle's mesh around its position. That query only finds a projectile by where it IS, not by the path it just flew: a shell moving faster than about twice the vehicle's size per tick (a tank gun's 30-40 blocks a tick, against a tank of 8-10 blocks) was outside the box both on the tick before it reached the vehicle and on the tick after it went through, so it was never even checked - it passed straight through and landed beyond. Whether a particular shot was caught depended on where its tick boundaries fell, which is why hits came and went.
 *
 * <p>It also keeps where each projectile was at the end of its last few ticks, so that once one is gone - whatever ended it - the end of its flight is checked against the vehicles around it one more time (onEndWorldTick()).
 *
 * <p>Server thread only; cleared when the server stops. Entries a missed unload would leave behind are dropped as soon as they're seen removed. */
public final class LoadedProjectiles {
	private static final Map<ServerLevel, Set<Projectile>> LOADED = new HashMap<>();

	/** How many end-of-tick positions are kept per projectile: with the position it was removed at, its last PATH_TICKS moves. Two - the move it ended on and the one before it - covers what the checks made while it flew can have left out (see onEndWorldTick()); further back, the vehicles may have turned too far since for the check to mean much. */
	private static final int PATH_TICKS = 2;

	/** End-of-tick positions, oldest first, at most PATH_TICKS. */
	private static final Map<Projectile, ArrayList<Vec3>> PATHS = new IdentityHashMap<>();

	/** Projectiles a vehicle has already taken (AbstractVehicleEntity.tudursvehiclemod$applyProjectileHits()) - not looked at again when they go. */
	private static final Set<Projectile> VEHICLE_HIT = Collections.newSetFromMap(new IdentityHashMap<>());

	/** A projectile that has just gone, with the end of its flight - checked at the end of the world's tick. */
	private record LateCheck(Projectile projectile, List<Vec3> path) {
	}

	private static final Map<ServerLevel, List<LateCheck>> LATE_CHECKS = new HashMap<>();

	private LoadedProjectiles() {
	}

	public static void onLoad(Entity entity, ServerLevel world) {
		if (entity instanceof Projectile projectile) {
			LOADED.computeIfAbsent(world, w -> Collections.newSetFromMap(new IdentityHashMap<>())).add(projectile);
		}
	}

	public static void onUnload(Entity entity, ServerLevel world) {
		if (!(entity instanceof Projectile projectile)) {
			return;
		}
		Set<Projectile> loaded = LOADED.get(world);
		if (loaded != null) {
			loaded.remove(projectile);
		}
		ArrayList<Vec3> recorded = PATHS.remove(projectile);
		boolean hitVehicle = VEHICLE_HIT.remove(projectile);
		Entity.RemovalReason reason = projectile.getRemovalReason();
		// Only a projectile that was used up - not one leaving with its chunk or for another dimension.
		if (hitVehicle || reason == null || !reason.shouldDestroy()) {
			return;
		}
		List<Vec3> path = new ArrayList<>(PATH_TICKS + 2);
		if (recorded != null) {
			path.addAll(recorded.subList(Math.max(0, recorded.size() - PATH_TICKS), recorded.size()));
		}
		Vec3 end = projectile.position();
		if (path.isEmpty()) {
			// Gone within its first tick: from where that tick began.
			path.add(AbstractVehicleEntity.tudursvehiclemod$lastMove(projectile)[0]);
		}
		if (!end.equals(path.get(path.size() - 1))) {
			path.add(end);
		}
		if (path.size() < 2) {
			return;
		}
		// Not right here: this runs in the middle of the entity's removal, and a hit can destroy the vehicle (removing and spawning entities of its own).
		LATE_CHECKS.computeIfAbsent(world, w -> new ArrayList<>()).add(new LateCheck(projectile, path));
	}

	/** Where the projectile was at the end of the tick before the one it ended at from (its last recorded position, if that is from - the start of the move it is making now), or null if that isn't known. */
	static Vec3 positionBefore(Projectile projectile, Vec3 from) {
		ArrayList<Vec3> recorded = PATHS.get(projectile);
		if (recorded == null || recorded.size() < 2 || recorded.get(recorded.size() - 1).distanceToSqr(from) > 1.0e-6) {
			return null;
		}
		return recorded.get(recorded.size() - 2);
	}

	/** Called by AbstractVehicleEntity.tudursvehiclemod$applyProjectileHits() before it discards the projectile a vehicle took. */
	static void markVehicleHit(Projectile projectile) {
		VEHICLE_HIT.add(projectile);
	}

	/** End of each world tick (VehicleMod's END_WORLD_TICK): first the projectiles that went this tick are checked once more along the end of their flight, then every projectile still flying has its position recorded.
	 *
	 * <p>Why look again after the fact: a projectile is checked against vehicles move by move while it flies, but whatever ends it - a block, an entity, a fuse, water - happens inside its own tick, and its removal is final. The move it ended on is checked before a block or entity hit takes effect (mixin.ProjectileFinalSegmentMixin), but nothing else is guaranteed to have looked at it, and anything that keeps a move from being checked in flight is otherwise invisible. Checking the recorded end of the flight once it has finished catches it, whichever way the flight ended. A vehicle found on the path takes the hit's damage: the projectile's explosion has already gone off where it ended, so this is damage only (AbstractVehicleEntity.tudursvehiclemod$tryLateHitOnPath()). */
	public static void onEndWorldTick(ServerLevel world) {
		List<LateCheck> checks = LATE_CHECKS.remove(world);
		if (checks != null) {
			for (LateCheck check : checks) {
				AbstractVehicleEntity.tudursvehiclemod$tryLateHitOnPath(world, check.projectile(), check.path());
			}
		}
		Set<Projectile> loaded = LOADED.get(world);
		if (loaded == null || loaded.isEmpty()) {
			return;
		}
		for (Projectile projectile : in(world)) {
			ArrayList<Vec3> path = PATHS.computeIfAbsent(projectile, p -> new ArrayList<>(PATH_TICKS + 1));
			Vec3 position = projectile.position();
			// Only a tick it actually moved: one resting on the ground, or in a chunk too far away to be ticked, would otherwise fill its path with the same point.
			if (!path.isEmpty() && path.get(path.size() - 1).equals(position)) {
				continue;
			}
			path.add(position);
			while (path.size() > PATH_TICKS) {
				path.remove(0);
			}
		}
	}

	/** True if world has at least one projectile loaded. */
	public static boolean any(ServerLevel world) {
		Set<Projectile> loaded = LOADED.get(world);
		return loaded != null && !loaded.isEmpty();
	}

	/** The projectiles world has loaded right now. Live view - don't keep it, and don't add or remove anything while iterating except through this class. */
	public static Set<Projectile> in(ServerLevel world) {
		Set<Projectile> loaded = LOADED.get(world);
		if (loaded == null) {
			return Set.of();
		}
		loaded.removeIf(projectile -> {
			if (projectile.isRemoved()) {
				PATHS.remove(projectile);
				VEHICLE_HIT.remove(projectile);
				return true;
			}
			return false;
		});
		return loaded;
	}

	public static void clear() {
		LOADED.clear();
		PATHS.clear();
		VEHICLE_HIT.clear();
		LATE_CHECKS.clear();
	}
}
