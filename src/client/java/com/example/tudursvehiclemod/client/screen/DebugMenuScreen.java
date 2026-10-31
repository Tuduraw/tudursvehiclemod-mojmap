package com.example.tudursvehiclemod.client.screen;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/** Developer/debug-only actions (not on the normal player-facing menu): re-running /reload, toggling the hit-detection mesh visualizer. */
public class DebugMenuScreen extends Screen {

	public DebugMenuScreen() {
		super(Component.translatable("gui.tudursvehiclemod.debug_menu"));
	}

	@Override
	protected void init() {
		int centerX = this.width / 2;
		int startY = this.height / 2 - 36 - 48;

		this.addRenderableWidget(Button.builder(
				Component.translatable("gui.tudursvehiclemod.reload_addons"),
				button -> {
					if (this.minecraft != null && this.minecraft.player != null) {
						this.minecraft.player.connection.sendCommand("reload");
					}
					this.onClose();
				}
		).bounds(centerX - 100, startY, 200, 20).build());

		this.addRenderableWidget(Button.builder(
				tudursvehiclemod$hitboxMeshButtonText(),
				button -> {
					com.example.tudursvehiclemod.client.debug.HitDetectionMeshDebugRenderer.enabled =
							!com.example.tudursvehiclemod.client.debug.HitDetectionMeshDebugRenderer.enabled;
					button.setMessage(tudursvehiclemod$hitboxMeshButtonText());
				}
		).bounds(centerX - 100, startY + 24, 200, 20).build());

		this.addRenderableWidget(Button.builder(
				tudursvehiclemod$collisionBoxButtonText(),
				button -> {
					com.example.tudursvehiclemod.client.debug.CollisionBoxDebugRenderer.enabled =
							!com.example.tudursvehiclemod.client.debug.CollisionBoxDebugRenderer.enabled;
					button.setMessage(tudursvehiclemod$collisionBoxButtonText());
				}
		).bounds(centerX - 100, startY + 48, 200, 20).build());

		// Same convention as the two toggle buttons just above.
		this.addRenderableWidget(Button.builder(
				tudursvehiclemod$seatPositionsButtonText(),
				button -> {
					com.example.tudursvehiclemod.client.debug.SeatPositionDebugRenderer.enabled =
							!com.example.tudursvehiclemod.client.debug.SeatPositionDebugRenderer.enabled;
					button.setMessage(tudursvehiclemod$seatPositionsButtonText());
				}
		).bounds(centerX - 100, startY + 72, 200, 20).build());

		// Whether client.VehicleHud still draws at
		// all while in third-person view (first person is unaffected
		// either way - see VehicleModConfig's own showHudInThirdPerson
		// doc).
		this.addRenderableWidget(Button.builder(
				tudursvehiclemod$thirdPersonHudButtonText(),
				button -> {
					com.example.tudursvehiclemod.client.VehicleModConfig config = com.example.tudursvehiclemod.client.VehicleModClient.getConfig();
					config.showHudInThirdPerson = !config.showHudInThirdPerson;
					config.save();
					button.setMessage(tudursvehiclemod$thirdPersonHudButtonText());
				}
		).bounds(centerX - 100, startY + 96, 200, 20).build());

		// Opens ConfigMenuScreen, which edits VehicleModConfig in place - see that class's own doc for how each setting applies.
		this.addRenderableWidget(Button.builder(
				Component.translatable("gui.tudursvehiclemod.config_menu"),
				button -> {
					if (this.minecraft != null) {
						this.minecraft.setScreen(new ConfigMenuScreen());
					}
				}
		).bounds(centerX - 100, startY + 120, 200, 20).build());

		// Disabled with an explanatory tooltip whenever this client isn't hosting the world being played (client.getServer() == null - true for a genuinely remote dedicated server connection), rather than opening a screen that would edit a config instance nobody's own dedicated server will ever actually read.
		boolean canEditServerConfig = this.minecraft != null && this.minecraft.getSingleplayerServer() != null;
		Button serverConfigButton = Button.builder(
				Component.translatable("gui.tudursvehiclemod.server_config_menu"),
				button -> {
					if (this.minecraft != null) {
						this.minecraft.setScreen(new ServerConfigMenuScreen());
					}
				}
		).bounds(centerX - 100, startY + 144, 200, 20).build();
		serverConfigButton.active = canEditServerConfig;
		if (!canEditServerConfig) {
			serverConfigButton.setTooltip(Tooltip.create(Component.translatable("gui.tudursvehiclemod.server_config_menu.unavailable_tooltip")));
		}
		this.addRenderableWidget(serverConfigButton);

		this.addRenderableWidget(Button.builder(
				Component.translatable("gui.tudursvehiclemod.close"),
				button -> this.onClose()
		).bounds(centerX - 100, startY + 168, 200, 20).build());
	}

	/** Label reflects the toggle's own current on/off state, same convention as a vanilla options-screen toggle button. */
	private static Component tudursvehiclemod$thirdPersonHudButtonText() {
		boolean enabled = com.example.tudursvehiclemod.client.VehicleModClient.getConfig().showHudInThirdPerson;
		return Component.translatable("gui.tudursvehiclemod.toggle_third_person_hud",
				Component.translatable(enabled ? "options.on" : "options.off"));
	}

	/** Label reflects the toggle's own current on/off state, same convention as a vanilla options-screen toggle button. */
	private static Component tudursvehiclemod$hitboxMeshButtonText() {
		boolean enabled = com.example.tudursvehiclemod.client.debug.HitDetectionMeshDebugRenderer.enabled;
		return Component.translatable("gui.tudursvehiclemod.toggle_hitbox_mesh",
				Component.translatable(enabled ? "options.on" : "options.off"));
	}

	/** Label reflects the toggle's own current on/off state, same convention as a vanilla options-screen toggle button. */
	private static Component tudursvehiclemod$collisionBoxButtonText() {
		boolean enabled = com.example.tudursvehiclemod.client.debug.CollisionBoxDebugRenderer.enabled;
		return Component.translatable("gui.tudursvehiclemod.toggle_collision_box",
				Component.translatable(enabled ? "options.on" : "options.off"));
	}

	/** Label reflects the toggle's own current on/off state, same convention as a vanilla options-screen toggle button. */
	private static Component tudursvehiclemod$seatPositionsButtonText() {
		boolean enabled = com.example.tudursvehiclemod.client.debug.SeatPositionDebugRenderer.enabled;
		return Component.translatable("gui.tudursvehiclemod.toggle_seat_positions",
				Component.translatable(enabled ? "options.on" : "options.off"));
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
