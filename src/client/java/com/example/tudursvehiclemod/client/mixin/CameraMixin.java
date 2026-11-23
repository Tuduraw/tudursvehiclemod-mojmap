package com.example.tudursvehiclemod.client.mixin;

import com.example.tudursvehiclemod.client.AircraftCameraZoomState;
import com.example.tudursvehiclemod.entity.AbstractVehicleEntity;
import com.example.tudursvehiclemod.entity.FreeCameraVehicle;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Two things while riding an aircraft/helicopter/submarine (or really any vehicle with a live seat offset): 1. */
@Mixin(Camera.class)
public abstract class CameraMixin {

	/** Camera's own `pos` field has a companion `blockPos` field that vanilla's own setPos(Vec3d) method (this invoker) keeps in sync alongside it. An earlier version of this mixin used a raw @Accessor("pos") field write instead, which bypasses that entirely - leaving blockPos stale at wherever vanilla's own un-offset camera position was, diverging from the seat-offset-adjusted `pos` this mixin actually wants to show. Used everywhere in this file instead of that raw accessor, so both fields stay consistent. */
	@Invoker("setPosition")
	public abstract void tudursvehiclemod$setPos(Vec3 pos);

	@org.spongepowered.asm.mixin.Shadow
	private boolean detached;

	@Inject(method = "setup(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/Entity;ZZF)V", at = @At("TAIL"))
	private void tudursvehiclemod$applyVehicleRollAndPos(Level area, Entity focusedEntity, boolean thirdPerson,
													boolean inverseView, float tickProgress, CallbackInfo ci) {
		if (!(focusedEntity instanceof Player player)) {
			return;
		}

		Camera self = (Camera) (Object) this;

		// Per Readme_Weapon.txt's own TVMissile doc: while the LOCAL
		// player is directly steering a TV-guided missile (see
		// client.TvMissileControlState's own doc), the camera renders
		// from THAT missile's own current position/orientation instead
		// of anything vehicle-related at all - checked FIRST, ahead of
		// every vehicle-piloting case below, since this overrides
		// whatever vehicle the player might also still actually be
		// seated in. Deliberately only ever moves the CAMERA itself, not
		// this player's own entity position at all (unlike vanilla's own
		// Entity#setCameraEntity(), which would forcibly teleport the
		// player's own entity to match - see
		// entity.projectile.VehicleProjectileEntity's own
		// tudursvehiclemod$setTvControlled() doc for exactly why that's
		// avoided here), so a passenger who's still actually seated in
		// their own vehicle never has their own position disturbed by
		// any of this at all.
		Integer controlledId = com.example.tudursvehiclemod.client.TvMissileControlState.controlledEntityId;
		if (controlledId != null) {
			Entity missile = area.getEntity(controlledId);
			if (missile instanceof com.example.tudursvehiclemod.entity.projectile.VehicleProjectileEntity) {
				float missileYaw = missile.getViewYRot(tickProgress);
				float missilePitch = missile.getViewXRot(tickProgress);
				Quaternionf fresh = new Quaternionf();
				fresh.rotateY((float) Math.toRadians(180.0 - missileYaw));
				fresh.rotateX((float) Math.toRadians(-missilePitch));
				if (inverseView) {
					fresh.rotateY((float) Math.PI);
				}
				self.rotation().set(fresh);
				this.tudursvehiclemod$setPos(missile.getPosition(tickProgress));
				return;
			}
			// The controlled entity isn't actually loaded/visible client-side (yet, or anymore) this exact frame - falls through to whatever the camera would otherwise be showing rather than leaving it stuck on a stale position; network.TvMissileControlEndPayload should arrive and clear controlledEntityId properly shortly regardless.
		}

		AbstractVehicleEntity vehicle;
		if (player.getVehicle() instanceof AbstractVehicleEntity mounted) {
			vehicle = mounted;
		} else {
			// While the LOCAL player is
			// remote-controlling a UAV vehicle through a bound
			// block.StationBlockEntity (see client.RemoteControlState's
			// own doc) - as opposed to actually riding one, the ordinary
			// case just above - this vehicle's own PILOT view is shown
			// instead, exactly the same way it would be if they really
			// were aboard it (this whole method's own logic below is
			// entirely unaware of/unaffected by which of the two cases
			// actually got it here). Checked here specifically (client-
			// side state, not entity.AbstractVehicleEntity's own server-
			// only tudursvehiclemod$getEffectiveVehicle(), which reads a
			// map that's only ever actually populated server-side) since
			// this whole method only ever runs client-side anyway.
			Integer remoteControlledId = com.example.tudursvehiclemod.client.RemoteControlState.controlledEntityId;
			if (remoteControlledId != null && area.getEntity(remoteControlledId) instanceof AbstractVehicleEntity remoteVehicle) {
				vehicle = remoteVehicle;
			} else {
				return;
			}
		}

		if (vehicle instanceof FreeCameraVehicle aircraft) {
			Vec3 eyePos = vehicle.getRotatedEyePos(player, tickProgress);

			if (aircraft.tudursvehiclemod$isEffectiveFreeLook(player)) {
				// Free-look: the pilot's own yaw/pitch (vanilla's own camera
				// rotation) shows through completely normally, but roll is
				// still the vehicle's own - not something the player
				// controls via mouse, so their head should still tilt with
				// the aircraft's own bank regardless of where they're
				// looking (same as it does outside free-look).
				float rollDegrees = aircraft.getRoll(tickProgress);
				if (rollDegrees != 0f) {
					self.rotation().rotateZ((float) -Math.toRadians(rollDegrees));
				}
				if (thirdPerson) {
					this.tudursvehiclemod$setPos(tudursvehiclemod$pullBackAlongCurrentRotation(
							self, area, player, eyePos));
				} else {
					this.tudursvehiclemod$setPos(eyePos);
				}
				return;
			}

			float yaw = aircraft.getViewYRot(tickProgress);
			float pitch = aircraft.getViewXRot(tickProgress);
			float roll = aircraft.getRoll(tickProgress);

			Quaternionf fresh = new Quaternionf();
			fresh.rotateY((float) Math.toRadians(180.0 - yaw));
			fresh.rotateX((float) Math.toRadians(-pitch));
			fresh.rotateZ((float) Math.toRadians(-roll));
			if (inverseView) {
				// Minecraft's own second third-person stage (F5 twice): camera in front, facing back towards the player.
				fresh.rotateY((float) Math.PI);
			}
			self.rotation().set(fresh);

			// Applied in BOTH first and third person for aircraft (unlike the other vehicles below, which only get it in first person).
			if (thirdPerson) {
				Quaternionf modelRotation = new Quaternionf()
						.rotateY((float) Math.toRadians(-yaw))
						.rotateX((float) Math.toRadians(pitch))
						.rotateZ((float) Math.toRadians(roll));
				// inverseView pulls the camera out in FRONT of the nose instead of behind it, to match the flipped rotation above.
				Vector3f pullDirection = new Vector3f(0, 0, inverseView ? 1 : -1);
				modelRotation.transform(pullDirection);
				this.tudursvehiclemod$setPos(tudursvehiclemod$pullBack(area, player, eyePos, pullDirection));
			} else {
				this.tudursvehiclemod$setPos(eyePos);
			}
			return;
		}

		float rollDegrees = vehicle.getRoll(tickProgress);
		if (rollDegrees != 0f) {
			self.rotation().rotateZ((float) -Math.toRadians(rollDegrees));
		}

		// Third person was left on vanilla's own
		// default entirely for every vehicle type that doesn't implement
		// FreeCameraVehicle (car/ship/helicopter) - meaning whenever a
		// seat's own camera position (CameraPosition/AddGunnerSeat's own
		// camera fields) differs from the seat's own physical position,
		// only first person actually centered on it; third person still
		// centered on the seat position instead. Reusing the same
		// pull-back-along-current-rotation approach already used for
		// aircraft free-look - it leaves the camera's OWN rotation exactly
		// as vanilla already set it (so scroll-wheel zoom/F5 double-tap
		// keep working normally), just re-centering on the mod's own
		// eyePos instead of the player's raw seat position.
		Vec3 eyePos = vehicle.getRotatedEyePos(player, tickProgress);
		if (thirdPerson) {
			this.tudursvehiclemod$setPos(tudursvehiclemod$pullBackAlongCurrentRotation(self, area, player, eyePos));
		} else {
			this.tudursvehiclemod$setPos(eyePos);
		}
	}

