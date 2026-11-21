package com.example.tudursvehiclemod;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/** Shared by the config files (VehicleModServerConfig, client.VehicleModConfig): reading one, and bringing a file written by an older version up to date. */
public final class ConfigFiles {

	private ConfigFiles() {
	}

	/** Reads path as type: null when it doesn't exist or can't be read. A setting missing from the file (one added after the file was written) keeps its default - and is added to the file, with that default, right away, so every setting can be found and edited in it. Settings already in the file are left exactly as written, in their order, along with anything the current version doesn't know (an addon's or a later version's own setting is never lost); only the missing ones are appended. */
	public static <T> T load(Path path, Gson gson, Class<T> type) {
		if (!Files.exists(path)) {
			return null;
		}
		JsonObject file;
		try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
			JsonElement parsed = JsonParser.parseReader(reader);
			if (!parsed.isJsonObject()) {
				return null;
			}
			file = parsed.getAsJsonObject();
		} catch (IOException | RuntimeException e) {
			return null;
		}
		T loaded;
		try {
			loaded = gson.fromJson(file, type);
		} catch (RuntimeException e) {
			return null;
		}
		if (loaded == null) {
			return null;
		}
		JsonObject complete = gson.toJsonTree(loaded).getAsJsonObject();
		int added = 0;
		for (Map.Entry<String, JsonElement> entry : complete.entrySet()) {
			if (!file.has(entry.getKey())) {
				file.add(entry.getKey(), entry.getValue());
				added++;
			}
		}
		if (added > 0) {
			write(path, gson, file);
			VehicleMod.LOGGER.info("[{}] Added {} new setting(s) with their default values to {}", VehicleMod.MOD_ID, added, path);
		}
		return loaded;
	}

	/** Saves config to path, keeping any setting the file has that the current version doesn't know (see load()). */
	public static void save(Path path, Gson gson, Object config) {
		JsonObject json = gson.toJsonTree(config).getAsJsonObject();
		if (Files.exists(path)) {
			try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
				JsonElement parsed = JsonParser.parseReader(reader);
				if (parsed.isJsonObject()) {
					for (Map.Entry<String, JsonElement> entry : parsed.getAsJsonObject().entrySet()) {
						if (!json.has(entry.getKey())) {
							json.add(entry.getKey(), entry.getValue());
						}
					}
				}
			} catch (IOException | RuntimeException ignored) {
				// An unreadable file is simply replaced.
			}
		}
		write(path, gson, json);
	}

	/** Writes json to path, creating its folder. A failure to write is logged, not thrown: not being able to write a config is not worth crashing over. */
	public static void write(Path path, Gson gson, Object json) {
		try {
			Files.createDirectories(path.getParent());
			try (Writer writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
				if (json instanceof JsonElement element) {
					gson.toJson(element, writer);
				} else {
					gson.toJson(json, writer);
				}
			}
		} catch (IOException e) {
			VehicleMod.LOGGER.warn("[{}] Could not write {}: {}", VehicleMod.MOD_ID, path, e.toString());
		}
	}
}
