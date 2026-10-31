package com.example.tudursvehiclemod.block;

import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

	/** UAV control station (project-specific, not from MC Heli).
 * Has a real inventory slot (see screen.StationScreenHandler) for the
 * binding stick, matching block.DroneCenterBlockEntity's own equivalent redesign - rather
 * than the earlier sneak-click insert/extract mechanic. This station's own "bound vehicle"
 * is derived directly from whatever registered stick currently sits in slot 0 (see
 * tudursvehiclemod$getBoundVehicleId()) rather than tracked as a separate field that needed
 * manual bookkeeping to stay in sync with the slot. */
public class StationBlockEntity extends BlockEntity implements MenuProvider, Container {

	private final NonNullList<ItemStack> items = NonNullList.withSize(1, ItemStack.EMPTY);

	/** Bound vehicle's last-known chunk, persisted to disk - the only thing ever force-loaded now. */
	private ChunkPos lastKnownVehicleChunk;

	/** Player currently waiting on a pending control attempt to resolve, or null if none in progress. */
	private UUID pendingControlPlayerId;
	/** Ticks waited so far for the pending attempt's temporarily-forced chunk to load the vehicle back in. */
	private int pendingControlTicksWaited;
	/** Chunk temporarily force-loaded for the currently-pending attempt - remembered separately from lastKnownVehicleChunk so it can be un-forced correctly even if that's since changed. */
	private ChunkPos pendingControlForcedChunk;
	/** How long a pending control attempt is given before giving up (100 ticks = 5 real seconds). */
	private static final int PENDING_CONTROL_TIMEOUT_TICKS = 100;

	public StationBlockEntity(BlockPos pos, net.minecraft.world.level.block.state.BlockState state) {
		super(com.example.tudursvehiclemod.registry.ModBlockEntities.STATION, pos, state);
	}

	/** Derived directly from whatever registered stick currently sits in slot 0 (see this class's own doc) - null if slot 0 is empty, holds an unregistered stick, or holds something else entirely. */
	public UUID tudursvehiclemod$getBoundVehicleId() {
		ItemStack stick = this.items.get(0);
		return stick.getItem() instanceof com.example.tudursvehiclemod.item.DroneControlStickItem
				? com.example.tudursvehiclemod.item.DroneControlStickItem.getRegisteredVehicleId(stick) : null;
	}

	/** Called from StationBlock's onUse() when a player tries to control the bound vehicle. Mounts immediately if already found; otherwise force-loads lastKnownVehicleChunk and returns PENDING - tick() resolves it within PENDING_CONTROL_TIMEOUT_TICKS. */
	public Result tudursvehiclemod$tryStartControlAttempt(ServerLevel world, ServerPlayer player, BlockPos stationPos) {
		UUID boundVehicleId = this.tudursvehiclemod$getBoundVehicleId();
		if (boundVehicleId == null) {
			return Result.NO_VEHICLE_BOUND;
		}
		if (this.pendingControlPlayerId != null) {
			return Result.ALREADY_PENDING;
		}
		com.example.tudursvehiclemod.entity.AbstractVehicleEntity vehicle = tudursvehiclemod$findBoundVehicle(world, boundVehicleId);
		if (vehicle != null) {
			this.tudursvehiclemod$rememberVehicleChunk(vehicle);
			return vehicle.tudursvehiclemod$tryEnterRemoteControl(player, stationPos) ? Result.SUCCESS : Result.CONTROL_UNAVAILABLE;
		}
		if (this.lastKnownVehicleChunk == null) {
			return Result.NEVER_SEEN;
		}
		com.example.tudursvehiclemod.ChunkForceTracker.requestWithoutLoading(world, this.lastKnownVehicleChunk, this.tudursvehiclemod$pendingControlRequester());
		this.pendingControlForcedChunk = this.lastKnownVehicleChunk;
		this.pendingControlPlayerId = player.getUUID();
		this.pendingControlTicksWaited = 0;
		return Result.PENDING;
	}

	/** Outcomes of tudursvehiclemod$tryStartControlAttempt() - StationBlock's onUse() maps each to a player message. PENDING gets a follow-up message once tick() resolves it. */
	public enum Result {
		SUCCESS, CONTROL_UNAVAILABLE, NO_VEHICLE_BOUND, NEVER_SEEN, PENDING, ALREADY_PENDING
	}

	private void tudursvehiclemod$rememberVehicleChunk(com.example.tudursvehiclemod.entity.AbstractVehicleEntity vehicle) {
		ChunkPos current = new ChunkPos(vehicle.blockPosition());
		if (!current.equals(this.lastKnownVehicleChunk)) {
			this.lastKnownVehicleChunk = current;
			this.setChanged();
		}
	}

