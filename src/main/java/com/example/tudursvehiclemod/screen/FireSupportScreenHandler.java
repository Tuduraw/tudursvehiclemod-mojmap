package com.example.tudursvehiclemod.screen;

import com.example.tudursvehiclemod.block.FireSupportCenterBlockEntity;
import com.example.tudursvehiclemod.item.DroneControlStickItem;
import com.example.tudursvehiclemod.item.DroneRouteBookItem;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** Fire Support Center screen: UNIT_SLOTS stick slots (one linked vehicle each) and the route-book slot, above the player's inventory. Everything else on the screen (settings, statuses, the last notice) comes from network.FireSupportStatePayload into tudursvehiclemod$getState(), and goes back as network.FireSupportActionPayload. */
public class FireSupportScreenHandler extends AbstractContainerMenu {

	public static final int UNIT_SLOT_X = 8;
	public static final int UNIT_SLOT_Y = 18;
	public static final int UNIT_SLOT_SPACING = 20;
	public static final int BOOK_SLOT_X = 8;
	public static final int BOOK_SLOT_Y = 142;
	public static final int INVENTORY_X = 8;
	public static final int INVENTORY_Y = 168;
	public static final int HOTBAR_Y = 226;

	private final Container inventory;
	/** Known server-side from the start; client-side once the first state payload arrives. */
	private BlockPos blockPos;
	/** Client side: the latest state from the server (empty until the first one arrives). */
	private CompoundTag state = new CompoundTag();

	/** Client-side constructor (registry.ModScreenHandlers). */
	public FireSupportScreenHandler(int syncId, Inventory playerInventory) {
		this(syncId, playerInventory, new SimpleContainer(FireSupportCenterBlockEntity.UNIT_SLOTS + 1), null);
	}

	public FireSupportScreenHandler(int syncId, Inventory playerInventory, Container inventory, BlockPos blockPos) {
		super(com.example.tudursvehiclemod.registry.ModScreenHandlers.FIRE_SUPPORT_CENTER, syncId);
		this.inventory = inventory;
		this.blockPos = blockPos;
		inventory.startOpen(playerInventory.player);
		for (int unit = 0; unit < FireSupportCenterBlockEntity.UNIT_SLOTS; unit++) {
			this.addSlot(new Slot(inventory, unit, UNIT_SLOT_X, UNIT_SLOT_Y + unit * UNIT_SLOT_SPACING) {
				@Override
				public boolean mayPlace(ItemStack stack) {
					return stack.getItem() instanceof DroneControlStickItem;
				}

				@Override
				public int getMaxStackSize() {
					return 1;
				}
			});
		}
		this.addSlot(new Slot(inventory, FireSupportCenterBlockEntity.BOOK_SLOT, BOOK_SLOT_X, BOOK_SLOT_Y) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return stack.getItem() instanceof DroneRouteBookItem;
			}

			@Override
			public int getMaxStackSize() {
				return 1;
			}
		});
		for (int row = 0; row < 3; row++) {
			for (int col = 0; col < 9; col++) {
				this.addSlot(new Slot(playerInventory, col + row * 9 + 9, INVENTORY_X + col * 18, INVENTORY_Y + row * 18));
			}
		}
		for (int col = 0; col < 9; col++) {
			this.addSlot(new Slot(playerInventory, col, INVENTORY_X + col * 18, HOTBAR_Y));
		}
	}

	public BlockPos tudursvehiclemod$getBlockPos() {
		return this.blockPos;
	}

	public CompoundTag tudursvehiclemod$getState() {
		return this.state;
	}

	/** Client side, from network.FireSupportStatePayload. */
	public void tudursvehiclemod$applyState(BlockPos pos, CompoundTag newState) {
		this.blockPos = pos;
		this.state = newState;
	}

	@Override
	public boolean stillValid(Player player) {
		return this.inventory.stillValid(player);
	}

	@Override
	public ItemStack quickMoveStack(Player player, int index) {
		int ownSlots = FireSupportCenterBlockEntity.UNIT_SLOTS + 1;
		Slot slot = this.slots.get(index);
		if (slot == null || !slot.hasItem()) {
			return ItemStack.EMPTY;
		}
		ItemStack original = slot.getItem();
		ItemStack result = original.copy();
		if (index < ownSlots) {
			if (!this.moveItemStackTo(original, ownSlots, this.slots.size(), true)) {
				return ItemStack.EMPTY;
			}
		} else if (original.getItem() instanceof DroneControlStickItem) {
			if (!this.moveItemStackTo(original, 0, FireSupportCenterBlockEntity.UNIT_SLOTS, false)) {
				return ItemStack.EMPTY;
			}
		} else if (original.getItem() instanceof DroneRouteBookItem) {
			if (!this.moveItemStackTo(original, FireSupportCenterBlockEntity.BOOK_SLOT, ownSlots, false)) {
				return ItemStack.EMPTY;
			}
		} else {
			return ItemStack.EMPTY;
		}
		if (original.isEmpty()) {
			slot.setByPlayer(ItemStack.EMPTY);
		} else {
			slot.setChanged();
		}
		return result;
	}
}
