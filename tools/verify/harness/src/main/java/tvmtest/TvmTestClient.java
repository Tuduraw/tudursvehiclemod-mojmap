package tvmtest;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.util.ScreenshotRecorder;

/**
 * Client half of the harness. Active only with -Dtvmtest.gallery=<count>: once in a world it asks the
 * (integrated) server to build the gallery, then for each vehicle asks to be moved in front of it,
 * waits for the chunk to render and saves screenshots/tvm_<index>_<a|b>.png (b = just after firing),
 * then quits. The server side is TvmTest's /tvmtest gallery and /tvmtest view commands.
 */
public class TvmTestClient implements ClientModInitializer {
	int ticks;
	int index = -1;
	int phase;
	int count = -1;

	@Override
	public void onInitializeClient() {
		if (System.getProperty("tvmtest.farveh") != null) {
			ClientTickEvents.END_CLIENT_TICK.register(this::farVehicleTick);
			return;
		}
		if (System.getProperty("tvmtest.speedsync") != null) {
			ClientTickEvents.END_CLIENT_TICK.register(this::speedSyncTick);
			return;
		}
		if (System.getProperty("tvmtest.deck") != null) {
			ClientTickEvents.END_CLIENT_TICK.register(this::deckTick);
			return;
		}
		if (System.getProperty("tvmtest.gallery") == null) {
			return;
		}
		ClientTickEvents.END_CLIENT_TICK.register(this::tick);
	}

	// ------------------------------------------------------------------ far vehicles
	// -Dtvmtest.farveh=1: turns renderFarVehicles on (fog off, render distance 2 - standing in for an LOD mod), then every 40 ticks logs the far vehicles it has (FarVehicleProxies) and how many vehicle render states were drawn; screenshots at 400 and 600 ticks; quits at 700. /tvmtest farveh <name> on the server places the vehicles.
	int farTicks;

	void farVehicleTick(MinecraftClient client) {
		if (client.world == null || client.player == null) {
			return;
		}
		farTicks++;
		if (farTicks == 1 && Boolean.getBoolean("tvmtest.hitmesh")) {
			// The hit detection debug view, with damage zones (client.debug.HitDetectionMeshDebugRenderer).
			com.example.tudursvehiclemod.client.debug.HitDetectionMeshDebugRenderer.enabled = true;
		}
		if (farTicks == 1) {
			com.example.tudursvehiclemod.client.VehicleModClient.getConfig().renderFarVehicles = true;
			com.example.tudursvehiclemod.client.VehicleModClient.getConfig().farVehicleRenderDistance = 1024;
			if (System.getProperty("tvmtest.farveh.off") != null) {
				com.example.tudursvehiclemod.client.VehicleModClient.getConfig().renderFarVehicles = false;
			}
			client.options.getViewDistance().setValue(2);
			if (net.minecraft.client.render.fog.FogRenderer.toggleFog()) {
				net.minecraft.client.render.fog.FogRenderer.toggleFog();
			}
			client.options.hudHidden = true;
		}
		// Each real vehicle's landing gear in its first ticks in this client's world: one that came from far away must not move its gear (it should already be where the stand-in had it).
		for (var e : client.world.getEntities()) {
			if (!(e instanceof com.example.tudursvehiclemod.entity.AircraftEntity v)) {
				continue;
			}
			int age = farSeen.merge(v.getId(), 1, Integer::sum);
			if (age == 1 || age == 3 || age == 10 || age == 30) {
				for (var part : v.getDefinition().toggleParts()) {
					if ("landing_gear".equals(part.trigger())) {
						TvmTest.LOG.info("[tvmtest] FARVEH real id={} age={} pos={},{},{} {}={}", v.getId(), age, Math.round(v.getX()), Math.round(v.getY()), Math.round(v.getZ()),
								part.part(), Math.round(v.getTogglePartProgress(part.part()) * 100) / 100.0);
						break;
					}
				}
			}
		}
		if (farTicks % 40 == 0) {
			var far = com.example.tudursvehiclemod.client.farvehicle.FarVehicleProxies.visible(client.world);
			StringBuilder sb = new StringBuilder();
			for (var v : far) {
				sb.append(v.getType().getUntranslatedName()).append('@').append(Math.round(v.getX())).append(',').append(Math.round(v.getY())).append(',').append(Math.round(v.getZ()));
				sb.append("[engine=").append(Math.round(v.tudursvehiclemod$getEngineSoundThrottle() * 100) / 100.0).append(']');
				// Its landing gear parts' progress (0 when up) - drawn as the server reported (entity.FarVehiclePose).
				for (var part : v.getDefinition().toggleParts()) {
					if ("landing_gear".equals(part.trigger())) {
						sb.append('[').append(part.part()).append('=').append(Math.round(v.getTogglePartProgress(part.part()) * 100) / 100.0).append(']');
						break;
					}
				}
				sb.append(' ');
			}
			int real = 0;
			for (var e : client.world.getEntities()) {
				if (e instanceof com.example.tudursvehiclemod.entity.AbstractVehicleEntity) real++;
			}
			var farProjectiles = com.example.tudursvehiclemod.client.farvehicle.FarVehicleProxies.visibleProjectiles(client.world);
			for (var p : farProjectiles) {
				sb.append("projectile@").append(Math.round(p.getX())).append(',').append(Math.round(p.getY())).append(',').append(Math.round(p.getZ())).append(' ');
			}
			TvmTest.LOG.info("[tvmtest] FARVEH tick={} proxies={} projectiles={} realVehicles={} player={},{},{} far=[{}]", farTicks, far.size(), farProjectiles.size(), real,
					Math.round(client.player.getX()), Math.round(client.player.getY()), Math.round(client.player.getZ()), sb);
		}
		if (farTicks == 400 || farTicks == 600 || farTicks == 615) {
			client.player.setYaw(-90f);
			client.player.setPitch(-5f);
			net.minecraft.client.util.ScreenshotRecorder.saveScreenshot(client.runDirectory, "farveh-" + farTicks + ".png", client.getFramebuffer(), 1,
					text -> TvmTest.LOG.info("[tvmtest] FARVEH screenshot {}", text.getString()));
		}
		if (farTicks >= 700) {
			client.scheduleStop();
		}
	}

