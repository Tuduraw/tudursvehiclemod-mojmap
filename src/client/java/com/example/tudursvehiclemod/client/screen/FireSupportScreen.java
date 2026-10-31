package com.example.tudursvehiclemod.client.screen;

import com.example.tudursvehiclemod.block.FireSupportCenterBlockEntity;
import com.example.tudursvehiclemod.network.FireSupportActionPayload;
import com.example.tudursvehiclemod.screen.FireSupportScreenHandler;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.player.Inventory;
import java.util.List;

/** Fire Support Center screen (block.FireSupportCenterBlockEntity): left, one row per unit - its stick slot, a weapon button (auto or one weapon; click to cycle) and the unit's name and status - with the route-book slot below; right, the target and the switches. Everything shown comes from the server's state (FireSupportScreenHandler.tudursvehiclemod$getState()); every button sends one action back. No background texture - the same flat panel and drawn slots as the Drone Center's screen. */
public class FireSupportScreen extends AbstractContainerScreen<FireSupportScreenHandler> {

	private static final int PANEL_COLOR = 0xFFC6C6C6;
	private static final int TEXT_COLOR = 0xFF404040;
	private static final int DIM_COLOR = 0xFF707070;
	private static final int OK_COLOR = 0xFF1E7F1E;
	private static final int WARN_COLOR = 0xFFB02020;
	private static final int RIGHT_X = 236;
	private static final int RIGHT_WIDTH = 116;

	private final Button[] weaponButtons = new Button[FireSupportCenterBlockEntity.UNIT_SLOTS];
	private Button modeButton;
	private EditBox relXField;
	private EditBox relYField;
	private EditBox relZField;
	private Button applyButton;
	private Button previousWaypointButton;
	private Button nextWaypointButton;
	private Button arcButton;
	private Button redstoneButton;
	private Button remoteButton;
	private Button activeButton;
	/** The relative coordinates last shown from the server - the fields are refilled only when these change, so typing isn't overwritten by the periodic state updates. */
	private int[] shownRelative;

	public FireSupportScreen(FireSupportScreenHandler handler, Inventory inventory, Component title) {
		super(handler, inventory, title);
		this.imageWidth = 360;
		this.imageHeight = 250;
		this.inventoryLabelX = FireSupportScreenHandler.INVENTORY_X;
		this.inventoryLabelY = FireSupportScreenHandler.INVENTORY_Y - 11;
	}

	@Override
	protected void init() {
		super.init();
		int left = this.leftPos;
		int top = this.topPos;
		for (int unit = 0; unit < FireSupportCenterBlockEntity.UNIT_SLOTS; unit++) {
			int slotUnit = unit;
			this.weaponButtons[unit] = this.addRenderableWidget(Button.builder(Component.empty(),
							button -> this.tudursvehiclemod$send(FireSupportCenterBlockEntity.ACTION_CYCLE_WEAPON, slotUnit, 0, 0))
					.bounds(left + 28, top + FireSupportScreenHandler.UNIT_SLOT_Y - 1 + unit * FireSupportScreenHandler.UNIT_SLOT_SPACING, 92, 18).build());
		}
		int rx = left + RIGHT_X;
		this.modeButton = this.addRenderableWidget(Button.builder(Component.empty(),
				button -> this.tudursvehiclemod$send(FireSupportCenterBlockEntity.ACTION_CYCLE_MODE, 0, 0, 0)).bounds(rx, top + 16, RIGHT_WIDTH, 18).build());
		this.relXField = this.tudursvehiclemod$coordinateField(rx, top + 38, "X");
		this.relYField = this.tudursvehiclemod$coordinateField(rx + 40, top + 38, "Y");
		this.relZField = this.tudursvehiclemod$coordinateField(rx + 80, top + 38, "Z");
		this.applyButton = this.addRenderableWidget(Button.builder(Component.translatable("gui.tudursvehiclemod.fire_support.apply"),
				button -> this.tudursvehiclemod$applyRelative()).bounds(rx, top + 58, RIGHT_WIDTH, 18).build());
		this.previousWaypointButton = this.addRenderableWidget(Button.builder(Component.literal("<"),
				button -> this.tudursvehiclemod$send(FireSupportCenterBlockEntity.ACTION_STEP_WAYPOINT, -1, 0, 0)).bounds(rx, top + 38, 20, 18).build());
		this.nextWaypointButton = this.addRenderableWidget(Button.builder(Component.literal(">"),
				button -> this.tudursvehiclemod$send(FireSupportCenterBlockEntity.ACTION_STEP_WAYPOINT, 1, 0, 0)).bounds(rx + RIGHT_WIDTH - 20, top + 38, 20, 18).build());
		this.arcButton = this.addRenderableWidget(Button.builder(Component.empty(),
				button -> this.tudursvehiclemod$send(FireSupportCenterBlockEntity.ACTION_TOGGLE_ARC, 0, 0, 0)).bounds(rx, top + 102, RIGHT_WIDTH, 18).build());
		this.redstoneButton = this.addRenderableWidget(Button.builder(Component.empty(),
				button -> this.tudursvehiclemod$send(FireSupportCenterBlockEntity.ACTION_TOGGLE_REDSTONE, 0, 0, 0)).bounds(rx, top + 122, RIGHT_WIDTH, 18).build());
		this.remoteButton = this.addRenderableWidget(Button.builder(Component.empty(),
				button -> this.tudursvehiclemod$send(FireSupportCenterBlockEntity.ACTION_TOGGLE_REMOTE, 0, 0, 0)).bounds(rx, top + 142, RIGHT_WIDTH, 18).build());
		this.activeButton = this.addRenderableWidget(Button.builder(Component.empty(),
				button -> this.tudursvehiclemod$send(FireSupportCenterBlockEntity.ACTION_TOGGLE_ACTIVE, 0, 0, 0)).bounds(rx, top + 164, RIGHT_WIDTH, 20).build());
		this.shownRelative = null;
		this.tudursvehiclemod$refreshWidgets();
	}