	/** Public so AbstractVehicleEntity's removePassenger() can update lastKnownVehicleChunk directly on dismount, recording the vehicle's actual final position (rememberVehicleChunk() above only runs when a control attempt starts, not ends). */
	public void tudursvehiclemod$updateLastKnownVehicleChunk(ChunkPos chunk) {
		if (!chunk.equals(this.lastKnownVehicleChunk)) {
			this.lastKnownVehicleChunk = chunk;
			this.setChanged();
		}
	}

	public static void tick(net.minecraft.world.level.Level world, BlockPos pos, net.minecraft.world.level.block.state.BlockState state, StationBlockEntity blockEntity) {
		if (world.isClientSide() || !(world instanceof ServerLevel serverWorld)) {
			return;
		}
		if (blockEntity.pendingControlPlayerId == null) {
			return;
		}
		com.example.tudursvehiclemod.entity.AbstractVehicleEntity vehicle =
				tudursvehiclemod$findBoundVehicle(serverWorld, blockEntity.tudursvehiclemod$getBoundVehicleId());
		if (vehicle != null) {
			blockEntity.tudursvehiclemod$rememberVehicleChunk(vehicle);
			blockEntity.tudursvehiclemod$finishPendingControlAttempt(serverWorld, serverPlayer ->
					serverPlayer.displayClientMessage(Component.translatable(vehicle.tudursvehiclemod$tryEnterRemoteControl(serverPlayer, blockEntity.getBlockPos())
							? "block.tudursvehiclemod.station.control_started"
							: "block.tudursvehiclemod.station.control_unavailable"), true));
			return;
		}
		blockEntity.pendingControlTicksWaited++;
		if (blockEntity.pendingControlTicksWaited >= PENDING_CONTROL_TIMEOUT_TICKS) {
			blockEntity.tudursvehiclemod$finishPendingControlAttempt(serverWorld, serverPlayer ->
					serverPlayer.displayClientMessage(Component.translatable("block.tudursvehiclemod.station.vehicle_not_found"), true));
		}
	}

	private void tudursvehiclemod$finishPendingControlAttempt(ServerLevel world, java.util.function.Consumer<ServerPlayer> notify) {
		if (this.pendingControlForcedChunk != null) {
			com.example.tudursvehiclemod.ChunkForceTracker.release(world, this.pendingControlForcedChunk, this.tudursvehiclemod$pendingControlRequester());
			this.pendingControlForcedChunk = null;
		}
		UUID playerId = this.pendingControlPlayerId;
		this.pendingControlPlayerId = null;
		this.pendingControlTicksWaited = 0;
		if (world.getServer().getPlayerList().getPlayer(playerId) instanceof ServerPlayer player) {
			notify.accept(player);
		}
	}

	/** Resolves this station's own bound vehicle UUID (see tudursvehiclemod$getBoundVehicleId()'s own doc) against world's own entity list, or null if unbound / that vehicle no longer actually exists. Server-only (a UUID lookup needs the server's own full entity list, not just whatever's client-side loaded/visible right now). */
	public static com.example.tudursvehiclemod.entity.AbstractVehicleEntity tudursvehiclemod$findBoundVehicle(ServerLevel world, UUID vehicleId) {
		if (vehicleId == null) {
			return null;
		}
		Entity entity = world.getEntity(vehicleId);
		return entity instanceof com.example.tudursvehiclemod.entity.AbstractVehicleEntity vehicle ? vehicle : null;
	}

	/** Called when this station block is removed - releases any pending control attempt's temporarily-forced chunk, since tick() won't run again to do it. Usually a no-op (pending attempts only last a few ticks). */
	public void tudursvehiclemod$releasePendingControlChunk(ServerLevel world) {
		if (this.pendingControlForcedChunk != null) {
			com.example.tudursvehiclemod.ChunkForceTracker.release(world, this.pendingControlForcedChunk, this.tudursvehiclemod$pendingControlRequester());
			this.pendingControlForcedChunk = null;
		}
		this.pendingControlPlayerId = null;
		this.pendingControlTicksWaited = 0;
	}

