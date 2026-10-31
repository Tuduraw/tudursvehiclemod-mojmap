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
		if (System.getProperty("tvmtest.gallery") == null) {
			return;
		}
		ClientTickEvents.END_CLIENT_TICK.register(this::tick);
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
