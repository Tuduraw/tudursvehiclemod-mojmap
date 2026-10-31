package com.example.tudursvehiclemod.item;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

/** A dedicated recording tool for a Drone Center's own patrol route - carried and used freely anywhere in the world (right-click while held opens client.screen.DroneRouteBookScreen), recording the player's own ABSOLUTE position at each "page" rather than needing the Drone Center's own config screen open (which left barely any room to walk around and actually record different spots). Once the route is built, insert this same book into a Drone Center (a second slot alongside the existing binding stick) to apply it - see block.DroneCenterBlockEntity's own tudursvehiclemod$getWaypoints()/setWaypoints() doc for how the book's own absolute coordinates convert to that specific center's own relative ones. Not consumed by either recording waypoints or being inserted into a center. */
public class DroneRouteBookItem extends Item {

	public DroneRouteBookItem(Properties settings) {
		super(settings);
	}

	/** This stack's own recorded route, oldest-first - empty (not null) if nothing has been recorded yet. */
	public static List<com.example.tudursvehiclemod.block.DroneRouteBookWaypoint> tudursvehiclemod$getWaypoints(ItemStack stack) {
		String encoded = stack.get(ModComponents.DRONE_ROUTE_WAYPOINTS);
		List<com.example.tudursvehiclemod.block.DroneRouteBookWaypoint> result = new ArrayList<>();
		if (encoded != null && !encoded.isEmpty()) {
			for (String part : encoded.split(";")) {
				com.example.tudursvehiclemod.block.DroneRouteBookWaypoint decoded =
						com.example.tudursvehiclemod.block.DroneRouteBookWaypoint.tudursvehiclemod$decode(part);
				if (decoded != null) {
					result.add(decoded);
				}
			}
		}
		return result;
	}

	/** Replaces this stack's own entire recorded route at once (same "resend/replace everything, rather than track individual deltas" reasoning as block.DroneCenterBlockEntity's own tudursvehiclemod$setWaypoints() doc). no fixed waypoint count limit at all anymore. */
	public static void tudursvehiclemod$setWaypoints(ItemStack stack, List<com.example.tudursvehiclemod.block.DroneRouteBookWaypoint> waypoints) {
		StringBuilder sb = new StringBuilder();
		int count = waypoints.size();
		for (int i = 0; i < count; i++) {
			if (i > 0) {
				sb.append(';');
			}
			sb.append(waypoints.get(i).tudursvehiclemod$encode());
		}
		if (sb.length() == 0) {
			stack.remove(ModComponents.DRONE_ROUTE_WAYPOINTS);
		} else {
			stack.set(ModComponents.DRONE_ROUTE_WAYPOINTS, sb.toString());
		}
	}

	/** This stack's own coordinate offset (see ModComponents' own DRONE_ROUTE_OFFSET doc) - {0, 0, 0} (no shift) if never set. */
	public static net.minecraft.core.Vec3i tudursvehiclemod$getOffset(ItemStack stack) {
		String encoded = stack.get(ModComponents.DRONE_ROUTE_OFFSET);
		if (encoded == null || encoded.isEmpty()) {
			return net.minecraft.core.Vec3i.ZERO;
		}
		String[] parts = encoded.split(",", -1);
		if (parts.length != 3) {
			return net.minecraft.core.Vec3i.ZERO;
		}
		try {
			return new net.minecraft.core.Vec3i(
					Integer.parseInt(parts[0].strip()), Integer.parseInt(parts[1].strip()), Integer.parseInt(parts[2].strip()));
		} catch (NumberFormatException e) {
			return net.minecraft.core.Vec3i.ZERO;
		}
	}

	/** Sets this stack's own coordinate offset - see tudursvehiclemod$getOffset()'s own doc. Removes the component entirely (same "absent = 0,0,0" convention) when set back to exactly 0,0,0. */
	public static void tudursvehiclemod$setOffset(ItemStack stack, int x, int y, int z) {
		if (x == 0 && y == 0 && z == 0) {
			stack.remove(ModComponents.DRONE_ROUTE_OFFSET);
		} else {
			stack.set(ModComponents.DRONE_ROUTE_OFFSET, x + "," + y + "," + z);
		}
	}

	@Override
	public InteractionResult use(Level world, Player player, InteractionHand hand) {
		if (!world.isClientSide() && player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
			net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking.send(serverPlayer,
					new com.example.tudursvehiclemod.network.DroneRouteBookOpenPayload(hand == InteractionHand.MAIN_HAND));
		}
		return InteractionResult.SUCCESS;
	}

	@Override
	public void appendHoverText(ItemStack stack, TooltipContext context, net.minecraft.world.item.component.TooltipDisplay displayComponent,
							   java.util.function.Consumer<Component> textConsumer, TooltipFlag type) {
		int count = tudursvehiclemod$getWaypoints(stack).size();
		textConsumer.accept(Component.translatable("item.tudursvehiclemod.drone_route_book.waypoint_count", count).withStyle(ChatFormatting.GRAY));
	}
}
