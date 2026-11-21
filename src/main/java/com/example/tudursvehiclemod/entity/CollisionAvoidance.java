package com.example.tudursvehiclemod.entity;

import com.example.tudursvehiclemod.asset.MeshVoxelGrid;
import java.util.Optional;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3d;

/** Keeps an aircraft flying itself (a CAS strike, a carrier's aircraft, a drone or formation member) out of the other vehicles around it - its wingmen, its mothership - by flying around them.
 *
 * <p>Each other vehicle is taken as a box: its model's extent (the contact grid's bounds, VehicleContacts) turned and placed as the vehicle is, made larger on every side by the avoiding aircraft's own size and a margin. Each tick, if the aircraft's path over the next LOOKAHEAD_TICKS would enter one of those boxes, it is turned away from it - up or down rather than sideways where it can, as aircraft keep apart by height - the sooner it would enter, the harder. Already inside a box (rolling off a deck, a box it was put in), it is left to fly out of it.
 *
 * <p>Applied to the aircraft's movement just before it moves (AircraftEntity.move()), so it works the same whichever routine is flying it; that routine then brings it back on course once clear. Server only. */
public final class CollisionAvoidance {

	private CollisionAvoidance() {
	}

	/** How far ahead (ticks) a path is checked. */
	public static final double LOOKAHEAD_TICKS = 40.0;
	/** Extra room (blocks) kept around each box, beyond the aircraft's own size. */
	public static final double MARGIN = 3.0;
	/** The strongest turn away, as a fraction of the aircraft's speed per tick. */
	public static final double MAX_STEER_FRACTION = 0.35;

	/** The other vehicles aircraft is to keep out of right now (see AircraftEntity.tudursvehiclemod$shouldAvoid()). */
	public interface Filter {
		boolean avoid(AbstractVehicleEntity other);
	}

	/** The change to aircraft's movement this tick (blocks/tick) that keeps it out of every vehicle filter accepts - Vec3.ZERO when nothing is in the way. */
	public static Vec3 steer(AbstractVehicleEntity aircraft, Vec3 movement, Filter filter) {
		if (!(aircraft.level() instanceof ServerLevel world)) {
			return Vec3.ZERO;
		}
		double speed = movement.length();
		if (speed < 0.05) {
			return Vec3.ZERO;
		}
		double ownRadius = ownRadius(aircraft);
		double reach = speed * LOOKAHEAD_TICKS + ownRadius + MARGIN;
		Vec3 pos = aircraft.position();
		Vec3 total = Vec3.ZERO;
		for (AbstractVehicleEntity other : VehicleContacts.loadedIn(world)) {
			if (other == aircraft || other.isRemoved() || other.level() != world || other.tudursvehiclemod$isDestroyed()
					|| aircraft.hasPassenger(other) || other.hasPassenger(aircraft) || !filter.avoid(other)) {
				continue;
			}
			Optional<MeshVoxelGrid> grid = VehicleContacts.tudursvehiclemod$voxels(other.getDefinition());
			if (grid.isEmpty()) {
				continue;
			}
			float scale = other.getDefinition().scale() > 0f ? other.getDefinition().scale() : 1f;
			float[] b = grid.get().bounds();
			double otherRadius = 0.5 * scale * Math.sqrt(sq(b[3] - b[0]) + sq(b[4] - b[1]) + sq(b[5] - b[2]));
			Vec3 center = other.position();
			if (pos.distanceToSqr(center) > sq(reach + otherRadius)) {
				continue;
			}
			// Into the other's frame: its box is then axis-aligned there.
			Quaternionf rotation = other.tudursvehiclemod$getCurrentRotationForRendering();
			Quaternionf inverse = rotation.conjugate(new Quaternionf());
			Vector3d p = new Vector3d(pos.x - center.x, pos.y - center.y, pos.z - center.z);
			inverse.transform(p);
			// Relative motion: the other vehicle moves too.
			Vec3 otherMotion = other.position().subtract(other.xo, other.yo, other.zo);
			Vector3d v = new Vector3d(movement.x - otherMotion.x, movement.y - otherMotion.y, movement.z - otherMotion.z);
			inverse.transform(v);
			double grow = ownRadius + MARGIN;
			double[] min = {b[0] * scale - grow, b[1] * scale - grow, b[2] * scale - grow};
			double[] max = {b[3] * scale + grow, b[4] * scale + grow, b[5] * scale + grow};
			double[] rawMin = {b[0] * scale, b[1] * scale, b[2] * scale};
			double[] rawMax = {b[3] * scale, b[4] * scale, b[5] * scale};
			if (inside(p, rawMin, rawMax)) {
				// Already in it (rolling off its deck, or put there): let it fly out.
				continue;
			}
			double hit;
			if (inside(p, min, max)) {
				hit = 0.0;
			} else {
				hit = entry(p, v, min, max);
				if (hit < 0 || hit > LOOKAHEAD_TICKS) {
					continue;
				}
			}
			// Where it would be when it got there, and which way is out from the box's middle (in the box's frame).
			Vector3d at = new Vector3d(p).add(new Vector3d(v).mul(hit));
			Vector3d mid = new Vector3d((rawMin[0] + rawMax[0]) / 2, (rawMin[1] + rawMax[1]) / 2, (rawMin[2] + rawMax[2]) / 2);
			Vector3d out = at.sub(mid, new Vector3d());
			// Normalised by the box's own half-size, so the nearest face wins, not the longest axis.
			out.x /= Math.max(1.0, (max[0] - min[0]) / 2);
			out.y /= Math.max(1.0, (max[1] - min[1]) / 2);
			out.z /= Math.max(1.0, (max[2] - min[2]) / 2);
			rotation.transform(out);
			Vec3 away = new Vec3(out.x, out.y, out.z);
			// Only the part across the path turns it; along the path would just slow it or speed it up.
			Vec3 dir = movement.normalize();
			away = away.subtract(dir.scale(away.dot(dir)));
			// Up or down rather than sideways: keep the vertical part, and halve the sideways one.
			away = new Vec3(away.x * 0.5, away.y, away.z * 0.5);
			if (away.lengthSqr() < 1.0E-6) {
				away = new Vec3(0, 1, 0);
			}
			// Over a ship or anything else low down, never down towards it.
			if (center.y + rawMin[1] < pos.y && away.y < 0) {
				away = new Vec3(away.x, -away.y, away.z);
			}
			double urgency = 1.0 - hit / LOOKAHEAD_TICKS;
			total = total.add(away.normalize().scale(speed * MAX_STEER_FRACTION * urgency));
		}
		double limit = speed * MAX_STEER_FRACTION;
		if (total.lengthSqr() > limit * limit) {
			total = total.normalize().scale(limit);
		}
		return total;
	}

