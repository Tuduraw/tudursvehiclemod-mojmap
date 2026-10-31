package com.example.tudursvehiclemod.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
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

/** Fire Support Center - see FireSupportCenterBlockEntity's own doc. Right-click opens its screen (unit and book slots plus every setting); sneak-right-click with a Fire Support Designator links the designator to it instead (FireSupportDesignatorItem.useOnBlock()). */
public class FireSupportCenterBlock extends BaseEntityBlock {

	public static final MapCodec<FireSupportCenterBlock> CODEC = simpleCodec(FireSupportCenterBlock::new);

	public FireSupportCenterBlock(Properties settings) {
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
		return new FireSupportCenterBlockEntity(pos, state);
	}

	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level world, BlockState state, BlockEntityType<T> type) {
		return world.isClientSide() ? null
				: createTickerHelper(type, com.example.tudursvehiclemod.registry.ModBlockEntities.FIRE_SUPPORT_CENTER, FireSupportCenterBlockEntity::tick);
	}

	@Override
	public void setPlacedBy(Level world, BlockPos pos, BlockState state, net.minecraft.world.entity.LivingEntity placer, ItemStack itemStack) {
		super.setPlacedBy(world, pos, state, placer, itemStack);
		if (world instanceof ServerLevel serverWorld && world.getBlockEntity(pos) instanceof FireSupportCenterBlockEntity blockEntity) {
			blockEntity.tudursvehiclemod$onRedstonePowerChanged(serverWorld, world.hasNeighborSignal(pos));
		}
	}

	/** Redstone control - a no-op unless the center has it enabled (see FireSupportCenterBlockEntity.tudursvehiclemod$onRedstonePowerChanged()). */
	@Override
	protected void neighborChanged(BlockState state, Level world, BlockPos pos, net.minecraft.world.level.block.Block sourceBlock,
			net.minecraft.world.level.redstone.Orientation wireOrientation, boolean notify) {
		super.neighborChanged(state, world, pos, sourceBlock, wireOrientation, notify);
		if (world instanceof ServerLevel serverWorld && world.getBlockEntity(pos) instanceof FireSupportCenterBlockEntity blockEntity) {
			blockEntity.tudursvehiclemod$onRedstonePowerChanged(serverWorld, world.hasNeighborSignal(pos));
		}
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player, BlockHitResult hit) {
		return this.tudursvehiclemod$openScreen(world, pos, player);
	}

	/** Called (instead of onUse()) while holding any item. Sneaking with an item never gets here at all - vanilla hands that straight to the item, which is how the designator links itself. */
	@Override
	protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level world, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
		return this.tudursvehiclemod$openScreen(world, pos, player);
	}

	private InteractionResult tudursvehiclemod$openScreen(Level world, BlockPos pos, Player player) {
		if (world instanceof ServerLevel serverWorld && player instanceof ServerPlayer serverPlayer
				&& world.getBlockEntity(pos) instanceof FireSupportCenterBlockEntity blockEntity) {
			serverPlayer.openMenu(blockEntity);
			com.example.tudursvehiclemod.network.FireSupportNetworking.tudursvehiclemod$sendState(serverPlayer, serverWorld, blockEntity);
		}
		return InteractionResult.SUCCESS;
	}
}
