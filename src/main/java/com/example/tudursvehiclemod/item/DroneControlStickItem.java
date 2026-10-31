package com.example.tudursvehiclemod.item;

import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** A dedicated stick that registers a single UAV
 * vehicle when used to right-click it (see
 * entity.AbstractVehicleEntity's own interact() doc for where that
 * registration actually happens - AbstractVehicleEntity extends Entity,
 * not LivingEntity, so this item's own useOnEntity() is never actually
 * called for a vehicle at all, hence handling it there instead), then
 * bound to a block.StationBlockEntity by inserting it there (see that
 * class's own doc). Registering a NEW vehicle simply overwrites
 * whatever this same stick was previously registered to - it isn't
 * consumed by either registering a vehicle or being inserted into a
 * station. */
public class DroneControlStickItem extends Item {

	public DroneControlStickItem(Properties settings) {
		super(settings);
	}

	/** The vehicle UUID this specific stack is currently registered to, or null if never registered (or the component was somehow stripped). */
	public static UUID getRegisteredVehicleId(ItemStack stack) {
		return stack.get(ModComponents.REGISTERED_VEHICLE_ID);
	}

	public static void setRegisteredVehicleId(ItemStack stack, UUID vehicleId) {
		stack.set(ModComponents.REGISTERED_VEHICLE_ID, vehicleId);
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, net.minecraft.world.item.component.TooltipDisplay displayComponent,
							   java.util.function.Consumer<Component> textConsumer, net.minecraft.world.item.TooltipFlag type) {
		UUID registeredId = getRegisteredVehicleId(stack);
		if (registeredId != null) {
			textConsumer.accept(Component.translatable("item.tudursvehiclemod.drone_control_stick.registered", registeredId.toString().substring(0, 8))
					.withStyle(ChatFormatting.GRAY));
		} else {
			textConsumer.accept(Component.translatable("item.tudursvehiclemod.drone_control_stick.unregistered").withStyle(ChatFormatting.DARK_GRAY));
		}
	}
}
