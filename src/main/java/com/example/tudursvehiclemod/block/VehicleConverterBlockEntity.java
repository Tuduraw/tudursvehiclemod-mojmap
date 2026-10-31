package com.example.tudursvehiclemod.block;

import com.example.tudursvehiclemod.item.TieredVehicleBaseItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Converts a tier-based item.TieredVehicleBaseItem (slot 0, not scoped to any one vehicle kind) into a specific spawner item of the SAME tier, for whichever converter target the player has currently paged to (selectedCategoryIndex, synced to the client via PropertyDelegate so screen.VehicleConverterScreenHandler's own screen can display the current selection). The selectable targets are the built-in categories plus whatever an addon has registered - see item.VehicleConverterTargets' own doc. The actual conversion only happens on an explicit confirm (screen.VehicleConverterScreenHandler's own onButtonClick(), the same vanilla mechanism an enchanting table/loom already use for their own button-driven actions - see that class's own doc) - paging alone never touches the slot's own contents at all. */
public class VehicleConverterBlockEntity extends BlockEntity implements Container, MenuProvider {

	private final NonNullList<ItemStack> items = NonNullList.withSize(1, ItemStack.EMPTY);
	private int selectedCategoryIndex;

	public VehicleConverterBlockEntity(BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
		super(com.example.tudursvehiclemod.registry.ModBlockEntities.VEHICLE_CONVERTER, pos, state);
	}

	/** Per this class's own doc: cycles the currently-paged target - called from the screen handler's own onButtonClick(), never touches the input slot at all. Wraps around in both directions. Covers addon-registered targets too (see item.VehicleConverterTargets' own doc), not just the built-in categories. */
	public void tudursvehiclemod$cyclePage(int direction) {
		int targetCount = Math.max(1, com.example.tudursvehiclemod.item.VehicleConverterTargets.count());
		this.selectedCategoryIndex = Math.floorMod(this.selectedCategoryIndex + direction, targetCount);
		this.setChanged();
	}

	public com.example.tudursvehiclemod.item.VehicleConverterTarget tudursvehiclemod$getSelectedCategory() {
		return com.example.tudursvehiclemod.item.VehicleConverterTargets.byIndex(this.selectedCategoryIndex);
	}

	/** Per this class's own doc: the actual conversion, only ever called from the screen handler's own onButtonClick() confirm action. Does nothing at all (silently) if slot 0 doesn't actually hold a TieredVehicleBaseItem - the confirm button is always visible/clickable regardless of slot contents, so this validates for itself rather than trusting the client. Consumes exactly 1 of the base item and gives the player 1 matching-tier spawner item for whichever target is currently selected - directly into their own inventory (or dropped at their feet if that's somehow full), matching how a crafting-table result is normally handed to the player rather than left in a slot for them to manually retrieve. */
	public void tudursvehiclemod$tryConvert(Player player) {
		ItemStack inputStack = this.items.get(0);
		if (!(inputStack.getItem() instanceof TieredVehicleBaseItem baseItem)) {
			return;
		}
		com.example.tudursvehiclemod.item.VehicleConverterTarget target = this.tudursvehiclemod$getSelectedCategory();
		if (target == null) {
			return;
		}
		int tier = baseItem.getTier();
		Item[] tierArray = target.tieredSpawnerItems();
		if (tierArray == null || tier < 1 || tier > tierArray.length) {
			return;
		}
		Item resultItem = tierArray[tier - 1];
		if (resultItem == null) {
			return;
		}
		inputStack.shrink(1);
		this.setChanged();
		ItemStack resultStack = new ItemStack(resultItem);
		if (!player.getInventory().add(resultStack)) {
			player.drop(resultStack, false);
		}
	}

	@Override
	public int getContainerSize() {
		return this.items.size();
	}

	@Override
	public boolean isEmpty() {
		return this.items.stream().allMatch(ItemStack::isEmpty);
	}

	@Override
	public ItemStack getItem(int slot) {
		return this.items.get(slot);
	}

	@Override
	public ItemStack removeItem(int slot, int amount) {
		ItemStack result = ContainerHelper.removeItem(this.items, slot, amount);
		if (!result.isEmpty()) {
			this.setChanged();
		}
		return result;
	}

	@Override
	public ItemStack removeItemNoUpdate(int slot) {
		return ContainerHelper.takeItem(this.items, slot);
	}

	@Override
	public void setItem(int slot, ItemStack stack) {
		this.items.set(slot, stack);
		if (stack.getCount() > this.getMaxStackSize()) {
			stack.setCount(this.getMaxStackSize());
		}
		this.setChanged();
	}

	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		return stack.getItem() instanceof TieredVehicleBaseItem;
	}

	@Override
	public boolean stillValid(Player player) {
		return this.level != null && this.level.getBlockEntity(this.worldPosition) == this
				&& player.distanceToSqr(this.worldPosition.getX() + 0.5, this.worldPosition.getY() + 0.5, this.worldPosition.getZ() + 0.5) <= 64.0;
	}

	@Override
	public void clearContent() {
		this.items.clear();
	}

	@Override
	protected void loadAdditional(ValueInput view) {
		super.loadAdditional(view);
		ContainerHelper.loadAllItems(view, this.items);
		this.selectedCategoryIndex = view.getIntOr("SelectedCategoryIndex", 0);
	}

	@Override
	protected void saveAdditional(ValueOutput view) {
		super.saveAdditional(view);
		ContainerHelper.saveAllItems(view, this.items, true);
		view.putInt("SelectedCategoryIndex", this.selectedCategoryIndex);
	}

	@Override
	public Component getDisplayName() {
		return Component.translatable("block.tudursvehiclemod.vehicle_converter");
	}

	@Override
	public AbstractContainerMenu createMenu(int syncId, net.minecraft.world.entity.player.Inventory playerInventory, Player player) {
		return new com.example.tudursvehiclemod.screen.VehicleConverterScreenHandler(syncId, playerInventory, this,
				this.tudursvehiclemod$createPropertyDelegate());
	}

	private ContainerData tudursvehiclemod$createPropertyDelegate() {
		return new ContainerData() {
			@Override
			public int get(int index) {
				return index == 0 ? VehicleConverterBlockEntity.this.selectedCategoryIndex : 0;
			}

			@Override
			public void set(int index, int value) {
				if (index == 0) {
					VehicleConverterBlockEntity.this.selectedCategoryIndex = value;
				}
			}

			@Override
			public int getCount() {
				return 1;
			}
		};
	}
}
