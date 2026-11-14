package com.example.tudursvehiclemod.entity;

import com.example.tudursvehiclemod.asset.RunwayDefinition;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** The solid surface of every runway, given straight to the game's collision lookup (Level.getEntityCollisions(), see mixin.RunwayDeckCollisionMixin) instead of being made of invisible entities.
 *
 * <p>The boxes are the ones the runway tiles (CarrierRunwayPlatformEntity) used to be: the same grid, the same size, one block deep under the runway's surface. Only the few under the box being asked about are made, each time it is asked, from the vehicle's position and yaw at that moment - on the server from its own, on a client from what it shows (as the tiles' client-side placement already did). So nothing has to be spawned, ticked, synced, saved or kept in step with the vehicle, and nothing is missing while a chunk's entities load. */
public final class RunwayDeckCollision {

	private RunwayDeckCollision() {
	}

	/** Vehicles with at least one runway, per world (each side has its own), added as they tick. */
	private static final Map<Level, Set<AbstractVehicleEntity>> RUNWAY_VEHICLES = Collections.synchronizedMap(new WeakHashMap<>());

	/** From the vehicle's own tick, on both sides, while its definition has runways. */
	static void markTicking(AbstractVehicleEntity vehicle) {
		RUNWAY_VEHICLES.computeIfAbsent(vehicle.level(), level -> Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>())).add(vehicle);
	}

	/** From the vehicle's onRemove() (both sides). */
	static void forget(AbstractVehicleEntity vehicle) {
		Set<AbstractVehicleEntity> vehicles = RUNWAY_VEHICLES.get(vehicle.level());
		if (vehicles != null) {
			vehicles.remove(vehicle);
		}
	}

	public static void clear() {
		RUNWAY_VEHICLES.clear();
	}

	/** The vehicles with runways currently ticking in level (for the client's deck carry - client.RunwayDeckCarry). */
	public static java.util.Collection<AbstractVehicleEntity> vehiclesIn(Level level) {
		Set<AbstractVehicleEntity> vehicles = RUNWAY_VEHICLES.get(level);
		return vehicles == null ? List.of() : vehicles;
	}

	/** Adds to shapes the runway boxes that intersect box, for entity (null: no particular entity) - what Level.getEntityCollisions() would have found had the tiles been there. */
	public static void collect(Level level, Entity entity, AABB box, List<VoxelShape> shapes) {
		Set<AbstractVehicleEntity> vehicles = RUNWAY_VEHICLES.get(level);
		if (vehicles == null || vehicles.isEmpty()) {
			return;
		}
		for (AbstractVehicleEntity vehicle : vehicles) {
			if (vehicle.isRemoved()) {
				vehicles.remove(vehicle);
				continue;
			}
			if (entity != null && (entity == vehicle || entity.isSpectator() || vehicle.hasPassenger(entity))) {
				continue;
			}
			collectFor(vehicle, box, shapes);
		}
	}

	private static void collectFor(AbstractVehicleEntity vehicle, AABB box, List<VoxelShape> shapes) {
		if (vehicle.tudursvehiclemod$isDestroyed()) {
			return;
		}
		List<RunwayDefinition> runways = vehicle.getDefinition().runways();
		if (runways.isEmpty()) {
			return;
		}
		double yawRad = Math.toRadians(vehicle.getYRot());
		double forwardX = -Math.sin(yawRad);
		double forwardZ = Math.cos(yawRad);
		double rightX = forwardZ;
		double rightZ = -forwardX;
		// The box's corners in the vehicle's frame (x along right, z along forward), as a range on each axis.
		double[] cornersX = {box.minX - vehicle.getX(), box.maxX - vehicle.getX()};
		double[] cornersZ = {box.minZ - vehicle.getZ(), box.maxZ - vehicle.getZ()};
		double localMinX = Double.POSITIVE_INFINITY, localMaxX = Double.NEGATIVE_INFINITY;
		double localMinZ = Double.POSITIVE_INFINITY, localMaxZ = Double.NEGATIVE_INFINITY;
		for (double cx : cornersX) {
			for (double cz : cornersZ) {
				double lx = cx * rightX + cz * rightZ;
				double lz = cx * forwardX + cz * forwardZ;
				localMinX = Math.min(localMinX, lx);
				localMaxX = Math.max(localMaxX, lx);
				localMinZ = Math.min(localMinZ, lz);
				localMaxZ = Math.max(localMaxZ, lz);
			}
		}
		for (int runwayIndex = 0; runwayIndex < runways.size(); runwayIndex++) {
			RunwayDefinition baseRunway = runways.get(runwayIndex);
			if (baseRunway.hatchGated() && !vehicle.isHatchOpen()) {
				continue;
			}
			RunwayDefinition runway = baseRunway.tudursvehiclemod$withHatchProgress(vehicle.tudursvehiclemod$getRunwayHatchProgress(runwayIndex));
			double surfaceY = vehicle.getY() + runway.heightY();
			if (box.minY >= surfaceY || box.maxY <= surfaceY - TILE_HEIGHT) {
				continue;
			}
			float tileSize = AbstractVehicleEntity.tudursvehiclemod$runwayTileSize(runway.width());
			double half = tileSize / 2.0;
			double minZ = Math.min(runway.startZ(), runway.endZ());
			double maxZ = Math.max(runway.startZ(), runway.endZ());
			double length = maxZ - minZ;
			double spacing = tileSize * 0.7;
			int columns = Math.max(1, (int) Math.ceil(runway.width() / spacing));
			int rows = Math.max(1, (int) Math.ceil(length / spacing));
			double firstX = runway.centerX() - runway.width() / 2.0 + half;
			double stepX = (runway.width() - tileSize) / Math.max(1, columns - 1);
			double firstZ = minZ + half;
			double stepZ = (length - tileSize) / Math.max(1, rows - 1);
			// A tile is an axis-aligned box in the WORLD, so in the vehicle's frame it reaches up to half * (|cos| + |sin|) from its centre; only those whose centre is that close to the box's range can touch it.
			double reach = half * (Math.abs(forwardX) + Math.abs(forwardZ));
			int colFrom = 0, colTo = columns - 1, rowFrom = 0, rowTo = rows - 1;
			if (columns > 1) {
				colFrom = Math.max(0, (int) Math.floor((localMinX - reach - firstX) / stepX));
				colTo = Math.min(columns - 1, (int) Math.ceil((localMaxX + reach - firstX) / stepX));
			} else if (localMaxX + reach < runway.centerX() || localMinX - reach > runway.centerX()) {
				continue;
			}
			if (rows > 1) {
				rowFrom = Math.max(0, (int) Math.floor((localMinZ - reach - firstZ) / stepZ));
				rowTo = Math.min(rows - 1, (int) Math.ceil((localMaxZ + reach - firstZ) / stepZ));
			} else if (localMaxZ + reach < (minZ + maxZ) / 2.0 || localMinZ - reach > (minZ + maxZ) / 2.0) {
				continue;
			}
			for (int row = rowFrom; row <= rowTo; row++) {
				double tileLocalZ = rows == 1 ? (minZ + maxZ) / 2.0 : firstZ + row * stepZ;
				for (int col = colFrom; col <= colTo; col++) {
					double tileLocalX = columns == 1 ? runway.centerX() : firstX + col * stepX;
					double worldX = vehicle.getX() + tileLocalX * rightX + tileLocalZ * forwardX;
					double worldZ = vehicle.getZ() + tileLocalX * rightZ + tileLocalZ * forwardZ;
					if (worldX + half <= box.minX || worldX - half >= box.maxX || worldZ + half <= box.minZ || worldZ - half >= box.maxZ) {
						continue;
					}
					shapes.add(Shapes.create(worldX - half, surfaceY - TILE_HEIGHT, worldZ - half, worldX + half, surfaceY, worldZ + half));
				}
			}
		}
	}

	/** How deep under the surface the boxes reach - the tiles' height. */
	private static final double TILE_HEIGHT = 1.0;

	/** Collision lookups that found nothing come back as List.of(), which can't be added to. */
	public static List<VoxelShape> withRunways(Level level, Entity entity, AABB box, List<VoxelShape> found) {
		Set<AbstractVehicleEntity> vehicles = RUNWAY_VEHICLES.get(level);
		if (vehicles == null || vehicles.isEmpty()) {
			return found;
		}
		List<VoxelShape> shapes = new ArrayList<>(found);
		collect(level, entity, box, shapes);
		return shapes.size() == found.size() ? found : shapes;
	}
}