	/** MC Heli's CameraPosition options, for the seat's current camera position (AbstractVehicleEntity.tudursvehiclemod$getActiveCameraPosition()) - applied after tudursvehiclemod$applyVehicleRollAndPos() has placed the camera as usual (both inject at TAIL, in this order):
	 * <ul>
	 * <li>fixed_yaw / fixed_pitch: the view's yaw / pitch relative to the vehicle are fixed at that angle (the view still turns, pitches and rolls with the vehicle). An axis left out keeps what it would otherwise be: the player's own look, or - for an aircraft outside free look - straight ahead. Only the view: the player's own look (what weapons that follow it aim at) is left as it is.</li>
	 * <li>force_camera: the view is always from the camera position itself - third person (F5) doesn't pull it back. A camera position more than a block from the seat's own eye (a view from outside, such as behind the vehicle) shows the player's own body, as third person would.</li>
	 * </ul>
	 * Nothing changes for a seat whose camera position has none of these, nor while a TV-guided missile is being steered. */
	@Inject(method = "setup(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/Entity;ZZF)V", at = @At("TAIL"))
	private void tudursvehiclemod$applyCameraPositionOptions(Level area, Entity focusedEntity, boolean thirdPerson,
			boolean inverseView, float tickProgress, CallbackInfo ci) {
		if (!(focusedEntity instanceof Player player)) {
			return;
		}
		Integer controlledId = com.example.tudursvehiclemod.client.TvMissileControlState.controlledEntityId;
		if (controlledId != null && area.getEntity(controlledId) instanceof com.example.tudursvehiclemod.entity.projectile.VehicleProjectileEntity) {
			return;
		}
		AbstractVehicleEntity vehicle;
		if (player.getVehicle() instanceof AbstractVehicleEntity mounted) {
			vehicle = mounted;
		} else {
			Integer remoteControlledId = com.example.tudursvehiclemod.client.RemoteControlState.controlledEntityId;
			if (remoteControlledId != null && area.getEntity(remoteControlledId) instanceof AbstractVehicleEntity remoteVehicle) {
				vehicle = remoteVehicle;
			} else {
				return;
			}
		}
		com.example.tudursvehiclemod.asset.CameraPositionEntry camera = vehicle.tudursvehiclemod$getActiveCameraPosition(player);
		if (camera == null) {
			return;
		}
		boolean fixed = camera.fixedYaw().isPresent() || camera.fixedPitch().isPresent();
		if (!fixed && !camera.forceCamera()) {
			return;
		}
		Camera self = (Camera) (Object) this;
		Vec3 eyePos = vehicle.getRotatedEyePos(player, tickProgress);
		if (fixed) {
			boolean lookFollowsVehicle = vehicle instanceof FreeCameraVehicle aircraft && !aircraft.tudursvehiclemod$isEffectiveFreeLook(player);
			float relativeYaw = camera.fixedYaw().map(Double::floatValue).orElseGet(() -> lookFollowsVehicle ? 0f
					: net.minecraft.util.Mth.wrapDegrees(player.getViewYRot(tickProgress) - vehicle.getViewYRot(tickProgress)));
			float relativePitch = camera.fixedPitch().map(Double::floatValue).orElseGet(() -> lookFollowsVehicle ? 0f
					: player.getViewXRot(tickProgress) - vehicle.getViewXRot(tickProgress));
			// The camera's rotation for a view along the body's own frame: body * Ry(-yaw) * Rx(pitch), turned to face the way the camera looks (Ry(pi)) - with yaw = pitch = 0 the same rotation the aircraft view above builds from yaw/pitch/roll.
			Quaternionf rotation = vehicle.tudursvehiclemod$getBodyOrientation(tickProgress)
					.rotateY((float) Math.toRadians(-relativeYaw))
					.rotateX((float) Math.toRadians(relativePitch))
					.rotateY((float) Math.PI);
			if (inverseView) {
				rotation.rotateY((float) Math.PI);
			}
			self.rotation().set(rotation);
		}
		if (camera.forceCamera()) {
			this.tudursvehiclemod$setPos(eyePos);
			if (vehicle.tudursvehiclemod$getCameraDistanceFromSeatEye(player) > 1.0) {
				this.detached = true;
			}
		} else if (thirdPerson) {
			this.tudursvehiclemod$setPos(tudursvehiclemod$pullBackAlongCurrentRotation(self, area, player, eyePos));
		} else {
			this.tudursvehiclemod$setPos(eyePos);
		}
	}

