package com.example.tudursvehiclemod.client;

import com.example.tudursvehiclemod.asset.RunwayDefinition;
import com.example.tudursvehiclemod.entity.AbstractVehicleEntity;
import com.example.tudursvehiclemod.entity.RunwayDeckCollision;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;

/** Moves the local player along with the runway deck it is on, as the deck moves and turns - on this client, as part of the player's own movement.
 *
 * <p>The server used to do this by teleporting the player every tick; each teleport made the server ignore the player's own movement until the client acknowledged it, so walking on a moving deck barely got anywhere. Done here instead, the carry is simply part of where the client says the player is, and the player can walk, turn and jump on a deck under way. Everything else on a deck (mobs, parked vehicles) is still carried by the server (AbstractVehicleEntity.tudursvehiclemod$carryRunwayDeckEntities()).
 *
 * <p>Uses the same footprint and the same height band as the server's carry, and the vehicle's position and yaw as this client shows them, so the player moves exactly as the deck it sees (RunwayDeckCollision's boxes) does. Runs at the end of each client tick, after the vehicle has moved for that tick. */
public final class RunwayDeckCarry {

	private RunwayDeckCarry() {
	}

	/** Each runway vehicle's position, yaw and runway hatch progress at the end of the previous client tick. */
	private static final Map<AbstractVehicleEntity, Pose> LAST_POSE = new WeakHashMap<>();

	private record Pose(double x, double y, double z, float yaw, float[] hatchProgress) {
	}

	/** How far above a deck surface (blocks) the player is still carried - a jump stays with the deck. */
	private static final double CARRY_ABOVE = 1.6;
	/** How far below it. */
	private static final double CARRY_BELOW = 0.5;
	/** How far outside the runway's edges (blocks) the player is still carried. */
	private static final double CARRY_MARGIN = 0.5;

	public static void onEndClientTick(Minecraft client) {
		LocalPlayer player = client.player;
		if (player == null || client.level == null) {
			LAST_POSE.clear();
			return;
		}
		boolean carried = player.isPassenger() || player.isSpectator();
		for (AbstractVehicleEntity vehicle : RunwayDeckCollision.vehiclesIn(client.level)) {
			if (vehicle.isRemoved()) {
				continue;
			}
			Pose now = poseOf(vehicle);
			Pose previous = LAST_POSE.put(vehicle, now);
			if (carried || previous == null || vehicle.tudursvehiclemod$isDestroyed()) {
				continue;
			}
			carried = carry(player, vehicle, previous, now);
		}
	}

	private static Pose poseOf(AbstractVehicleEntity vehicle) {
		int runways = vehicle.getDefinition().runways().size();
		float[] progress = new float[runways];
		for (int i = 0; i < runways; i++) {
			progress[i] = vehicle.tudursvehiclemod$getRunwayHatchProgress(i);
		}
		return new Pose(vehicle.getX(), vehicle.getY(), vehicle.getZ(), vehicle.getYRot(), progress);
	}

	/** Moves player with the deck of vehicle from previous to now, if player is on one of its runways. */
	private static boolean carry(LocalPlayer player, AbstractVehicleEntity vehicle, Pose previous, Pose now) {
		double deltaX = now.x() - previous.x();
		double deltaY = now.y() - previous.y();
		double deltaZ = now.z() - previous.z();
		float deltaYaw = Mth.wrapDegrees(now.yaw() - previous.yaw());
		// A jump this large is a teleport or a fresh position from the server, not motion to follow (the server's carry draws the same line).
		if (Math.abs(deltaX) > 5.0 || Math.abs(deltaY) > 5.0 || Math.abs(deltaZ) > 5.0 || Math.abs(deltaYaw) > 30.0) {
			return false;
		}
		List<RunwayDefinition> runways = vehicle.getDefinition().runways();
		double prevYawRad = Math.toRadians(previous.yaw());
		double prevForwardX = -Math.sin(prevYawRad);
		double prevForwardZ = Math.cos(prevYawRad);
		double prevRightX = prevForwardZ;
		double prevRightZ = -prevForwardX;
		double relX = player.getX() - previous.x();
		double relZ = player.getZ() - previous.z();
		double localX = relX * prevRightX + relZ * prevRightZ;
		double localZ = relX * prevForwardX + relZ * prevForwardZ;
		for (int index = 0; index < runways.size() && index < previous.hatchProgress().length && index < now.hatchProgress().length; index++) {
			RunwayDefinition base = runways.get(index);
			if (base.hatchGated() && !vehicle.isHatchOpen()) {
				continue;
			}
			RunwayDefinition runway = base.tudursvehiclemod$withHatchProgress(previous.hatchProgress()[index]);
			double minZ = Math.min(runway.startZ(), runway.endZ());
			double maxZ = Math.max(runway.startZ(), runway.endZ());
			double halfWidth = runway.width() / 2.0;
			if (localX < runway.centerX() - halfWidth - CARRY_MARGIN || localX > runway.centerX() + halfWidth + CARRY_MARGIN
					|| localZ < minZ - CARRY_MARGIN || localZ > maxZ + CARRY_MARGIN) {
				continue;
			}
			double prevSurfaceY = previous.y() + runway.heightY();
			double above = player.getY() - prevSurfaceY;
			if (above < -CARRY_BELOW || above > CARRY_ABOVE) {
				continue;
			}
			// The runway's own movement as its hatch progress changes (an elevator), on top of the hull's.
			float progressDelta = now.hatchProgress()[index] - previous.hatchProgress()[index];
			double hatchX = progressDelta * (runway.hatchOffsetX() * prevRightX + runway.hatchOffsetZ() * prevForwardX);
			double hatchY = progressDelta * runway.hatchOffsetY();
			double hatchZ = progressDelta * (runway.hatchOffsetX() * prevRightZ + runway.hatchOffsetZ() * prevForwardZ);
			if (Math.abs(deltaX) < 1.0E-5 && Math.abs(deltaY) < 1.0E-5 && Math.abs(deltaZ) < 1.0E-5 && Math.abs(deltaYaw) < 1.0E-5
					&& Math.abs(hatchX) < 1.0E-5 && Math.abs(hatchY) < 1.0E-5 && Math.abs(hatchZ) < 1.0E-5) {
				// On a deck that isn't moving: nothing to do, and no other deck to look for.
				return true;
			}
			double cos = Math.cos(Math.toRadians(deltaYaw));
			double sin = Math.sin(Math.toRadians(deltaYaw));
			double rotatedRelX = relX * cos - relZ * sin;
			double rotatedRelZ = relX * sin + relZ * cos;
			double newX = player.getX() + deltaX + (rotatedRelX - relX) + hatchX;
			double newZ = player.getZ() + deltaZ + (rotatedRelZ - relZ) + hatchZ;
			// Standing on the deck: kept exactly on its surface (as the server's carry does), so the player's own gravity can't pull it a little further down each tick on a rising elevator. In the air: moved by as much as the deck moved.
			double newY = player.onGround() && Math.abs(above) < 0.1
					? now.y() + runway.heightY() + hatchY
					: player.getY() + deltaY + hatchY;
			player.setPos(newX, newY, newZ);
			if (Math.abs(deltaYaw) > 1.0E-5) {
				player.setYRot(player.getYRot() + deltaYaw);
				player.setYHeadRot(player.getYHeadRot() + deltaYaw);
			}
			return true;
		}
		return false;
	}
}
