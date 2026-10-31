package com.example.tudursvehiclemod.block;

import com.example.tudursvehiclemod.item.FuelCanItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** Slot 0: any furnace-style fuel item (coal, a lava bucket,..). */
public class FuelRefinerBlockEntity extends BlockEntity implements Container, MenuProvider {

	/** A generous cap - a stack of 64 coal (1600 ticks each) is 102,400, so this comfortably holds a full stack's worth without needing to be emptied constantly. */
	public static final int MAX_STORED_BURN_TIME = 128_000;
	/** The full-gauge value of the screen property (see the PropertyDelegate below) - fits in a short. */
	public static final int STORED_BURN_TIME_GAUGE_SCALE = 10_000;
	/** How much stored burn time converts to 1 unit of can fuel per tick. */
	private static final int BURN_TIME_PER_FUEL_UNIT = 4;
	/** Multiplies the fuel units actually produced per unit of burn time consumed, without changing how much burn time gets consumed for that output. */
	private static final int FUEL_YIELD_MULTIPLIER = 10;
	/** How many units of can fuel transfer per tick while a can sits in slot 1. */
	private static final int FUEL_TRANSFER_PER_TICK = 20;

	private final NonNullList<ItemStack> items = NonNullList.withSize(2, ItemStack.EMPTY);
	private int storedBurnTime;

	public FuelRefinerBlockEntity(BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
		super(com.example.tudursvehiclemod.registry.ModBlockEntities.FUEL_REFINER, pos, state);
	}

	public static void tick(net.minecraft.world.level.Level world, BlockPos pos, net.minecraft.world.level.block.state.BlockState state,
			FuelRefinerBlockEntity blockEntity) {
		if (world.isClientSide()) {
			return;
		}
		boolean dirty = false;

		ItemStack fuelSlot = blockEntity.items.get(0);
		if (blockEntity.storedBurnTime < MAX_STORED_BURN_TIME && !fuelSlot.isEmpty()
				&& FuelValues.isFuel(fuelSlot)) {
			int fuelTime = FuelValues.getFuelTicks(fuelSlot);
			if (fuelTime > 0) {
				blockEntity.storedBurnTime = Math.min(MAX_STORED_BURN_TIME, blockEntity.storedBurnTime + fuelTime);
				fuelSlot.shrink(1);
				dirty = true;
			}
		}

		ItemStack canSlot = blockEntity.items.get(1);
		if (blockEntity.storedBurnTime > 0 && !canSlot.isEmpty() && canSlot.getItem() instanceof FuelCanItem) {
			int canFuel = FuelCanItem.getFuel(canSlot);
			if (canFuel < FuelCanItem.MAX_FUEL) {
				int burnTimeAvailable = Math.min(blockEntity.storedBurnTime, FUEL_TRANSFER_PER_TICK * BURN_TIME_PER_FUEL_UNIT);
				int fuelUnitsToAdd = (burnTimeAvailable / BURN_TIME_PER_FUEL_UNIT) * FUEL_YIELD_MULTIPLIER;
				fuelUnitsToAdd = Math.min(fuelUnitsToAdd, FuelCanItem.MAX_FUEL - canFuel);
				if (fuelUnitsToAdd > 0) {
					// Burn time consumed is based on the UNMULTIPLIED fuel units (fuelUnitsToAdd / FUEL_YIELD_MULTIPLIER, i.e.
					blockEntity.storedBurnTime -= (fuelUnitsToAdd / FUEL_YIELD_MULTIPLIER) * BURN_TIME_PER_FUEL_UNIT;
					FuelCanItem.setFuel(canSlot, canFuel + fuelUnitsToAdd);
					dirty = true;
				}
			}
		}

		if (dirty) {
			blockEntity.setChanged();
		}
	}

	/** 0.0-1.0, for the screen's own progress bar. */
	public float getStoredBurnTimeRatio() {
		return Mth.clamp((float) this.storedBurnTime / MAX_STORED_BURN_TIME, 0f, 1f);
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
		if (slot == 0) {
			return FuelValues.isFuel(stack);
		}
		if (slot == 1) {
			return stack.getItem() instanceof FuelCanItem;
		}
		return false;
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
		this.storedBurnTime = view.getIntOr("StoredBurnTime", 0);
	}

	@Override
	protected void saveAdditional(ValueOutput view) {
		super.saveAdditional(view);
		ContainerHelper.saveAllItems(view, this.items, true);
		view.putInt("StoredBurnTime", this.storedBurnTime);
	}

	@Override
	public Component getDisplayName() {
		return Component.translatable("block.tudursvehiclemod.fuel_refiner");
	}

	@Override
	public AbstractContainerMenu createMenu(int syncId, net.minecraft.world.entity.player.Inventory playerInventory, Player player) {
		return new com.example.tudursvehiclemod.screen.FuelRefinerScreenHandler(syncId, playerInventory, this,
				this.tudursvehiclemod$createPropertyDelegate());
	}

	private ContainerData tudursvehiclemod$createPropertyDelegate() {
		return new ContainerData() {
			@Override
			public int get(int index) {
				// Screen properties travel as shorts (-32768 to 32767), but the store holds up to MAX_STORED_BURN_TIME (128000): past 32767 the gauge wrapped round - a lava bucket and a coal block showed it empty. What the screen gets is the fill level in hundredths of a percent (0 to STORED_BURN_TIME_GAUGE_SCALE) instead - all it ever draws.
				return index == 0
						? (int) ((long) FuelRefinerBlockEntity.this.storedBurnTime * STORED_BURN_TIME_GAUGE_SCALE / MAX_STORED_BURN_TIME)
						: 0;
			}

			@Override
			public void set(int index, int value) {
				// Read-only from the screen's side: only the server's copy is ever set, and that from the block entity itself.
			}

			@Override
			public int getCount() {
				return 1;
			}
		};
	}
}
