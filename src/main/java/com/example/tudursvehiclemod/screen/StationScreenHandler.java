package com.example.tudursvehiclemod.screen;

import com.example.tudursvehiclemod.item.DroneControlStickItem;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** See block.StationBlockEntity's own doc for what slot 0 does (binding stick). */
public class StationScreenHandler extends AbstractContainerMenu {

	private final Container inventory;

	/** Client-side constructor (see registry.ModScreenHandlers' own factory registration). */
	public StationScreenHandler(int syncId, Inventory playerInventory) {
		this(syncId, playerInventory, new SimpleContainer(1));
	}

	public StationScreenHandler(int syncId, Inventory playerInventory, Container inventory) {
		super(com.example.tudursvehiclemod.registry.ModScreenHandlers.STATION, syncId);
		this.inventory = inventory;
		inventory.startOpen(playerInventory.player);

		this.addSlot(new Slot(inventory, 0, 80, 35) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return stack.getItem() instanceof DroneControlStickItem;
			}
		});

		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
			}
		}
		for (int col = 0; col < 9; col++) {
			this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
		}
	}

	@Override
	public boolean stillValid(Player player) {
		return this.inventory.stillValid(player);
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		ItemStack result = ItemStack.EMPTY;
		Slot slot = this.slots.get(index);
		if (slot != null && slot.hasItem()) {
			ItemStack original = slot.getItem();
			result = original.copy();
			if (index == 0) {
				// From the device's own slot into the player's inventory.
				if (!this.moveItemStackTo(original, 1, this.slots.size(), true)) {
					return ItemStack.EMPTY;
				}
			} else {
				// From the player's inventory into the device's own slot, if it fits.
				if (original.getItem() instanceof DroneControlStickItem) {
					if (!this.moveItemStackTo(original, 0, 1, false)) {
						return ItemStack.EMPTY;
					}
				} else {
					return ItemStack.EMPTY;
				}
			}
			if (original.isEmpty()) {
				slot.setByPlayer(ItemStack.EMPTY);
			} else {
				slot.setChanged();
			}
		}
		return result;
	}
}