	private EditBox tudursvehiclemod$coordinateField(int x, int y, String axis) {
		EditBox field = new EditBox(this.font, x, y, 36, 16, Component.literal(axis));
		field.setMaxLength(6);
		field.setFilter(text -> text.isEmpty() || text.equals("-") || text.matches("-?\\d{1,5}"));
		field.setHint(Component.literal(axis).withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
		return this.addRenderableWidget(field);
	}

	private void tudursvehiclemod$applyRelative() {
		try {
			int x = Integer.parseInt(this.relXField.getValue().trim());
			int y = Integer.parseInt(this.relYField.getValue().trim());
			int z = Integer.parseInt(this.relZField.getValue().trim());
			this.tudursvehiclemod$send(FireSupportCenterBlockEntity.ACTION_SET_RELATIVE, x, y, z);
		} catch (NumberFormatException ignored) {
			// An incomplete field ("" or "-"): nothing to send yet.
		}
	}

	private void tudursvehiclemod$send(int action, int a, int b, int c) {
		BlockPos pos = this.menu.tudursvehiclemod$getBlockPos();
		if (pos != null) {
			ClientPlayNetworking.send(new FireSupportActionPayload(pos, action, a, b, c));
		}
	}

	@Override
	protected void containerTick() {
		super.containerTick();
		this.tudursvehiclemod$refreshWidgets();
	}

	/** Brings every button label and the visible target controls in line with the latest state. */
	private void tudursvehiclemod$refreshWidgets() {
		CompoundTag state = this.menu.tudursvehiclemod$getState();
		ListTag units = state.getListOrEmpty("Units");
		for (int unit = 0; unit < this.weaponButtons.length; unit++) {
			CompoundTag entry = unit < units.size() ? units.getCompoundOrEmpty(unit) : new CompoundTag();
			boolean linked = !entry.getStringOr("Name", "").isEmpty();
			String weapon = entry.getStringOr("Weapon", "");
			this.weaponButtons[unit].visible = linked;
			this.weaponButtons[unit].setMessage(weapon.isEmpty()
					? Component.translatable("gui.tudursvehiclemod.fire_support.weapon_auto")
					: Component.literal(this.tudursvehiclemod$trim(weapon, 84)));
		}
		String mode = state.getStringOr("Mode", FireSupportCenterBlockEntity.TargetMode.RELATIVE.name());
		this.modeButton.setMessage(Component.translatable("gui.tudursvehiclemod.fire_support.mode." + mode.toLowerCase(java.util.Locale.ROOT)));
		boolean relative = FireSupportCenterBlockEntity.TargetMode.RELATIVE.name().equals(mode);
		boolean waypoint = FireSupportCenterBlockEntity.TargetMode.WAYPOINT.name().equals(mode);
		this.relXField.visible = relative;
		this.relYField.visible = relative;
		this.relZField.visible = relative;
		this.applyButton.visible = relative;
		this.previousWaypointButton.visible = waypoint;
		this.nextWaypointButton.visible = waypoint;
		int[] serverRelative = {state.getIntOr("RelX", 0), state.getIntOr("RelY", 0), state.getIntOr("RelZ", 64)};
		if (state.contains("RelX") && (this.shownRelative == null || !java.util.Arrays.equals(this.shownRelative, serverRelative))) {
			this.shownRelative = serverRelative;
			this.relXField.setValue(Integer.toString(serverRelative[0]));
			this.relYField.setValue(Integer.toString(serverRelative[1]));
			this.relZField.setValue(Integer.toString(serverRelative[2]));
		}
		this.arcButton.setMessage(Component.translatable(state.getBooleanOr("HighArc", false)
				? "gui.tudursvehiclemod.fire_support.arc_high" : "gui.tudursvehiclemod.fire_support.arc_low"));
		this.redstoneButton.setMessage(Component.translatable(state.getBooleanOr("Redstone", false)
				? "gui.tudursvehiclemod.fire_support.redstone_on" : "gui.tudursvehiclemod.fire_support.redstone_off"));
		this.remoteButton.setMessage(Component.translatable(state.getBooleanOr("Remote", false)
				? "gui.tudursvehiclemod.fire_support.remote_on" : "gui.tudursvehiclemod.fire_support.remote_off"));
		this.activeButton.setMessage(Component.translatable(state.getBooleanOr("Active", false)
				? "gui.tudursvehiclemod.fire_support.active_on" : "gui.tudursvehiclemod.fire_support.active_off"));
	}

	@Override
	protected void renderBg(GuiGraphics context, float delta, int mouseX, int mouseY) {
		int left = this.leftPos;
		int top = this.topPos;
		context.fill(left, top, left + this.imageWidth, top + this.imageHeight, PANEL_COLOR);
		context.fill(left + RIGHT_X - 4, top + 4, left + RIGHT_X - 3, top + this.imageHeight - 4, 0xFF9A9A9A);
		for (int unit = 0; unit < FireSupportCenterBlockEntity.UNIT_SLOTS; unit++) {
			SlotGrid.drawSlot(context, left + FireSupportScreenHandler.UNIT_SLOT_X,
					top + FireSupportScreenHandler.UNIT_SLOT_Y + unit * FireSupportScreenHandler.UNIT_SLOT_SPACING);
		}
		SlotGrid.drawSlot(context, left + FireSupportScreenHandler.BOOK_SLOT_X, top + FireSupportScreenHandler.BOOK_SLOT_Y);
		SlotGrid.drawPlayerInventoryGrid(context, left, top + FireSupportScreenHandler.INVENTORY_Y, top + FireSupportScreenHandler.HOTBAR_Y);
	}

	@Override
	protected void renderLabels(GuiGraphics context, int mouseX, int mouseY) {
		super.renderLabels(context, mouseX, mouseY);
		CompoundTag state = this.menu.tudursvehiclemod$getState();
		ListTag units = state.getListOrEmpty("Units");
		for (int unit = 0; unit < FireSupportCenterBlockEntity.UNIT_SLOTS; unit++) {
			CompoundTag entry = unit < units.size() ? units.getCompoundOrEmpty(unit) : new CompoundTag();
			int rowY = FireSupportScreenHandler.UNIT_SLOT_Y + unit * FireSupportScreenHandler.UNIT_SLOT_SPACING;
			String name = entry.getStringOr("Name", "");
			if (name.isEmpty()) {
				context.drawString(this.font, Component.translatable("gui.tudursvehiclemod.fire_support.unit_empty"), 28, rowY + 4, DIM_COLOR, false);
				continue;
			}
			FireSupportCenterBlockEntity.UnitStatus status = this.tudursvehiclemod$status(entry.getStringOr("Status", ""));
			context.drawString(this.font, Component.literal(this.tudursvehiclemod$trim(name, 106)), 124, rowY - 1, TEXT_COLOR, false);
			int color = status.canFire() ? OK_COLOR : status.isWarning() ? WARN_COLOR : DIM_COLOR;
			context.drawString(this.font, Component.translatable(status.translationKey()), 124, rowY + 8, color, false);
		}
		context.drawString(this.font, Component.translatable("gui.tudursvehiclemod.fire_support.book"),
				FireSupportScreenHandler.BOOK_SLOT_X + 20, FireSupportScreenHandler.BOOK_SLOT_Y + 4, TEXT_COLOR, false);

		context.drawString(this.font, Component.translatable("gui.tudursvehiclemod.fire_support.target_header"), RIGHT_X, 6, TEXT_COLOR, false);
		String mode = state.getStringOr("Mode", "");
		if (FireSupportCenterBlockEntity.TargetMode.WAYPOINT.name().equals(mode)) {
			int count = state.getIntOr("WaypointCount", 0);
			Component label = count == 0 ? Component.translatable("gui.tudursvehiclemod.fire_support.no_waypoints")
					: Component.translatable("gui.tudursvehiclemod.fire_support.waypoint", state.getIntOr("WaypointIndex", 0) + 1, count);
			context.drawCenteredString(this.font, label, RIGHT_X + RIGHT_WIDTH / 2, 43, 0xFFFFFFFF);
		} else if (FireSupportCenterBlockEntity.TargetMode.DESIGNATOR.name().equals(mode)) {
			context.drawWordWrap(this.font, Component.translatable("gui.tudursvehiclemod.fire_support.designator_hint"), RIGHT_X, 40, RIGHT_WIDTH, DIM_COLOR, false);
		}
		int[] target = state.getIntArray("Target").orElse(null);
		if (target != null && target.length == 3) {
			context.drawString(this.font, Component.translatable("gui.tudursvehiclemod.fire_support.target", target[0], target[1], target[2]), RIGHT_X, 80, TEXT_COLOR, false);
			context.drawString(this.font, Component.translatable("gui.tudursvehiclemod.fire_support.distance", state.getIntOr("TargetDistance", 0)), RIGHT_X, 90, TEXT_COLOR, false);
		} else {
			context.drawString(this.font, Component.translatable("gui.tudursvehiclemod.fire_support.no_target"), RIGHT_X, 80, WARN_COLOR, false);
		}

		// The last notice, then the units it named.
		String messageKey = state.getStringOr("MessageKey", "");
		if (!messageKey.isEmpty()) {
			int y = 190;
			for (FormattedCharSequence line : this.font.split(Component.translatable(messageKey), RIGHT_WIDTH)) {
				if (y > this.imageHeight - 10) {
					break;
				}
				context.drawString(this.font, line, RIGHT_X, y, TEXT_COLOR, false);
				y += 10;
			}
			ListTag args = state.getListOrEmpty("MessageArgs");
			for (int i = 0; i < args.size() && y <= this.imageHeight - 10; i++) {
				Component detail = FireSupportCenterBlockEntity.tudursvehiclemod$problemText(args.getStringOr(i, ""));
				for (FormattedCharSequence line : this.font.split(detail, RIGHT_WIDTH)) {
					if (y > this.imageHeight - 10) {
						break;
					}
					context.drawString(this.font, line, RIGHT_X, y, WARN_COLOR, false);
					y += 10;
				}
			}
		}
	}

	@Override
	public void render(GuiGraphics context, int mouseX, int mouseY, float delta) {
		super.render(context, mouseX, mouseY, delta);
		this.renderTooltip(context, mouseX, mouseY);
	}

	private FireSupportCenterBlockEntity.UnitStatus tudursvehiclemod$status(String name) {
		try {
			return FireSupportCenterBlockEntity.UnitStatus.valueOf(name);
		} catch (IllegalArgumentException e) {
			return FireSupportCenterBlockEntity.UnitStatus.EMPTY;
		}
	}

	private String tudursvehiclemod$trim(String text, int width) {
		return this.font.width(text) <= width ? text : this.font.plainSubstrByWidth(text, width - this.font.width("...")) + "...";
	}

	/** Keeps typing in a coordinate field from triggering the inventory key (closing the screen on "E"). */
	@Override
	public boolean keyPressed(net.minecraft.client.input.KeyEvent input) {
		for (EditBox field : List.of(this.relXField, this.relYField, this.relZField)) {
			if (field.isFocused() && field.visible) {
				if (input.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
					return super.keyPressed(input);
				}
				if (input.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_ENTER || input.key() == org.lwjgl.glfw.GLFW.GLFW_KEY_KP_ENTER) {
					this.tudursvehiclemod$applyRelative();
					return true;
				}
				return field.keyPressed(input) || true;
			}
		}
		return super.keyPressed(input);
	}
}
