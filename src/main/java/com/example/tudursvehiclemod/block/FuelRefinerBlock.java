package com.example.tudursvehiclemod.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class FuelRefinerBlock extends BaseEntityBlock {

	public static final MapCodec<FuelRefinerBlock> CODEC = simpleCodec(FuelRefinerBlock::new);

	public FuelRefinerBlock(Properties settings) {
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
		return new FuelRefinerBlockEntity(pos, state);
	}

	// Reuses BlockWithEntity's own protected static validateTicker(..) helper (defining another one here with the same erasure would conflict with it).
	@Override
	public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level world, BlockState state, BlockEntityType<T> type) {
		return createTickerHelper(type, com.example.tudursvehiclemod.registry.ModBlockEntities.FUEL_REFINER,
				FuelRefinerBlockEntity::tick);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player, BlockHitResult hit) {
		if (!(world instanceof ServerLevel)) {
			return InteractionResult.SUCCESS;
		}
		if (world.getBlockEntity(pos) instanceof FuelRefinerBlockEntity blockEntity
				&& player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
			serverPlayer.openMenu(blockEntity);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	protected boolean hasAnalogOutputSignal(BlockState state) {
		return true;
	}

	@Override
	protected int getAnalogOutputSignal(BlockState state, Level world, BlockPos pos, net.minecraft.core.Direction direction) {
		if (world.getBlockEntity(pos) instanceof FuelRefinerBlockEntity blockEntity) {
			return Math.round(blockEntity.getStoredBurnTimeRatio() * 15f);
		}
		return 0;
	}
}
