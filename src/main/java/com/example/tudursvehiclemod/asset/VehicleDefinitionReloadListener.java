package com.example.tudursvehiclemod.asset;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

/** Scans data/<namespace>/vehicles/*.json on every reload (including /reload on a running server) and rebuilds VehicleRegistry. */
public class VehicleDefinitionReloadListener implements SimpleSynchronousResourceReloadListener {

	private static final Logger LOGGER = LoggerFactory.getLogger("VehicleMod/Definitions");
	private static final String DIRECTORY = "vehicles";
	private static final String SUFFIX = ".json";

	@Override
	public Identifier getFabricId() {
		return Identifier.fromNamespaceAndPath("tudursvehiclemod", "vehicle_definitions");
	}

	/** The damage zones read during the current reload (DamageZone), by vehicle id: only vehicles that have some. */
	private Map<Identifier, java.util.List<DamageZone>> zones = new HashMap<>();

	@Override
	public void onResourceManagerReload(ResourceManager manager) {
		Map<Identifier, VehicleDefinition> loaded = new HashMap<>();
		this.zones = new HashMap<>();

		for (Map.Entry<Identifier, Resource> entry :
				manager.listResources(DIRECTORY, id -> id.getPath().endsWith(SUFFIX)).entrySet()) {

			Identifier fileId = entry.getKey();
			String path = fileId.getPath();
			Identifier vehicleId = Identifier.fromNamespaceAndPath(
					fileId.getNamespace(),
					path.substring(DIRECTORY.length() + 1, path.length() - SUFFIX.length())
			);

			try (Reader reader = entry.getValue().openAsReader()) {
				var json = VehicleJsonCompat.upgrade(JsonParser.parseReader(reader));
				VehicleDefinition.CODEC.parse(JsonOps.INSTANCE, json)
						.resultOrPartial(error -> LOGGER.error("Failed to parse vehicle '{}': {}", vehicleId, error))
						.ifPresent(def -> {
							loaded.put(vehicleId, def);
							readDamageZones(vehicleId, json, this.zones);
						});
			} catch (Exception e) {
				LOGGER.error("Failed to read vehicle definition {}", vehicleId, e);
			}
		}

		int fromResourcePacks = loaded.size();
		int addonPackCount = AddonPaths.listSubdirectories(AddonPaths.getAddonsRoot()).size();
		loadFromAddonsFolder(loaded);
		int fromAddonsFolder = loaded.size() - fromResourcePacks;

		VehicleRegistry.setAll(loaded);
		DamageZone.setAll(this.zones);
		LOGGER.info("Loaded {} vehicle definition(s) ({} from tudursvehiclemod-addons/, {} addon pack(s) found)",
				loaded.size(), fromAddonsFolder, addonPackCount);
	}

	/** The same definitions this listener builds, but without a server ResourceManager: data/&lt;namespace&gt;/vehicles/ inside every
	 * loaded mod (this mod's own built-ins and addon mods' jars), then the loose tudursvehiclemod-addons/ folder on top, the same
	 * override order as reload(). World and server data packs are not visible this way.
	 *
	 * Used by a client connected to a remote server (dedicated, or someone else's LAN game): the server never sends its definitions,
	 * so the client reads its own copy of the packs - see client.ClientDefinitions. Never throws. */
	public static Map<Identifier, VehicleDefinition> loadWithoutResourceManager() {
		Map<Identifier, VehicleDefinition> loaded = new HashMap<>();
		VehicleDefinitionReloadListener listener = new VehicleDefinitionReloadListener();
		for (var mod : net.fabricmc.loader.api.FabricLoader.getInstance().getAllMods()) {
			for (Path modRoot : mod.getRootPaths()) {
				for (Path namespaceDir : AddonPaths.listSubdirectories(modRoot.resolve("data"))) {
					loadVehiclesDir(namespaceDir.getFileName().toString(), namespaceDir.resolve(DIRECTORY), loaded, listener.zones);
				}
			}
		}
		listener.loadFromAddonsFolder(loaded);
		// The damage zones too - on this client only for the debug view (client.debug.HitDetectionMeshDebugRenderer); the damage itself is the server's.
		DamageZone.setAll(listener.zones);
		return loaded;
	}