	/** This station's own chunk stays loaded permanently for as long as the block entity exists, regardless of binding/control state. Goes through ChunkForceTracker (requester: this block entity), so an aircraft or Drone Center that also needs this chunk can't un-force it from under the station, and /tvm unloadchunks knows it's a live request. */
	public void tudursvehiclemod$updateChunkForceLoading(boolean forced) {
		if (this.getLevel() instanceof ServerLevel serverWorld) {
			ChunkPos ownChunk = new ChunkPos(this.getBlockPos());
			if (forced) {
				// Its own chunk is loaded by definition - requestWithoutLoading() skips the synchronous load, which would hang the server when this runs while that chunk is still loading (see VehicleMod's BLOCK_ENTITY_LOAD listener).
				com.example.tudursvehiclemod.ChunkForceTracker.requestWithoutLoading(serverWorld, ownChunk, this);
			} else {
				com.example.tudursvehiclemod.ChunkForceTracker.release(serverWorld, ownChunk, this);
			}
		}
	}

	/** A pending control attempt's chunk is a separate request from the station's own chunk - they can be the same chunk, and finishing the attempt must not un-force the station's. */
	private Object tudursvehiclemod$pendingControlRequester() {
		return new com.example.tudursvehiclemod.ChunkForceTracker.Purpose(this, "pending_control");
	}

	/** Breaking the station: ends remote control of its vehicle and releases every chunk it was force-loading. This used to be in StationBlock.onStateReplaced(), which in 1.21.11 runs only after this block entity has already been removed from the world (getBlockEntity() there returns null), so none of it ever ran. This runs while the block entity is still in place. super scatters the slot's stick. */
	@Override
	public void preRemoveSideEffects(BlockPos pos, net.minecraft.world.level.block.state.BlockState oldState) {
		if (this.getLevel() instanceof ServerLevel serverWorld) {
			this.tudursvehiclemod$releasePendingControlChunk(serverWorld);
			if (tudursvehiclemod$findBoundVehicle(serverWorld, this.tudursvehiclemod$getBoundVehicleId())
					instanceof com.example.tudursvehiclemod.entity.AbstractVehicleEntity vehicle) {
				vehicle.tudursvehiclemod$tryExitRemoteControl();
			}
			com.example.tudursvehiclemod.ChunkForceTracker.releaseAllOwnedBy(serverWorld, this);
		}
		super.preRemoveSideEffects(pos, oldState);
	}

	// --- NamedScreenHandlerFactory / Inventory (see this class's own doc) ---

	@Override
	public Component getDisplayName() {
		return Component.translatable("block.tudursvehiclemod.station");
	}

	@Override
	public AbstractContainerMenu createMenu(int syncId, Inventory playerInventory, Player player) {
		return new com.example.tudursvehiclemod.screen.StationScreenHandler(syncId, playerInventory, this);
	}

	@Override
	public int getContainerSize() {
		return this.items.size();
	}

	@Override
	public boolean isEmpty() {
		return this.items.get(0).isEmpty();
	}

	@Override
	public ItemStack getItem(int slot) {
		return this.items.get(slot);
	}

	@Override
	public ItemStack removeItem(int slot, int amount) {
		ItemStack result = net.minecraft.world.ContainerHelper.removeItem(this.items, slot, amount);
		if (!result.isEmpty()) {
			this.setChanged();
		}
		return result;
	}

	@Override
	public ItemStack removeItemNoUpdate(int slot) {
		return net.minecraft.world.ContainerHelper.takeItem(this.items, slot);
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
	public boolean stillValid(Player player) {
		return this.getLevel() != null && this.getLevel().getBlockEntity(this.getBlockPos()) == this
				&& player.distanceToSqr(this.getBlockPos().getX() + 0.5, this.getBlockPos().getY() + 0.5, this.getBlockPos().getZ() + 0.5) <= 64.0;
	}

	@Override
	public void clearContent() {
		this.items.clear();
	}

	@Override
	protected void loadAdditional(ValueInput view) {
		super.loadAdditional(view);
		ContainerHelper.loadAllItems(view, this.items);
		int lastChunkX = view.getIntOr("LastKnownVehicleChunkX", Integer.MIN_VALUE);
		int lastChunkZ = view.getIntOr("LastKnownVehicleChunkZ", Integer.MIN_VALUE);
		this.lastKnownVehicleChunk = lastChunkX == Integer.MIN_VALUE ? null : new ChunkPos(lastChunkX, lastChunkZ);
	}

	@Override
	protected void saveAdditional(ValueOutput view) {
		super.saveAdditional(view);
		ContainerHelper.saveAllItems(view, this.items, true);
		if (this.lastKnownVehicleChunk != null) {
			view.putInt("LastKnownVehicleChunkX", this.lastKnownVehicleChunk.x);
			view.putInt("LastKnownVehicleChunkZ", this.lastKnownVehicleChunk.z);
		}
	}
}
