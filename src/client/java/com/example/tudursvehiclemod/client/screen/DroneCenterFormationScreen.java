package com.example.tudursvehiclemod.client.screen;

import com.example.tudursvehiclemod.screen.DroneCenterFormationScreenHandler;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** The wingman-slot screen, mirroring VehicleMenuScreen's own pagination pattern (SlotGrid-drawn cells, prev/next page buttons) but much simpler - no repair/resupply tabs, just the slot grid itself plus a "N機編成" label showing the currently-synced formation size. No custom GUI background PNG of its own, same reasoning as VehicleMenuScreen/FuelRefinerScreen (see those classes' own doc). */
public class DroneCenterFormationScreen extends AbstractContainerScreen<DroneCenterFormationScreenHandler> {

	private static final int PANEL_COLOR = 0xFFC6C6C6;
	private static final int BORDER_COLOR = 0xFF8B8B8B;

	public DroneCenterFormationScreen(DroneCenterFormationScreenHandler handler, Inventory inventory, Component title) {
		super(handler, inventory, title);
		this.imageWidth = 176;
		this.imageHeight = 18 + DroneCenterFormationScreenHandler.PAGE_ROWS * 18 + 14 + 3 * 18 + 14 + 18;
		this.inventoryLabelY = this.imageHeight - 94;
	}

	@Override
	protected void init() {
		super.init();
		int slotCount = this.getMenu().tudursvehiclemod$getFormationSize() - 1;
		int totalPages = slotCount <= 0 ? 1
				: (slotCount + DroneCenterFormationScreenHandler.PAGE_SIZE - 1) / DroneCenterFormationScreenHandler.PAGE_SIZE;
		if (totalPages > 1) {
			int currentPage = this.getMenu().tudursvehiclemod$getCurrentPage();
			int navY = this.topPos + 4;
			Button prevPage = Button.builder(Component.literal("<"), button -> {
				if (this.minecraft != null && this.minecraft.gameMode != null) {
					this.minecraft.gameMode.handleInventoryButtonClick(this.getMenu().containerId, DroneCenterFormationScreenHandler.PAGE_PREV_BUTTON_ID);
				}
			}).bounds(this.leftPos + this.imageWidth - 40, navY, 16, 16).build();
			prevPage.active = currentPage > 0;
			this.addRenderableWidget(prevPage);
			Button nextPage = Button.builder(Component.literal(">"), button -> {
				if (this.minecraft != null && this.minecraft.gameMode != null) {
					this.minecraft.gameMode.handleInventoryButtonClick(this.getMenu().containerId, DroneCenterFormationScreenHandler.PAGE_NEXT_BUTTON_ID);
				}
			}).bounds(this.leftPos + this.imageWidth - 20, navY, 16, 16).build();
			nextPage.active = currentPage < totalPages - 1;
			this.addRenderableWidget(nextPage);
		}
	}

	@Override
	protected void renderBg(GuiGraphics context, float delta, int mouseX, int mouseY) {
		int x = this.leftPos;
		int y = this.topPos;
		context.fill(x, y, x + this.imageWidth, y + this.imageHeight, PANEL_COLOR);
		tudursvehiclemod$fillBorder(context, x, y, this.imageWidth, this.imageHeight, BORDER_COLOR);

		int formationSize = this.getMenu().tudursvehiclemod$getFormationSize();
		context.drawString(this.font, Component.translatable("gui.tudursvehiclemod.drone_center_formation.size", formationSize),
				x + 8, y + 6, 0xFFFFFFFF, false);

		int slotCount = Math.max(0, formationSize - 1);
		int pageStart = this.getMenu().tudursvehiclemod$getCurrentPage() * DroneCenterFormationScreenHandler.PAGE_SIZE;
		int visibleThisPage = Math.min(DroneCenterFormationScreenHandler.PAGE_SIZE, Math.max(0, slotCount - pageStart));
		for (int i = 0; i < visibleThisPage; i++) {
			int col = i % 9;
			int row = i / 9;
			SlotGrid.drawSlot(context, x + 8 + col * 18, y + 18 + row * 18);
		}

		int playerInvY = y + 18 + DroneCenterFormationScreenHandler.PAGE_ROWS * 18 + 14;
		SlotGrid.drawPlayerInventoryGrid(context, x, playerInvY, playerInvY + 58);
	}

	/** drawBorder(int, int, int, int, int) was removed from DrawContext as of this project's own Minecraft version. */
	private static void tudursvehiclemod$fillBorder(GuiGraphics context, int x, int y, int width, int height, int color) {
		context.fill(x, y, x + width, y + 1, color);
		context.fill(x, y + height - 1, x + width, y + height, color);
		context.fill(x, y, x + 1, y + height, color);
		context.fill(x + width - 1, y, x + width, y + height, color);
	}
}
