package com.example.tudursvehiclemod.block;

import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/** Converts a tier-based item.TieredVehicleBaseItem into a specific item.TieredVehicleSpawnerItem - see block.VehicleConverterBlockEntity's own doc for the full conversion flow. Crafted from a crafting table surrounded by gunpowder (see recipe/vehicle_converter.json). */
public class VehicleConverterBlock extends BaseEntityBlock {

	public static final MapCodec<VehicleConverterBlock> CODEC = simpleCodec(VehicleConverterBlock::new);

	public VehicleConverterBlock(Properties settings) {
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
		return new VehicleConverterBlockEntity(pos, state);
	}

	@Override
	protected InteractionResult useWithoutItem(BlockState state, Level world, BlockPos pos, Player player, BlockHitResult hit) {
		if (!(world instanceof ServerLevel)) {
			return InteractionResult.SUCCESS;
		}
		if (world.getBlockEntity(pos) instanceof VehicleConverterBlockEntity blockEntity
				&& player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
			serverPlayer.openMenu(blockEntity);
		}
		return InteractionResult.SUCCESS;
	}
}