	/** Reads data/&lt;namespace&gt;/vehicles/**.json from a plain directory (a mod root or an addon folder). Never throws. */
	private static void loadVehiclesDir(String namespace, Path vehiclesDir, Map<Identifier, VehicleDefinition> loaded, Map<Identifier, java.util.List<DamageZone>> zones) {
		if (!Files.isDirectory(vehiclesDir)) {
			return;
		}
		try (var files = Files.walk(vehiclesDir)) {
			for (Path jsonFile : (Iterable<Path>) files.filter(p -> p.toString().endsWith(SUFFIX))::iterator) {
				String relative = vehiclesDir.relativize(jsonFile).toString().replace('\\', '/');
				String path = relative.substring(0, relative.length() - SUFFIX.length());
				try (BufferedReader reader = Files.newBufferedReader(jsonFile, StandardCharsets.UTF_8)) {
					Identifier vehicleId = Identifier.fromNamespaceAndPath(namespace, path);
					var json = VehicleJsonCompat.upgrade(JsonParser.parseReader(reader));
					VehicleDefinition.CODEC.parse(JsonOps.INSTANCE, json)
							.resultOrPartial(error -> LOGGER.error("Failed to parse vehicle '{}' ({}): {}", vehicleId, jsonFile, error))
							.ifPresent(def -> {
								loaded.put(vehicleId, def);
								readDamageZones(vehicleId, json, zones);
							});
				} catch (Exception e) {
					LOGGER.error("Failed to read vehicle definition {}", jsonFile, e);
				}
			}
		} catch (Exception e) {
			LOGGER.error("Failed to scan {}", vehiclesDir, e);
		}
	}

	/** A vehicle file's "damage_zones" (DamageZone) into zones - replacing whatever an earlier file with the same id had, so the definition and its zones always come from the same file. A malformed list is logged and left out (the vehicle still loads, without zones). */
	private static void readDamageZones(Identifier vehicleId, com.google.gson.JsonElement json, Map<Identifier, java.util.List<DamageZone>> zones) {
		zones.remove(vehicleId);
		if (!json.isJsonObject() || !json.getAsJsonObject().has("damage_zones")) {
			return;
		}
		DamageZone.CODEC.listOf().parse(JsonOps.INSTANCE, json.getAsJsonObject().get("damage_zones"))
				.resultOrPartial(error -> LOGGER.error("Failed to parse damage_zones of vehicle '{}': {}", vehicleId, error))
				.filter(list -> !list.isEmpty())
				.ifPresent(list -> zones.put(vehicleId, java.util.List.copyOf(list)));
	}

	/** See AddonPaths for the expected folder layout. Never throws. */
	private void loadFromAddonsFolder(Map<Identifier, VehicleDefinition> loaded) {
		for (Path addonDir : AddonPaths.listSubdirectories(AddonPaths.getAddonsRoot())) {
			Path dataDir = addonDir.resolve("data");
			for (Path namespaceDir : AddonPaths.listSubdirectories(dataDir)) {
				String namespace = namespaceDir.getFileName().toString();
				Path vehiclesDir = namespaceDir.resolve(DIRECTORY);
				if (!Files.isDirectory(vehiclesDir)) {
					continue;
				}
				try (var files = Files.walk(vehiclesDir)) {
					for (Path jsonFile : (Iterable<Path>) files.filter(p -> p.toString().endsWith(SUFFIX))::iterator) {
						String relative = vehiclesDir.relativize(jsonFile).toString().replace('\\', '/');
						String path = relative.substring(0, relative.length() - SUFFIX.length());
						Identifier vehicleId = Identifier.fromNamespaceAndPath(namespace, path);

						try (BufferedReader reader = Files.newBufferedReader(jsonFile, StandardCharsets.UTF_8)) {
							var json = VehicleJsonCompat.upgrade(JsonParser.parseReader(reader));
							VehicleDefinition.CODEC.parse(JsonOps.INSTANCE, json)
									.resultOrPartial(error -> LOGGER.error(
											"Failed to parse addon vehicle '{}' ({}): {}", vehicleId, jsonFile, error))
									.ifPresent(def -> {
										loaded.put(vehicleId, def);
										readDamageZones(vehicleId, json, this.zones);
									});
						} catch (Exception e) {
							LOGGER.error("Failed to read addon vehicle definition {}", jsonFile, e);
						}
					}
				} catch (Exception e) {
					LOGGER.error("Failed to scan {}", vehiclesDir, e);
				}
			}
		}
	}
}
