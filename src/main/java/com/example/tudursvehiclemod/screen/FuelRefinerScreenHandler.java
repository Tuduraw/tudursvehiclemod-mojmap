package com.example.tudursvehiclemod.screen;

import com.example.tudursvehiclemod.block.FuelRefinerBlockEntity;
import com.example.tudursvehiclemod.item.FuelCanItem;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import com.example.tudursvehiclemod.block.FuelValues;

/** See block.FuelRefinerBlockEntity's own doc for what slot 0/1 do. */
public class FuelRefinerScreenHandler extends AbstractContainerMenu {

	private final Container inventory;
	private final ContainerData propertyDelegate;

	/** Client-side constructor (see registry.ModScreenHandlers' own factory registration). */
	public FuelRefinerScreenHandler(int syncId, Inventory playerInventory) {
		this(syncId, playerInventory, new SimpleContainer(2), new SimpleContainerData(1));
	}

	public FuelRefinerScreenHandler(int syncId, Inventory playerInventory, Container inventory,
			ContainerData propertyDelegate) {
		super(com.example.tudursvehiclemod.registry.ModScreenHandlers.FUEL_REFINER, syncId);
		this.inventory = inventory;
		this.propertyDelegate = propertyDelegate;
		inventory.startOpen(playerInventory.player);

		this.addSlot(new Slot(inventory, 0, 62, 24) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return FuelValues.isFuel(stack);
			}
		});
		this.addSlot(new Slot(inventory, 1, 62, 52) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return stack.getItem() instanceof FuelCanItem;
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

		this.addDataSlots(propertyDelegate);
	}

	/** 0.0-1.0, for the screen's own stored-burn-time progress bar. */
	public float getStoredBurnTimeRatio() {
		return Mth.clamp(this.propertyDelegate.get(0) / (float) FuelRefinerBlockEntity.STORED_BURN_TIME_GAUGE_SCALE, 0f, 1f);
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
			if (index < 2) {
				// From the device's own slots into the player's inventory.
				if (!this.moveItemStackTo(original, 2, this.slots.size(), true)) {
					return ItemStack.EMPTY;
				}
			} else {
				// From the player's inventory into whichever device slot fits.
				if (FuelValues.isFuel(original)) {
					if (!this.moveItemStackTo(original, 0, 1, false)) {
						return ItemStack.EMPTY;
					}
				} else if (original.getItem() instanceof FuelCanItem) {
					if (!this.moveItemStackTo(original, 1, 2, false)) {
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
