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
		if (System.getProperty("tvmtest.deck") != null) {
			ClientTickEvents.END_CLIENT_TICK.register(this::deckTick);
			return;
		}
		if (System.getProperty("tvmtest.gallery") == null) {
			return;
		}
		ClientTickEvents.END_CLIENT_TICK.register(this::tick);
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
