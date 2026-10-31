package com.example.tudursvehiclemod.client.hud;

import com.example.tudursvehiclemod.asset.AddonPaths;
import com.mojang.blaze3d.platform.NativeImage;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Loads every HUD script under assets/&lt;namespace&gt;/hud/*.txt, from TWO places: <ol> <li>Any real resource pack (any namespace, not just this mod's own).. */
public class HudScriptLoader implements SimpleSynchronousResourceReloadListener {

	/** Identifier plus the PNG's own actual pixel dimensions. */
	public record TextureInfo(Identifier id, int width, int height) {
	}

	private static Map<String, HudScript> loadedScripts = Map.of();
	private static Map<String, TextureInfo> loadedTextures = Map.of();

	public static Map<String, HudScript> getScripts() {
		return loadedScripts;
	}

	/** Looks up a texture by name (as referenced in a DrawTexture command, lowercase, no extension/path) across every namespace/source it was found under during the.. */
	public static TextureInfo resolveTexture(String name) {
		TextureInfo found = loadedTextures.get(name.toLowerCase(Locale.ROOT));
		if (found != null) {
			return found;
		}
		return new TextureInfo(Identifier.fromNamespaceAndPath("tudursvehiclemod", "textures/gui/" + name + ".png"), 256, 256);
	}

	public static void register() {
		ResourceManagerHelper.get(PackType.CLIENT_RESOURCES).registerReloadListener(new HudScriptLoader());
	}

	@Override
	public Identifier getFabricId() {
		return Identifier.fromNamespaceAndPath("tudursvehiclemod", "hud_script_loader");
	}

	@Override
	public void onResourceManagerReload(ResourceManager manager) {
		Map<String, HudScript> scripts = new HashMap<>();
		loadScriptsFromResourceManager(manager, scripts);
		loadScriptsFromLooseAddons(scripts);
		loadedScripts = Map.copyOf(scripts);
		// Always logged (not just on error).
		System.out.println("[tudursvehiclemod] HUD script reload: found " + loadedScripts.size()
				+ " script(s): " + loadedScripts.keySet());

		Map<String, TextureInfo> textures = new HashMap<>();
		loadTexturesFromResourceManager(manager, textures);
		loadTexturesFromLooseAddons(textures);
		loadedTextures = Map.copyOf(textures);
		System.out.println("[tudursvehiclemod] HUD texture reload: found " + loadedTextures.size()
				+ " texture(s): " + loadedTextures.keySet());
	}

	private static void loadScriptsFromResourceManager(ResourceManager manager, Map<String, HudScript> scripts) {
		for (Map.Entry<Identifier, Resource> entry : manager.listResources("hud", id -> id.getPath().endsWith(".txt"))
				.entrySet()) {
			Identifier id = entry.getKey();
			try (InputStream stream = entry.getValue().open()) {
				String text = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
				String key = keyFromFileName(id.getPath(), ".txt");
				scripts.put(key, HudScript.parse(id, text));
			} catch (IOException e) {
				System.err.println("[tudursvehiclemod] Failed to read HUD script " + id + ": " + e.getMessage());
			} catch (RuntimeException e) {
				System.err.println("[tudursvehiclemod] Failed to parse HUD script " + id + ": " + e.getMessage());
			}
		}
	}

