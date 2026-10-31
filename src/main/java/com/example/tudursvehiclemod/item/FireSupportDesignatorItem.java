package com.example.tudursvehiclemod.item;

import com.example.tudursvehiclemod.VehicleModServerConfig;
import com.example.tudursvehiclemod.block.FireSupportCenterBlock;
import com.example.tudursvehiclemod.block.FireSupportCenterBlockEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/** Remote handset for a Fire Support Center (block.FireSupportCenterBlockEntity).
 *
 * <ul>
 * <li>Sneak + right-click the center: links this designator to it.</li>
 * <li>Right-click: marks the block you are looking at (up to VehicleModServerConfig.fireSupportDesignatorRange) as the target - taken when the center is set to designator targeting.</li>
 * <li>Sneak + right-click anywhere else: turns support on or off.</li>
 * </ul>
 *
 * <p>Works only while the center has remote control enabled - only then are it and its vehicles kept loaded (see the center's doc) - and only from the center's own dimension. Every outcome is reported in the actionbar. */
public class FireSupportDesignatorItem extends Item {

	public FireSupportDesignatorItem(Properties settings) {
		super(settings);
	}

	public static GlobalPos tudursvehiclemod$getLink(ItemStack stack) {
		return stack.get(ModComponents.FIRE_SUPPORT_LINK);
	}

	@Override
	public InteractionResult useOn(UseOnContext context) {
		Level world = context.getLevel();
		Player player = context.getPlayer();
		if (player == null || !player.isShiftKeyDown() || !(world.getBlockState(context.getClickedPos()).getBlock() instanceof FireSupportCenterBlock)) {
			return InteractionResult.PASS;
		}
		if (world instanceof ServerLevel && player instanceof ServerPlayer serverPlayer) {
			context.getItemInHand().set(ModComponents.FIRE_SUPPORT_LINK, GlobalPos.of(world.dimension(), context.getClickedPos().immutable()));
			serverPlayer.displayClientMessage(Component.translatable("message.tudursvehiclemod.fire_support.linked",
					context.getClickedPos().getX(), context.getClickedPos().getY(), context.getClickedPos().getZ()), true);
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public InteractionResult use(Level world, Player user, InteractionHand hand) {
		if (!(world instanceof ServerLevel serverWorld) || !(user instanceof ServerPlayer player)) {
			return InteractionResult.SUCCESS;
		}
		FireSupportCenterBlockEntity center = tudursvehiclemod$findCenter(serverWorld, player, user.getItemInHand(hand));
		if (center == null) {
			return InteractionResult.FAIL;
		}
		if (player.isShiftKeyDown()) {
			center.tudursvehiclemod$setActive(serverWorld, !center.tudursvehiclemod$isActive(), player);
			return InteractionResult.SUCCESS;
		}
		if (center.tudursvehiclemod$getTargetMode() != FireSupportCenterBlockEntity.TargetMode.DESIGNATOR) {
			player.displayClientMessage(Component.translatable("message.tudursvehiclemod.fire_support.not_designator_mode"), true);
			return InteractionResult.FAIL;
		}
		HitResult hit = player.pick(VehicleModServerConfig.get().fireSupportDesignatorRange, 1.0f, false);
		if (!(hit instanceof BlockHitResult blockHit) || hit.getType() != HitResult.Type.BLOCK) {
			player.displayClientMessage(Component.translatable("message.tudursvehiclemod.fire_support.no_block"), true);
			return InteractionResult.FAIL;
		}
		BlockPos target = blockHit.getBlockPos();
		boolean wasActive = center.tudursvehiclemod$isActive();
		center.tudursvehiclemod$setDesignatedTarget(serverWorld, target, player);
		// A center that just went off over this target has already said why.
		if (!wasActive || center.tudursvehiclemod$isActive()) {
			player.displayClientMessage(Component.translatable("message.tudursvehiclemod.fire_support.designated",
					target.getX(), target.getY(), target.getZ(), (int) Math.round(Math.sqrt(target.distSqr(center.getBlockPos())))), true);
		}
		return InteractionResult.SUCCESS;
	}

	/** The linked center, if it can be reached right now; otherwise tells the player why not and returns null. */
	private static FireSupportCenterBlockEntity tudursvehiclemod$findCenter(ServerLevel world, ServerPlayer player, ItemStack stack) {
		GlobalPos link = tudursvehiclemod$getLink(stack);
		if (link == null) {
			player.displayClientMessage(Component.translatable("message.tudursvehiclemod.fire_support.not_linked"), true);
			return null;
		}
		if (!link.dimension().equals(world.dimension())) {
			player.displayClientMessage(Component.translatable("message.tudursvehiclemod.fire_support.other_dimension"), true);
			return null;
		}
		// Not loaded means remote control is off (it keeps the center loaded) - don't load it here.
		if (!world.hasChunkAt(link.pos()) || !(world.getBlockEntity(link.pos()) instanceof FireSupportCenterBlockEntity center)) {
			player.displayClientMessage(Component.translatable("message.tudursvehiclemod.fire_support.center_unreachable"), true);
			return null;
		}
		if (!center.tudursvehiclemod$isRemoteControlEnabled()) {
			player.displayClientMessage(Component.translatable("message.tudursvehiclemod.fire_support.remote_disabled"), true);
			return null;
		}
		return center;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, net.minecraft.world.item.component.TooltipDisplay displayComponent,
			java.util.function.Consumer<Component> textConsumer, net.minecraft.world.item.TooltipFlag type) {
		GlobalPos link = tudursvehiclemod$getLink(stack);
		if (link != null) {
			textConsumer.accept(Component.translatable("item.tudursvehiclemod.fire_support_designator.linked",
					link.pos().getX(), link.pos().getY(), link.pos().getZ()).withStyle(ChatFormatting.GRAY));
		} else {
			textConsumer.accept(Component.translatable("item.tudursvehiclemod.fire_support_designator.unlinked").withStyle(ChatFormatting.DARK_GRAY));
		}
		textConsumer.accept(Component.translatable("item.tudursvehiclemod.fire_support_designator.usage").withStyle(ChatFormatting.DARK_GRAY));
	}
}
