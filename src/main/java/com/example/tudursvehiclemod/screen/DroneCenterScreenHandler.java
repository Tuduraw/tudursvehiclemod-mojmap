package com.example.tudursvehiclemod.screen;

import com.example.tudursvehiclemod.item.DroneControlStickItem;
import com.example.tudursvehiclemod.item.DroneRouteBookItem;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** See block.DroneCenterBlockEntity's own doc for what slot 0/1 do (binding stick / route book respectively). */
public class DroneCenterScreenHandler extends AbstractContainerMenu {

	private final Container inventory;
	/** Null client-side when this screen was opened without a known block position yet (shouldn't normally happen, but avoids a hard crash if it does) - the config-screen-opening button just does nothing in that case. */
	private final BlockPos blockPos;

	/** Client-side constructor (see registry.ModScreenHandlers' own factory registration). */
	public DroneCenterScreenHandler(int syncId, Inventory playerInventory) {
		this(syncId, playerInventory, new SimpleContainer(2), null);
	}

	public DroneCenterScreenHandler(int syncId, Inventory playerInventory, Container inventory, BlockPos blockPos) {
		super(com.example.tudursvehiclemod.registry.ModScreenHandlers.DRONE_CENTER, syncId);
		this.inventory = inventory;
		this.blockPos = blockPos;
		inventory.startOpen(playerInventory.player);

		this.addSlot(new Slot(inventory, 0, 62, 24) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return stack.getItem() instanceof DroneControlStickItem;
			}
		});
		this.addSlot(new Slot(inventory, 1, 62, 52) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return stack.getItem() instanceof DroneRouteBookItem;
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

	/** Block position this screen was opened for - used by the client's own "Open Config" button to know which Drone Center to request settings for. Null only if this screen handler was somehow constructed without one (defensive; shouldn't normally happen). */
	public BlockPos tudursvehiclemod$getBlockPos() {
		return this.blockPos;
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
				if (original.getItem() instanceof DroneControlStickItem) {
					if (!this.moveItemStackTo(original, 0, 1, false)) {
						return ItemStack.EMPTY;
					}
				} else if (original.getItem() instanceof DroneRouteBookItem) {
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
