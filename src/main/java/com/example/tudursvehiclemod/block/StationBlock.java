package com.example.tudursvehiclemod.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** UAV control station block (/ "右クリック時の挙動"). */
public class StationBlock extends BaseEntityBlock {

	public static final MapCodec<StationBlock> CODEC = simpleCodec(StationBlock::new);

	public StationBlock(Properties settings) {
		super(settings);
	}

	@Override
	protected MapCodec<? extends BaseEntityBlock> codec() {
		return CODEC;
	}

	@Override
	protected net.minecraft.world.level.block.RenderShape getRenderShape(BlockState state) {
		return net.minecraft.world.level.block.RenderShape.MODEL;
	}

	@Override
	public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
		return new StationBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level world, BlockState state, BlockEntityType<T> type) {
		return createTickerHelper(type, com.example.tudursvehiclemod.registry.ModBlockEntities.STATION, StationBlockEntity::tick);
	}

	@Override
	public void setPlacedBy(Level world, BlockPos pos, BlockState state, net.minecraft.world.entity.LivingEntity placer, ItemStack itemStack) {
		super.setPlacedBy(world, pos, state, placer, itemStack);
		if (world.getBlockEntity(pos) instanceof StationBlockEntity blockEntity) {
			blockEntity.tudursvehiclemod$updateChunkForceLoading(true);
		}
	}

	// Breaking the block: see StationBlockEntity.onBlockReplaced() - in 1.21.11 the block entity is already gone by the time onStateReplaced() runs, so the cleanup lives there.

	/** The previous sneak-right-click access to the item slot container UI was removed entirely (sneaking while holding any item bypasses block interaction unconditionally in vanilla Minecraft - see tudursvehiclemod$registerUseBlockCallback()'s own doc), consolidating the remote-control toggle and the item slots into one reachable screen: right-click (sneaking or not, identically) always opens client.screen.StationMenuScreen, which offers a "Toggle Remote Control" button (running the exact logic that used to fire immediately on right-click - see tudursvehiclemod$handleToggleRemoteControl()'s own doc) and an "Item Slots" button. */
	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player, BlockHitResult hit) {
		return this.tudursvehiclemod$handleUse(state, world, pos, player, hit);
	}

	/** This block's own interaction (opening the station menu screen - see onUse()'s own doc) only ever worked while empty-handed: Minecraft calls THIS method first whenever the interacting player is holding any item at all, only falling through to the plain onUse() above when empty-handed - without overriding it too, held-item interactions never reached this block's own logic at all. Delegates to the exact same handling regardless of whatever item (if any) is held, since none of that logic actually depends on the held stack. */
	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level world, BlockPos pos, Player player, net.minecraft.world.InteractionHand hand, BlockHitResult hit) {
		return this.tudursvehiclemod$handleUse(state, world, pos, player, hit);
	}

	private InteractionResult tudursvehiclemod$handleUse(BlockState state, Level world, BlockPos pos, Player player, BlockHitResult hit) {
		// A remnant of the earlier "reach this handler even while sneaking" fix (see tudursvehiclemod$registerUseBlockCallback()'s own doc) that no longer applies.
		if (player.isShiftKeyDown()) {
			return InteractionResult.PASS;
		}
		if (!(world instanceof ServerLevel) || !(world.getBlockEntity(pos) instanceof StationBlockEntity)) {
			return InteractionResult.SUCCESS;
		}
		if (!(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResult.SUCCESS;
		}
		net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(serverPlayer,
				new com.example.tudursvehiclemod.network.StationMenuOpenPayload(pos.getX(), pos.getY(), pos.getZ()));
		return InteractionResult.SUCCESS;
	}

	/** Runs the same remote-control-toggle logic this block's own right-click used to run immediately, before that was replaced with client.screen.StationMenuScreen's own "Toggle Remote Control" button (see this class's own onUse() doc) - called from the network.StationToggleRemoteControlPayload's own handler. Re-validates the block entity and world from scratch rather than trusting anything about the client's own screen state. */
	public static void tudursvehiclemod$handleToggleRemoteControl(ServerLevel serverWorld, ServerPlayer serverPlayer, BlockPos pos) {
		if (!(serverWorld.getBlockEntity(pos) instanceof StationBlockEntity blockEntity)) {
			return;
		}

		java.util.UUID boundVehicleId = blockEntity.tudursvehiclemod$getBoundVehicleId();
		if (boundVehicleId == null) {
			serverPlayer.displayClientMessage(Component.translatable("block.tudursvehiclemod.station.no_vehicle_bound"), true);
			return;
		}

		// Per this class's own doc: the SAME button toggles both directions - exiting if this exact player is ALREADY remote-controlling the bound vehicle through this exact station right now.
		if (StationBlockEntity.tudursvehiclemod$findBoundVehicle(serverWorld, boundVehicleId)
				instanceof com.example.tudursvehiclemod.entity.AbstractVehicleEntity alreadyControlledVehicle
				&& alreadyControlledVehicle.tudursvehiclemod$getRemoteControllerId() != null
				&& alreadyControlledVehicle.tudursvehiclemod$getRemoteControllerId().equals(serverPlayer.getUUID())) {
			alreadyControlledVehicle.tudursvehiclemod$tryExitRemoteControl();
			serverPlayer.displayClientMessage(Component.translatable("block.tudursvehiclemod.station.control_ended"), true);
			return;
		}
		// Ending control is allowed from anywhere (above); starting it needs the player at the station - see ModNetworking.tudursvehiclemod$canReachBlock().
		if (!com.example.tudursvehiclemod.network.ModNetworking.tudursvehiclemod$canReachBlock(serverPlayer, pos)) {
			return;
		}

		// Rather than only ever trying the bound
		// vehicle's own CURRENT in-memory status once and giving up
		// immediately if it's not already found (which is exactly why
		// a distant vehicle could never actually be reached at all
		// before this change) - see StationBlockEntity's own
		// tudursvehiclemod$tryStartControlAttempt() doc for the full
		// on-demand chunk-loading/retry design this now goes through
		// instead.
		StationBlockEntity.Result result = blockEntity.tudursvehiclemod$tryStartControlAttempt(serverWorld, serverPlayer, pos);
		switch (result) {
			case SUCCESS -> serverPlayer.displayClientMessage(Component.translatable("block.tudursvehiclemod.station.control_started"), true);
			case CONTROL_UNAVAILABLE -> serverPlayer.displayClientMessage(Component.translatable("block.tudursvehiclemod.station.control_unavailable"), true);
			case NO_VEHICLE_BOUND -> serverPlayer.displayClientMessage(Component.translatable("block.tudursvehiclemod.station.no_vehicle_bound"), true);
			case NEVER_SEEN -> serverPlayer.displayClientMessage(Component.translatable("block.tudursvehiclemod.station.vehicle_not_found"), true);
			case PENDING -> serverPlayer.displayClientMessage(Component.translatable("block.tudursvehiclemod.station.control_pending"), true);
			case ALREADY_PENDING -> serverPlayer.displayClientMessage(Component.translatable("block.tudursvehiclemod.station.control_pending"), true);
		}
	}

	/** Confirmed via decompiling ServerPlayerInteractionManager for this exact Minecraft version: sneaking while holding any item at all - even in the off-hand - bypasses onUse()/onUseWithItem() entirely, unconditionally, regardless of block or item type. net.fabricmc.fabric.api.event.player.UseBlockCallback is injected at the very head of that same vanilla method, before this bypass check, so registering a listener there reaches this block's own interaction in every case. Called once from VehicleMod's own onInitialize(). */
	public static void tudursvehiclemod$registerUseBlockCallback() {
		net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
			BlockPos pos = hitResult.getBlockPos();
			BlockState state = world.getBlockState(pos);
			if (!(state.getBlock() instanceof StationBlock stationBlock)) {
				return InteractionResult.PASS;
			}
			return stationBlock.tudursvehiclemod$handleUse(state, world, pos, player, hitResult);
		});
	}
}
