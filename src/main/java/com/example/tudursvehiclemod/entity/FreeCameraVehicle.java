package com.example.tudursvehiclemod.entity;

/** Implemented by AircraftEntity and SubmarineEntity - both let the pilot's
 * own view move independently of the vehicle (permanently for submarines,
 * toggleable via isFreeLook() for aircraft), while the vehicle's own yaw/
 * pitch/roll still drive the camera's roll and the mounted player's own
 * body tilt (see CameraMixin, PlayerLookRateMixin, LivingEntityRendererMixin -
 * all three check this interface rather than a specific vehicle class so
 * they apply to both). All four methods are already implemented via
 * ordinary inheritance (Entity's own getViewYRot()/getViewXRot(), and
 * AbstractVehicleEntity's own getRoll()/isFreeLook()) - this interface exists
 * purely so the mixins can treat both vehicle types uniformly. */
public interface FreeCameraVehicle {
	float getViewYRot(float tickProgress);

	float getViewXRot(float tickProgress);

	float getRoll(float tickProgress);

	boolean isFreeLook();

	/** Any non-pilot seat is ALWAYS effectively
	 * free-look, regardless of the shared isFreeLook() toggle above (which
	 * only the PILOT actually controls, by holding freeLookKey) - see
	 * AbstractVehicleEntity's own doc for the concrete implementation. */
	boolean tudursvehiclemod$isEffectiveFreeLook(net.minecraft.world.entity.Entity viewer);
}