	/** Pulls the camera away from eyePos along pullDirection (a world-space unit vector) by AircraftCameraZoomState's current distance, clamped by a raycast so it.. */
	private static Vec3 tudursvehiclemod$pullBack(Level area, Player player, Vec3 eyePos, Vector3f pullDirection) {
		double desiredDistance = AircraftCameraZoomState.currentDistance;
		Vec3 desiredPos = new Vec3(
				eyePos.x + pullDirection.x * desiredDistance,
				eyePos.y + pullDirection.y * desiredDistance,
				eyePos.z + pullDirection.z * desiredDistance);

		double actualDistance = desiredDistance;
		ClipContext raycastContext = new ClipContext(eyePos, desiredPos,
				ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, player);
		BlockHitResult hit = area.clip(raycastContext);
		if (hit.getType() == HitResult.Type.BLOCK) {
			// Pull in slightly further than the exact hit point, so the camera doesn't sit right at (and risk still clipping into) the surface it hit.
			double hitDistance = eyePos.distanceTo(hit.getLocation());
			actualDistance = Math.max(0.0, Math.min(desiredDistance, hitDistance - 0.25));
		}

		return new Vec3(
				eyePos.x + pullDirection.x * actualDistance,
				eyePos.y + pullDirection.y * actualDistance,
				eyePos.z + pullDirection.z * actualDistance);
	}

	/** Free-look's own third-person pull-back: since the rotation is left as whatever vanilla's own camera update already set it to.. */
	private static Vec3 tudursvehiclemod$pullBackAlongCurrentRotation(Camera self, Level area, Player player,
																   Vec3 eyePos) {
		Vector3f pullDirection = new Vector3f(0, 0, 1);
		self.rotation().transform(pullDirection);
		return tudursvehiclemod$pullBack(area, player, eyePos, pullDirection);
	}

}
