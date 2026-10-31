package com.example.tudursvehiclemod.client.screen;

import com.example.tudursvehiclemod.screen.DroneCenterScreenHandler;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** No custom GUI background PNG asset of its own - same reasoning as FuelRefinerScreen's own doc (a simple beveled-rectangle slot background instead of cropping a vanilla texture). Slot 0 the binding stick, slot 1 an optional item.DroneRouteBookItem - see block.DroneCenterBlockEntity's own doc. */
public class DroneCenterScreen extends AbstractContainerScreen<DroneCenterScreenHandler> {

	private static final int PANEL_COLOR = 0xFFC6C6C6;

	public DroneCenterScreen(DroneCenterScreenHandler handler, Inventory inventory, Component title) {
		super(handler, inventory, title);
		this.imageWidth = 176;
		this.imageHeight = 166;
		this.inventoryLabelY = this.imageHeight - 94;
	}

	@Override
	protected void renderBg(GuiGraphics context, float delta, int mouseX, int mouseY) {
		int x = this.leftPos;
		int y = this.topPos;
		context.fill(x, y, x + this.imageWidth, y + this.imageHeight, PANEL_COLOR);

		// This screen's own slots (binding stick, route book) - see DroneCenterScreenHandler's own constructor for these exact coordinates.
		SlotGrid.drawSlot(context, x + 62, y + 24);
		SlotGrid.drawSlot(context, x + 62, y + 52);

		// Player's own inventory grid (3 rows + hotbar) - see DroneCenterScreenHandler's own constructor for these exact coordinates.
		SlotGrid.drawPlayerInventoryGrid(context, x, y + 84, y + 142);
	}
}