	/** Scans tudursvehiclemod-addons/&lt;addon&gt;/assets/&lt;namespace&gt;/hud/*.txt directly with plain file I/O. */
	private static void loadScriptsFromLooseAddons(Map<String, HudScript> scripts) {
		for (Path addonDir : AddonPaths.listSubdirectories(AddonPaths.getAddonsRoot())) {
			Path assetsDir = addonDir.resolve("assets");
			for (Path namespaceDir : AddonPaths.listSubdirectories(assetsDir)) {
				String namespace = namespaceDir.getFileName().toString();
				Path hudDir = namespaceDir.resolve("hud");
				if (!Files.isDirectory(hudDir)) {
					continue;
				}
				try (var files = Files.walk(hudDir)) {
					for (Path txtFile : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".txt"))::iterator) {
						try {
							Identifier id = Identifier.fromNamespaceAndPath(namespace, "hud/" + hudDir.relativize(txtFile)
									.toString().replace('\\', '/').toLowerCase(java.util.Locale.ROOT));
							String text = Files.readString(txtFile, StandardCharsets.UTF_8);
							String key = keyFromFileName(txtFile.getFileName().toString(), ".txt");
							scripts.put(key, HudScript.parse(id, text));
						} catch (IOException e) {
							System.err.println("[tudursvehiclemod] Failed to read loose HUD script " + txtFile
									+ ": " + e.getMessage());
						} catch (RuntimeException e) {
							System.err.println("[tudursvehiclemod] Failed to parse loose HUD script " + txtFile
									+ ": " + e.getMessage());
						}
					}
				} catch (IOException e) {
					System.err.println("[tudursvehiclemod] Failed to scan " + hudDir + ": " + e.getMessage());
				}
			}
		}
	}

	private static void loadTexturesFromResourceManager(ResourceManager manager, Map<String, TextureInfo> textures) {
		for (Map.Entry<Identifier, Resource> entry :
				manager.listResources("textures/gui", res -> res.getPath().endsWith(".png")).entrySet()) {
			Identifier id = entry.getKey();
			String key = keyFromFileName(id.getPath(), ".png");
			try (InputStream stream = entry.getValue().open()) {
				BufferedImage image = ImageIO.read(stream);
				if (image != null) {
					textures.put(key, new TextureInfo(id, image.getWidth(), image.getHeight()));
				} else {
					System.err.println("[tudursvehiclemod] Could not decode HUD texture " + id + " - skipping");
				}
			} catch (IOException e) {
				System.err.println("[tudursvehiclemod] Failed to read HUD texture " + id + ": " + e.getMessage());
			}
		}
	}

	/** Scans tudursvehiclemod-addons/&lt;addon&gt;/assets/&lt;namespace&gt;/ textures/gui/*.png directly with plain file I/O, AND registers each one with the client's.. */
	private static void loadTexturesFromLooseAddons(Map<String, TextureInfo> textures) {
		Minecraft client = Minecraft.getInstance();
		for (Path addonDir : AddonPaths.listSubdirectories(AddonPaths.getAddonsRoot())) {
			Path assetsDir = addonDir.resolve("assets");
			for (Path namespaceDir : AddonPaths.listSubdirectories(assetsDir)) {
				String namespace = namespaceDir.getFileName().toString();
				Path guiTexturesDir = namespaceDir.resolve("textures").resolve("gui");
				if (!Files.isDirectory(guiTexturesDir)) {
					continue;
				}
				try (var files = Files.walk(guiTexturesDir)) {
					for (Path pngFile : (Iterable<Path>) files.filter(p -> p.toString().endsWith(".png"))::iterator) {
						String relative = guiTexturesDir.relativize(pngFile).toString()
								.replace('\\', '/').toLowerCase(java.util.Locale.ROOT);
						String key = keyFromFileName(pngFile.getFileName().toString(), ".png");
						try {
							Identifier id = Identifier.fromNamespaceAndPath(namespace, "textures/gui/" + relative);
							BufferedImage awtImage = ImageIO.read(pngFile.toFile());
							if (awtImage == null) {
								System.err.println("[tudursvehiclemod] Could not decode loose HUD texture "
										+ pngFile + " - skipping");
								continue;
							}
							textures.put(key, new TextureInfo(id, awtImage.getWidth(), awtImage.getHeight()));
							try (InputStream in = Files.newInputStream(pngFile)) {
								NativeImage nativeImage = NativeImage.read(in);
								client.execute(() -> client.getTextureManager()
										.register(id, new DynamicTexture(id::toString, nativeImage)));
							}
						} catch (IOException | RuntimeException e) {
							// RuntimeException too: a namespace folder that isn't a valid identifier throws from Identifier.of() and used to abort the whole reload.
							System.err.println("[tudursvehiclemod] Failed to read loose HUD texture " + pngFile
									+ ": " + e.getMessage());
						}
					}
				} catch (IOException e) {
					System.err.println("[tudursvehiclemod] Failed to scan " + guiTexturesDir + ": " + e.getMessage());
				}
			}
		}
	}

	private static String keyFromFileName(String pathOrFileName, String extension) {
		String fileName = pathOrFileName.contains("/")
				? pathOrFileName.substring(pathOrFileName.lastIndexOf('/') + 1)
				: pathOrFileName;
		return fileName.substring(0, fileName.length() - extension.length()).toLowerCase(Locale.ROOT);
	}
}
