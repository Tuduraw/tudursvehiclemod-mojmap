package com.example.tudursvehiclemod.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
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

/** Target drone control center block (/ block.DroneCenterBlockEntity's own doc). */
public class DroneCenterBlock extends BaseEntityBlock {

	public static final MapCodec<DroneCenterBlock> CODEC = simpleCodec(DroneCenterBlock::new);

	public DroneCenterBlock(Properties settings) {
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
		return new DroneCenterBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level world, BlockState state, BlockEntityType<T> type) {
		return createTickerHelper(type, com.example.tudursvehiclemod.registry.ModBlockEntities.DRONE_CENTER, DroneCenterBlockEntity::tick);
	}

	@Override
	public void setPlacedBy(Level world, BlockPos pos, BlockState state, net.minecraft.world.entity.LivingEntity placer, ItemStack itemStack) {
		super.setPlacedBy(world, pos, state, placer, itemStack);
		if (world.getBlockEntity(pos) instanceof DroneCenterBlockEntity blockEntity) {
			blockEntity.tudursvehiclemod$updateChunkForceLoading(true);
			if (world instanceof ServerLevel serverWorld) {
				blockEntity.tudursvehiclemod$onRedstonePowerChanged(serverWorld, world.hasNeighborSignal(pos));
			}
		}
	}

	/** Reacts to any redstone power change at this position - a genuine no-op (via tudursvehiclemod$onRedstonePowerChanged()'s own early return) unless the block entity's own redstone-control mode is currently enabled. */
	@Override
	protected void neighborChanged(BlockState state, Level world, BlockPos pos, net.minecraft.world.level.block.Block sourceBlock,
			net.minecraft.world.level.redstone.Orientation wireOrientation, boolean notify) {
		super.neighborChanged(state, world, pos, sourceBlock, wireOrientation, notify);
		if (world instanceof ServerLevel serverWorld && world.getBlockEntity(pos) instanceof DroneCenterBlockEntity blockEntity) {
			blockEntity.tudursvehiclemod$onRedstonePowerChanged(serverWorld, world.hasNeighborSignal(pos));
		}
	}

	// Breaking the block: see DroneCenterBlockEntity.onBlockReplaced() - in 1.21.11 the block entity is already gone by the time onStateReplaced() runs, so the cleanup lives there.

	/** Right-click (sneaking or not, identically) always opens this block's own config screen (client.screen.DroneCenterConfigScreen - altitude/speed/turn-radius adjustment, an activate/deactivate toggle, and an "Item Slots" button that opens the slot UI instead - see that screen's own doc). */
	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player, BlockHitResult hit) {
		return this.tudursvehiclemod$handleUse(state, world, pos, player, hit);
	}

	/** This block's own interaction (opening either the slot UI or the config screen - see onUse()'s own doc) only ever worked while empty-handed: Minecraft calls THIS method first whenever the interacting player is holding any item at all, only falling through to the plain onUse() above when empty-handed - without overriding it too, held-item interactions never reached this block's own logic at all. Delegates to the exact same handling regardless of whatever item (if any) is held, since none of that logic actually depends on the held stack. */
	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level world, BlockPos pos, Player player, net.minecraft.world.InteractionHand hand, BlockHitResult hit) {
		return this.tudursvehiclemod$handleUse(state, world, pos, player, hit);
	}

	private InteractionResult tudursvehiclemod$handleUse(BlockState state, Level world, BlockPos pos, Player player, BlockHitResult hit) {
		// A remnant of the earlier "reach this handler even while sneaking" fix (see tudursvehiclemod$registerUseBlockCallback()'s own doc) that no longer applies.
		if (player.isShiftKeyDown()) {
			return InteractionResult.PASS;
		}
		if (!(world instanceof ServerLevel serverWorld) || !(world.getBlockEntity(pos) instanceof DroneCenterBlockEntity blockEntity)) {
			return InteractionResult.SUCCESS;
		}
		if (!(player instanceof ServerPlayer serverPlayer)) {
			return InteractionResult.SUCCESS;
		}

		net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(serverPlayer,
				new com.example.tudursvehiclemod.network.DroneCenterConfigOpenPayload(
						pos.getX(), pos.getY(), pos.getZ(),
						blockEntity.tudursvehiclemod$getSpeedFraction(),
						(float) blockEntity.tudursvehiclemod$getOrbitAltitude(),
						blockEntity.tudursvehiclemod$getRadiusMultiplier(),
						blockEntity.tudursvehiclemod$isActive(),
						blockEntity.tudursvehiclemod$isRedstoneControlEnabled(),
						blockEntity.tudursvehiclemod$encodeFormationConfig()));
		return InteractionResult.SUCCESS;
	}

	/** Confirmed via decompiling ServerPlayerInteractionManager for this exact Minecraft version: sneaking while holding any item at all - even in the off-hand - bypasses onUse()/onUseWithItem() entirely, unconditionally, regardless of block or item type: net.fabricmc.fabric.api.event.player.UseBlockCallback is injected at the very head of that same vanilla method, before this bypass check, so registering a listener there reaches this block's own interaction in every case. Called once from VehicleMod's own onInitialize(). */
	public static void tudursvehiclemod$registerUseBlockCallback() {
		net.fabricmc.fabric.api.event.player.UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
			BlockPos pos = hitResult.getBlockPos();
			BlockState state = world.getBlockState(pos);
			if (!(state.getBlock() instanceof DroneCenterBlock droneCenterBlock)) {
				return InteractionResult.PASS;
			}
			return droneCenterBlock.tudursvehiclemod$handleUse(state, world, pos, player, hitResult);
		});
	}
}
