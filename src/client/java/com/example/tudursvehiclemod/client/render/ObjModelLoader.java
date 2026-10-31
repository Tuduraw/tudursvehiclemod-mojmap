package com.example.tudursvehiclemod.client.render;

import com.example.tudursvehiclemod.asset.AddonPaths;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/** Finds every assets/<namespace>/models/obj/*.obj on resource reload and parses it into an ObjModel, keyed by its resource Identifier (e.g. */
public class ObjModelLoader implements SimpleSynchronousResourceReloadListener {

	private static final Logger LOGGER = LoggerFactory.getLogger("VehicleMod/ObjModelLoader");
	private static Map<Identifier, ObjModel> MODELS = new HashMap<>();

	@Override
	public Identifier getFabricId() {
		return Identifier.fromNamespaceAndPath("tudursvehiclemod", "obj_models");
	}

	@Override
	public void onResourceManagerReload(ResourceManager manager) {
		Map<Identifier, ObjModel> loaded = new HashMap<>();

		for (Map.Entry<Identifier, Resource> entry :
				manager.listResources("models/obj", id -> id.getPath().endsWith(".obj")).entrySet()) {

			Identifier id = entry.getKey();
			try (BufferedReader reader = entry.getValue().openAsReader()) {
				loaded.put(id, ObjModel.parse(reader));
			} catch (Exception e) {
				LOGGER.error("Failed to parse OBJ model {}", id, e);
			}
		}

		int fromResourcePacks = loaded.size();
		loadFromAddonsFolder(loaded);
		int fromAddonsFolder = loaded.size() - fromResourcePacks;

		MODELS = loaded;
		LOGGER.info("Loaded {} OBJ vehicle model(s) ({} from tudursvehiclemod-addons/)", loaded.size(), fromAddonsFolder);
	}

	/** See AddonPaths for the expected folder layout. Never throws. */
	private void loadFromAddonsFolder(Map<Identifier, ObjModel> loaded) {
		for (Path addonDir : AddonPaths.listSubdirectories(AddonPaths.getAddonsRoot())) {
			Path assetsDir = addonDir.resolve("assets");
			for (Path namespaceDir : AddonPaths.listSubdirectories(assetsDir)) {
				String namespace = namespaceDir.getFileName().toString();
				Path modelsDir = namespaceDir.resolve("models").resolve("obj");
				if (!Files.isDirectory(modelsDir)) {
					continue;
				}
				try (var files = Files.walk(modelsDir)) {
					for (Path objFile : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".obj"))::iterator) {
						String relative = modelsDir.relativize(objFile).toString()
								.replace('\\', '/').toLowerCase(java.util.Locale.ROOT);
						try {
							Identifier id = Identifier.fromNamespaceAndPath(namespace, "models/obj/" + relative);
							try (BufferedReader reader = Files.newBufferedReader(objFile, StandardCharsets.UTF_8)) {
								loaded.put(id, ObjModel.parse(reader));
							}
						} catch (Exception e) {
							LOGGER.error("Failed to parse addon OBJ model {}", objFile, e);
						}
					}
				} catch (Exception e) {
					LOGGER.error("Failed to scan {}", modelsDir, e);
				}
			}
		}
	}

	public static Optional<ObjModel> get(Identifier id) {
		return Optional.ofNullable(MODELS.get(id));
	}
}
