package com.example.tudursvehiclemod.client;

import com.example.tudursvehiclemod.asset.VehicleDefinition;
import com.example.tudursvehiclemod.asset.VehicleRegistry;
import com.example.tudursvehiclemod.item.VehicleConverterTarget;
import com.example.tudursvehiclemod.network.SelectVehiclePayload;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Opened by TieredVehicleSpawnerItem (via VehicleSelectScreenOpener) when the pilot right-clicks one of the 25 tiered spawner items.
 *
 * Sorted by display name by default (rather than tier-only, which used to
 * leave same-tier vehicles in whatever order VehicleRegistry's own backing
 * map happened to iterate in - not a stable order across restarts, per a
 * direct report that this made vehicles hard to find) - see SortMode's own
 * doc for the other available orderings, and the search box for filtering
 * by name directly. */
public class VehicleSelectScreen extends Screen {

	private static final int ROWS_PER_PAGE = 7;
	private static final int BUTTON_HEIGHT = 20;
	private static final int BUTTON_WIDTH = 220;
	private static final int LIST_TOP_OFFSET = 56;

	private enum SortMode {
		NAME("screen.tudursvehiclemod.sort_name"),
		TIER("screen.tudursvehiclemod.sort_tier");

		private final String translationKey;

		SortMode(String translationKey) {
			this.translationKey = translationKey;
		}

		Component label() {
			return Component.translatable(translationKey);
		}

		SortMode next() {
			return this == NAME ? TIER : NAME;
		}
	}

	private final VehicleConverterTarget category;
	private final int tier;
	private final List<Map.Entry<Identifier, VehicleDefinition>> allInCategory = new ArrayList<>();
	private final List<Map.Entry<Identifier, VehicleDefinition>> matches = new ArrayList<>();
	private SortMode sortMode = SortMode.NAME;
	private EditBox searchBox;
	private int page;

	public VehicleSelectScreen(VehicleConverterTarget category, int tier) {
		super(Component.translatable(category.translationKey()).append(Component.literal(" (" + tier + ")")));
		this.category = category;
		this.tier = tier;
	}

	@Override
	protected void init() {
		allInCategory.clear();
		for (Map.Entry<Identifier, VehicleDefinition> entry : VehicleRegistry.getAll().entrySet()) {
			VehicleDefinition def = entry.getValue();
			if (def.entityType().equals(category.entityTypeId()) && def.tier() <= tier) {
				allInCategory.add(entry);
			}
		}

		String previousSearch = this.searchBox != null ? this.searchBox.getValue() : "";
		this.searchBox = new EditBox(this.font, this.width / 2 - BUTTON_WIDTH / 2, 40, BUTTON_WIDTH, 20,
				Component.translatable("screen.tudursvehiclemod.search"));
		this.searchBox.setHint(Component.translatable("screen.tudursvehiclemod.search"));
		this.searchBox.setValue(previousSearch);
		this.searchBox.setResponder(text -> {
			page = 0;
			applyFilterAndSort();
		});
		this.addRenderableWidget(this.searchBox);
		this.setInitialFocus(this.searchBox);

		applyFilterAndSort();
	}

	private void applyFilterAndSort() {
		String query = this.searchBox.getValue().strip().toLowerCase(Locale.ROOT);
		matches.clear();
		for (Map.Entry<Identifier, VehicleDefinition> entry : allInCategory) {
			String name = VehicleDisplayNames.resolve(entry.getKey(), entry.getValue());
			if (query.isEmpty() || name.toLowerCase(Locale.ROOT).contains(query)) {
				matches.add(entry);
			}
		}

		Comparator<Map.Entry<Identifier, VehicleDefinition>> comparator = switch (sortMode) {
			case NAME -> Comparator.comparing(e -> VehicleDisplayNames.resolve(e.getKey(), e.getValue()), String.CASE_INSENSITIVE_ORDER);
			case TIER -> Comparator.<Map.Entry<Identifier, VehicleDefinition>>comparingInt(e -> e.getValue().tier())
					.thenComparing(e -> VehicleDisplayNames.resolve(e.getKey(), e.getValue()), String.CASE_INSENSITIVE_ORDER);
		};
		matches.sort(comparator);

		rebuildButtons();
	}

	private void rebuildButtons() {
		this.clearWidgets();
		this.addRenderableWidget(this.searchBox);

		int totalPages = Math.max(1, (matches.size() + ROWS_PER_PAGE - 1) / ROWS_PER_PAGE);
		page = Math.max(0, Math.min(page, totalPages - 1));

		int x = this.width / 2 - BUTTON_WIDTH / 2;
		int startY = LIST_TOP_OFFSET;

		// Sort toggle button, to the right of the search box's own row.
		this.addRenderableWidget(Button.builder(
				Component.translatable("screen.tudursvehiclemod.sort_by", sortMode.label()),
				button -> {
					sortMode = sortMode.next();
					applyFilterAndSort();
				}
		).bounds(x, 16, BUTTON_WIDTH, BUTTON_HEIGHT).build());

		int from = page * ROWS_PER_PAGE;
		int to = Math.min(matches.size(), from + ROWS_PER_PAGE);

		for (int i = from; i < to; i++) {
			Map.Entry<Identifier, VehicleDefinition> entry = matches.get(i);
			Identifier vehicleId = entry.getKey();
			VehicleDefinition def = entry.getValue();
			int y = startY + (i - from) * BUTTON_HEIGHT;

			String label = VehicleDisplayNames.resolve(vehicleId, def) + " (T" + def.tier() + ")";
			this.addRenderableWidget(Button.builder(Component.literal(label), button -> {
				ClientPlayNetworking.send(new SelectVehiclePayload(vehicleId, true));
				this.onClose();
			}).bounds(x, y, BUTTON_WIDTH, BUTTON_HEIGHT).build());
		}

		if (totalPages > 1) {
			int navY = startY + ROWS_PER_PAGE * BUTTON_HEIGHT + 8;
			this.addRenderableWidget(Button.builder(Component.literal("<"), button -> {
				page--;
				rebuildButtons();
			}).bounds(x, navY, 40, BUTTON_HEIGHT).build());

			this.addRenderableWidget(Button.builder(Component.literal(">"), button -> {
				page++;
				rebuildButtons();
			}).bounds(x + BUTTON_WIDTH - 40, navY, 40, BUTTON_HEIGHT).build());
		}
	}

	@Override
	public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		context.drawCenteredString(this.font, this.title, this.width / 2, 4, 0xFFFFFFFF);

		if (matches.isEmpty()) {
			context.drawCenteredString(this.font,
					Component.translatable("screen.tudursvehiclemod.no_vehicles_available"),
					this.width / 2, this.height / 2, 0xFFFFAAAA);
		}
	}

	@Override
	public boolean isPauseScreen() {
		return false;
	}
}
