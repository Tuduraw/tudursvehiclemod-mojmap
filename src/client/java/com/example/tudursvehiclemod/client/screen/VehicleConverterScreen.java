package com.example.tudursvehiclemod.client.screen;

import com.example.tudursvehiclemod.screen.VehicleConverterScreenHandler;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** No custom GUI background PNG asset of its own - see client.screen.FuelRefinerScreen's own doc for why (slot backgrounds drawn as simple beveled rectangles via SlotGrid instead of a cropped vanilla texture). */
public class VehicleConverterScreen extends AbstractContainerScreen<VehicleConverterScreenHandler> {

	private static final int PANEL_COLOR = 0xFFC6C6C6;

	public VehicleConverterScreen(VehicleConverterScreenHandler handler, Inventory inventory, Component title) {
		super(handler, inventory, title);
		this.imageWidth = 176;
		this.imageHeight = 176;
		this.inventoryLabelY = this.imageHeight - 94;
	}

	@Override
	protected void init() {
		super.init();
		int centerX = this.leftPos + this.imageWidth / 2;
		// Per this screen's own drawBackground() layout: the page buttons sit either side of the slot itself (which VehicleConverterScreenHandler's own constructor fixes at x+80, y+35), with confirm below both.
		int pageButtonY = this.topPos + 34;
		this.addRenderableWidget(Button.builder(Component.literal("<"),
						button -> this.tudursvehiclemod$clickButton(VehicleConverterScreenHandler.BUTTON_PREVIOUS_PAGE))
				.bounds(centerX - 58, pageButtonY, 20, 20).build());
		this.addRenderableWidget(Button.builder(Component.literal(">"),
						button -> this.tudursvehiclemod$clickButton(VehicleConverterScreenHandler.BUTTON_NEXT_PAGE))
				.bounds(centerX + 38, pageButtonY, 20, 20).build());
		this.addRenderableWidget(Button.builder(Component.translatable("gui.tudursvehiclemod.vehicle_converter.confirm"),
						button -> this.tudursvehiclemod$clickButton(VehicleConverterScreenHandler.BUTTON_CONFIRM))
				.bounds(centerX - 50, this.topPos + 58, 100, 20).build());
	}

	/** Sends this button press to the server via the standard vanilla clickButton mechanism (same one an enchanting table/loom already use) - see VehicleConverterScreenHandler's own onButtonClick() doc for what each ID actually does. */
	private void tudursvehiclemod$clickButton(int buttonId) {
		if (this.minecraft != null && this.minecraft.gameMode != null) {
			this.minecraft.gameMode.handleInventoryButtonClick(this.getMenu().containerId, buttonId);
		}
	}

	@Override
	protected void renderBg(GuiGraphics context, float delta, int mouseX, int mouseY) {
		int x = this.leftPos;
		int y = this.topPos;
		context.fill(x, y, x + this.imageWidth, y + this.imageHeight, PANEL_COLOR);

		// This screen's own single slot (base item input) - MUST match VehicleConverterScreenHandler's own Slot coordinate exactly (x+80, y+35): these two had drifted apart.
		SlotGrid.drawSlot(context, x + 80, y + 35);

		// Currently-selected target, above the slot/page-button row - covers addon-registered targets too (see item.VehicleConverterTargets' own doc).
		com.example.tudursvehiclemod.item.VehicleConverterTarget selected =
				com.example.tudursvehiclemod.item.VehicleConverterTargets.byIndex(
						this.getMenu().getSelectedCategoryIndex());
		if (selected != null) {
			Component categoryText = Component.translatable(selected.translationKey());
			int textWidth = this.font.width(categoryText);
			context.drawString(this.font, categoryText, x + this.imageWidth / 2 - textWidth / 2, y + 22, 0xFF404040, false);
		}

		// Player's own inventory grid (3 rows + hotbar) - see VehicleConverterScreenHandler's own constructor for these exact coordinates.
		SlotGrid.drawPlayerInventoryGrid(context, x, y + 84, y + 142);
	}
}
