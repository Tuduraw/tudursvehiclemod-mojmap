package com.example.tudursvehiclemod.screen;

import com.example.tudursvehiclemod.block.VehicleConverterBlockEntity;
import com.example.tudursvehiclemod.item.TieredVehicleBaseItem;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/** See block.VehicleConverterBlockEntity's own doc for what slot 0 and the button IDs below do. */
public class VehicleConverterScreenHandler extends AbstractContainerMenu {

	/** Per VehicleConverterBlockEntity's own doc: onButtonClick()'s own button IDs - matching this project's own convention elsewhere of small, self-documenting int constants rather than magic numbers scattered across both the client screen (which sends these) and this class (which receives them). */
	public static final int BUTTON_PREVIOUS_PAGE = 0;
	public static final int BUTTON_NEXT_PAGE = 1;
	public static final int BUTTON_CONFIRM = 2;

	private final Container inventory;
	private final VehicleConverterBlockEntity blockEntity;
	private final ContainerData propertyDelegate;

	/** Client-side constructor (see registry.ModScreenHandlers' own factory registration). */
	public VehicleConverterScreenHandler(int syncId, Inventory playerInventory) {
		this(syncId, playerInventory, null, new SimpleContainerData(1));
	}

	public VehicleConverterScreenHandler(int syncId, Inventory playerInventory, VehicleConverterBlockEntity blockEntity,
			ContainerData propertyDelegate) {
		super(com.example.tudursvehiclemod.registry.ModScreenHandlers.VEHICLE_CONVERTER, syncId);
		this.blockEntity = blockEntity;
		this.inventory = blockEntity != null ? blockEntity : new SimpleContainer(1);
		this.propertyDelegate = propertyDelegate;
		this.inventory.startOpen(playerInventory.player);

		this.addSlot(new Slot(this.inventory, 0, 80, 35) {
			@Override
			public boolean mayPlace(ItemStack stack) {
				return stack.getItem() instanceof TieredVehicleBaseItem;
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

	/** For the screen's own display - see item.VehicleCategory's own values(). */
	public int getSelectedCategoryIndex() {
		return this.propertyDelegate.get(0);
	}

	/** Per VehicleConverterBlockEntity's own doc: the standard vanilla button-click mechanism (same as an enchanting table/loom) - no custom network payload needed at all. Server-side only; a client press is a no-op if this.blockEntity is null (the client-side constructor's own placeholder, never actually used for real interaction - see that constructor's own doc). */
	@Override
	public boolean clickMenuButton(Player player, int id) {
		if (this.blockEntity == null) {
			return false;
		}
		switch (id) {
			case BUTTON_PREVIOUS_PAGE -> this.blockEntity.tudursvehiclemod$cyclePage(-1);
			case BUTTON_NEXT_PAGE -> this.blockEntity.tudursvehiclemod$cyclePage(1);
			case BUTTON_CONFIRM -> this.blockEntity.tudursvehiclemod$tryConvert(player);
			default -> {
				return false;
			}
		}
		return true;
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
				if (!this.moveItemStackTo(original, 1, this.slots.size(), true)) {
					return ItemStack.EMPTY;
				}
			} else {
				if (original.getItem() instanceof TieredVehicleBaseItem) {
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