	/** Half the aircraft's own model diagonal - its size, for the room it needs. */
	private static double ownRadius(AbstractVehicleEntity aircraft) {
		Optional<MeshVoxelGrid> grid = VehicleContacts.tudursvehiclemod$voxels(aircraft.getDefinition());
		float scale = aircraft.getDefinition().scale() > 0f ? aircraft.getDefinition().scale() : 1f;
		if (grid.isEmpty()) {
			return Math.max(aircraft.getDefinition().width(), aircraft.getDefinition().height());
		}
		float[] b = grid.get().bounds();
		// Not the full half-diagonal (an aircraft is long and flat): the mean of its half-extents.
		return scale * ((b[3] - b[0]) + (b[4] - b[1]) + (b[5] - b[2])) / 6.0;
	}

	private static boolean inside(Vector3d p, double[] min, double[] max) {
		return p.x > min[0] && p.x < max[0] && p.y > min[1] && p.y < max[1] && p.z > min[2] && p.z < max[2];
	}

	/** When (ticks) the point p moving by v each tick enters the box; -1 if it never does. */
	private static double entry(Vector3d p, Vector3d v, double[] min, double[] max) {
		double tMin = 0, tMax = Double.POSITIVE_INFINITY;
		double[] ps = {p.x, p.y, p.z};
		double[] vs = {v.x, v.y, v.z};
		for (int axis = 0; axis < 3; axis++) {
			if (Math.abs(vs[axis]) < 1.0E-9) {
				if (ps[axis] <= min[axis] || ps[axis] >= max[axis]) {
					return -1;
				}
				continue;
			}
			double t1 = (min[axis] - ps[axis]) / vs[axis];
			double t2 = (max[axis] - ps[axis]) / vs[axis];
			tMin = Math.max(tMin, Math.min(t1, t2));
			tMax = Math.min(tMax, Math.max(t1, t2));
			if (tMin > tMax) {
				return -1;
			}
		}
		return tMin;
	}

	private static double sq(double x) {
		return x * x;
	}
}