	// ------------------------------------------------------------------ speed sync
	// -Dtvmtest.speedsync=1: logs the vehicle speed multiplier this client uses (entity.VehicleSpeedScaling) every 100 ticks in a world, for 600 ticks, then quits - change it on the server meanwhile (/tvmtest setspeed <x>) to see it follow.
	int speedSyncTicks;
	final java.util.Map<Integer, Integer> farSeen = new java.util.HashMap<>();
	int driveTicks, driveRuns, driveDoneVehicle = -1;
	net.minecraft.util.math.Vec3d driveStartPos;
	double drivePeak, driveLastX, driveLastZ, driveStartX, driveStartZ;

	void speedSyncTick(MinecraftClient client) {
		if (client.world == null || client.player == null) {
			return;
		}
		speedSyncTicks++;
		if (client.player.isDead()) {
			client.player.requestRespawn();
			return;
		}
		if (speedSyncTicks == 1 && client.player.getVehicle() != null) {
			// Still seated from the last session: not one /tvmtest speeddrive gave us.
			driveDoneVehicle = client.player.getVehicle().getId();
		}
		if (speedSyncTicks % 100 == 0) {
			TvmTest.LOG.info("[tvmtest] SPEEDSYNC tick={} multiplier={} vehicle={} driveTicks={} done={}", speedSyncTicks,
					com.example.tudursvehiclemod.entity.VehicleSpeedScaling.multiplier(client.world), client.player.getVehicle(), driveTicks, driveDoneVehicle);
		}
		// Once seated in a vehicle (/tvmtest speeddrive <name> on the server): full forward for 200 ticks, peak speed as this client moves it.
		if (client.player.getVehicle() instanceof com.example.tudursvehiclemod.entity.AbstractVehicleEntity vehicle && vehicle.getId() != driveDoneVehicle) {
			client.options.forwardKey.setPressed(true);
			// -Dtvmtest.standby: an aircraft into standby mode first (Alt+G's payload), then full throttle.
			if (driveTicks == 20 && System.getProperty("tvmtest.standby") != null && vehicle instanceof com.example.tudursvehiclemod.entity.AircraftEntity) {
				net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new com.example.tudursvehiclemod.network.StandbyTogglePayload());
				driveStartPos = new net.minecraft.util.math.Vec3d(vehicle.getX(), vehicle.getY(), vehicle.getZ());
			}
			if (driveTicks % 50 == 0 && vehicle instanceof com.example.tudursvehiclemod.entity.AircraftEntity aircraft) {
				TvmTest.LOG.info("[tvmtest] STANDBY t={} standby={} throttle={} moved={}", driveTicks, aircraft.tudursvehiclemod$isStandby(),
						Math.round(aircraft.getThrottle() * 100) / 100.0, driveStartPos == null ? -1 : Math.round(new net.minecraft.util.math.Vec3d(aircraft.getX(), aircraft.getY(), aircraft.getZ()).distanceTo(driveStartPos) * 100) / 100.0);
			}
			if (driveTicks > 0) {
				double dx = vehicle.getX() - driveLastX, dz = vehicle.getZ() - driveLastZ;
				double step = Math.sqrt(dx * dx + dz * dz);
				// A jump of more than 3 blocks in a tick is a position correction (boarding, a teleport), not driving.
				if (step < 3.0) {
					drivePeak = Math.max(drivePeak, step);
				}
				if (driveTicks == 150) {
					driveStartX = vehicle.getX();
					driveStartZ = vehicle.getZ();
				}
			}
			driveLastX = vehicle.getX();
			driveLastZ = vehicle.getZ();
			driveTicks++;
			if (driveTicks == 200) {
				client.options.forwardKey.setPressed(false);
				double last50 = Math.sqrt(Math.pow(vehicle.getX() - driveStartX, 2) + Math.pow(vehicle.getZ() - driveStartZ, 2)) / 50.0;
				TvmTest.LOG.info("[tvmtest] SPEEDDRIVE peakKmh={} last50Kmh={} multiplier={} cap={}", Math.round(drivePeak * 72.0 * 10) / 10.0, Math.round(last50 * 72.0 * 10) / 10.0,
						com.example.tudursvehiclemod.entity.VehicleSpeedScaling.multiplier(vehicle), vehicle.tudursvehiclemod$getEffectiveMaxSpeed() * 72.0);
				drivePeak = 0;
				driveTicks = 0;
				driveRuns++;
				driveDoneVehicle = vehicle.getId();
			}
		}
		if (speedSyncTicks >= 6000 || driveRuns >= Integer.getInteger("tvmtest.runs", 2)) {
			client.scheduleStop();
		}
	}

	// ------------------------------------------------------------------ deck walk
	int deckTicks;
	final com.google.gson.JsonObject deckReport = new com.google.gson.JsonObject();
	final com.google.gson.JsonArray deckSamples = new com.google.gson.JsonArray();
	double deckMinDy = Double.POSITIVE_INFINITY, deckMaxDy = Double.NEGATIVE_INFINITY;
	int deckOnTicks, deckOffTicks, deckServerOffTicks;
	double deckMaxClientServerGap;

	/**
	 * Deck-walk test (-Dtvmtest.deck=1): the player is put on a carrier's deck (/tvmtest deckwalk setup), stands, walks
	 * forward, the carrier gets under way (/tvmtest deckwalk sail), the player walks again while it sails, then stands.
	 * Every tick records the player's height above the deck as this client sees it, and as the server does; writes
	 * tvmtest-deckwalk.json and quits.
	 */
	boolean deckJoined;
	double walkStartZ;

	void deckTick(MinecraftClient client) {
		if (client.player == null || client.world == null) {
			if (deckJoined && deckTicks < 560) {
				// Kicked or disconnected before the end.
				deckReport.addProperty("disconnectedAtTick", deckTicks);
				deckTicks = 559;
				writeDeckReport(client);
			}
			return;
		}
		deckJoined = true;
		deckTicks++;
		int t = deckTicks;
		if (t == 100) {
			client.player.networkHandler.sendChatCommand("tvmtest deckwalk setup");
		}
		if (t == 170) {
			client.player.networkHandler.sendChatCommand("tvmtest deckwalk board");
		}
		if (t == 220) {
			press(client, client.options.forwardKey, true);
		} else if (t == 250) {
			press(client, client.options.forwardKey, false);
		} else if (t == 300) {
			client.player.networkHandler.sendChatCommand("tvmtest deckwalk sail");
		} else if (t == 360) {
			press(client, client.options.forwardKey, true);
		} else if (t == 400) {
			press(client, client.options.forwardKey, false);
		} else if (t == 380) {
			ScreenshotRecorder.saveScreenshot(client.runDirectory, "tvm_deck_walk.png", client.getFramebuffer(), 1, text -> { });
		}
		if (t >= 200 && t < 560) {
			// The carrier as this client shows it: the nearest vehicle with a runway (works on a dedicated server too).
			com.example.tudursvehiclemod.entity.AbstractVehicleEntity clientShip = null;
			for (net.minecraft.entity.Entity e : client.world.getEntities()) {
				if (e instanceof com.example.tudursvehiclemod.entity.AbstractVehicleEntity v && !v.getDefinition().runways().isEmpty()
						&& (clientShip == null || v.squaredDistanceTo(client.player) < clientShip.squaredDistanceTo(client.player))) {
					clientShip = v;
				}
			}
			if (clientShip != null) {
				double deckY = clientShip.getY() + clientShip.getDefinition().runways().get(0).heightY();
				double dy = client.player.getY() - deckY;
				deckMinDy = Math.min(deckMinDy, dy);
				deckMaxDy = Math.max(deckMaxDy, dy);
				if (Math.abs(dy) < 0.05 && client.player.isOnGround()) {
					deckOnTicks++;
				} else {
					deckOffTicks++;
				}
				double relZNow = client.player.getZ() - clientShip.getZ();
				if (t == 220 || t == 360) {
					walkStartZ = relZNow;
				} else if (t == 250) {
					deckReport.addProperty("walkParkedBlocks", Math.round((relZNow - walkStartZ) * 100) / 100.0);
				} else if (t == 400) {
					deckReport.addProperty("walkSailingBlocks", Math.round((relZNow - walkStartZ) * 100) / 100.0);
				}
				net.minecraft.server.network.ServerPlayerEntity serverPlayer = client.getServer() == null || TvmTest.deckShip == null ? null
						: client.getServer().getPlayerManager().getPlayer(client.player.getUuid());
				if (serverPlayer != null) {
					double serverDy = serverPlayer.getY() - (TvmTest.deckShip.getY() + TvmTest.deckShip.getDefinition().runways().get(0).heightY());
					if (Math.abs(serverDy) > 0.05) {
						deckServerOffTicks++;
					}
					deckMaxClientServerGap = Math.max(deckMaxClientServerGap, serverPlayer.getEntityPos().distanceTo(client.player.getEntityPos()));
				}
				if (t % 10 == 0) {
					deckSamples.add(t + ": dy=" + Math.round(dy * 1000) / 1000.0 + " onGround=" + client.player.isOnGround()
							+ " shipZ=" + Math.round(clientShip.getZ() * 10) / 10.0 + " relZ=" + Math.round((client.player.getZ() - clientShip.getZ()) * 100) / 100.0);
				}
			}
		}
		if (t == 555) {
			client.player.networkHandler.sendChatCommand("tvmtest deckwalk report");
		}
		if (t == 560) {
			writeDeckReport(client);
		}
	}

	void writeDeckReport(MinecraftClient client) {
		{
			deckReport.addProperty("runwayTiles", Boolean.getBoolean("tvm.runwayTiles"));
			deckReport.addProperty("minDy", deckMinDy);
			deckReport.addProperty("maxDy", deckMaxDy);
			deckReport.addProperty("ticksOnDeck", deckOnTicks);
			deckReport.addProperty("ticksOffDeck", deckOffTicks);
			deckReport.addProperty("serverTicksOffDeck", deckServerOffTicks);
			deckReport.addProperty("maxClientServerGap", deckMaxClientServerGap);
			deckReport.add("samples", deckSamples);
			try {
				java.nio.file.Files.writeString(client.runDirectory.toPath().resolve("tvmtest-deckwalk.json"),
						new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(deckReport));
			} catch (java.io.IOException e) {
				TvmTest.LOG.error("[tvmtest] deckwalk report", e);
			}
			TvmTest.LOG.info("[tvmtest] DECKWALK DONE");
			client.scheduleStop();
		}
	}

	void tick(MinecraftClient client) {
		if (client.player == null || client.world == null) {
			return;
		}
		ticks++;
		if (count < 0) {
			if (ticks == 100) {
				client.player.networkHandler.sendChatCommand("tvmtest gallery");
			}
			if (ticks > 100 && TvmTest.galleryCount > 0) {
				count = TvmTest.galleryCount;
				ticks = 0;
			}
			return;
		}
		if (index >= count) {
			rideTick(client);
			return;
		}
		// phase 0: move in front of the vehicle; 1: plain shot; 2: fire weapons; 3: shot while firing
		if (phase == 0 && ticks >= 20) {
			index++;
			if (index >= count) {
				ticks = 0;
				return;
			}
			client.player.networkHandler.sendChatCommand("tvmtest view " + index);
			phase = 1;
			ticks = 0;
		} else if (phase == 1 && ticks == 80) {
			shot(client, "a");
			client.player.networkHandler.sendChatCommand("tvmtest fire " + index);
			phase = 2;
			ticks = 0;
		} else if (phase == 2 && ticks == 6) {
			shot(client, "b");
			phase = 0;
			ticks = 0;
		}
	}

	int rideIndex = -1;
	int rideTicks;

	net.minecraft.client.option.KeyBinding find(MinecraftClient client, String translation) {
		for (net.minecraft.client.option.KeyBinding k : client.options.allKeys) {
			if (k.getId().equals(translation)) {
				return k;
			}
		}
		return null;
	}

	void press(MinecraftClient client, net.minecraft.client.option.KeyBinding k, boolean down) {
		if (k != null) {
			net.minecraft.client.util.InputUtil.Key key = net.minecraft.client.util.InputUtil.fromTranslationKey(k.getBoundKeyTranslationKey());
			net.minecraft.client.option.KeyBinding.setKeyPressed(key, down);
			if (down) {
				net.minecraft.client.option.KeyBinding.onKeyPressed(key);
			}
		}
	}

	/** After the gallery: ride each vehicle as pilot, hold forward for 100 ticks and fire for 20 of them. */
	void rideTick(MinecraftClient client) {
		rideTicks++;
		if (rideIndex < 0 || rideTicks == 140) {
			if (rideIndex >= 0) {
				press(client, client.options.forwardKey, false);
				press(client, find(client, "key.tudursvehiclemod.fire_weapon"), false);
				shot(client, rideIndex, "c");
				client.player.networkHandler.sendChatCommand("tvmtest rideend " + rideIndex);
			}
			rideIndex++;
			rideTicks = 0;
			if (rideIndex >= count) {
				TvmTest.LOG.info("[tvmtest] GALLERY DONE");
				client.scheduleStop();
				rideIndex = Integer.MAX_VALUE / 2;
				return;
			}
			client.player.networkHandler.sendChatCommand("tvmtest view " + rideIndex);
			client.player.networkHandler.sendChatCommand("tvmtest ride " + rideIndex);
			return;
		}
		if (rideTicks >= 20 && rideTicks < 120) {
			press(client, client.options.forwardKey, true);
		}
		net.minecraft.client.option.KeyBinding fire = find(client, "key.tudursvehiclemod.fire_weapon");
		if (rideTicks == 60) {
			press(client, fire, true);
		} else if (rideTicks == 80) {
			press(client, fire, false);
		}
	}

	void shot(MinecraftClient client, String suffix) {
		shot(client, index, suffix);
	}

	void shot(MinecraftClient client, int i, String suffix) {
		String name = String.format("tvm_%02d_%s.png", i, suffix);
		ScreenshotRecorder.saveScreenshot(client.runDirectory, name, client.getFramebuffer(), 1, text -> TvmTest.LOG.info("[tvmtest] screenshot {}", name));
	}
}
