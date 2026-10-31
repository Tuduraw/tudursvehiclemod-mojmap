package com.example.tudursvehiclemod.block;

import com.example.tudursvehiclemod.ChunkForceTracker;
import com.example.tudursvehiclemod.asset.WeaponAimRange;
import com.example.tudursvehiclemod.asset.WeaponDefinition;
import com.example.tudursvehiclemod.asset.WeaponType;
import com.example.tudursvehiclemod.entity.AbstractVehicleEntity;
import com.example.tudursvehiclemod.entity.FireSupportGunnerEntity;
import com.example.tudursvehiclemod.entity.projectile.Ballistics;
import com.example.tudursvehiclemod.item.DroneControlStickItem;
import com.example.tudursvehiclemod.item.DroneRouteBookItem;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;

/** Fire Support Center: lays the guns of up to UNIT_SLOTS linked vehicles on one target point and keeps them firing - unmanned, from wherever the vehicles stand.
 *
 * <p><b>Linking.</b> A vehicle is linked by putting a registered DroneControlStickItem into one of the unit slots - the same stick, registered the same way (right-click the vehicle with it), that a Station or Drone Center takes. Ground vehicles, ships and static emplacements; an aircraft can't hold a gun on a point from where it is, and is reported as having no usable weapon.
 *
 * <p><b>Target.</b> One of three ways (TargetMode): coordinates relative to this block, typed into the screen; one waypoint of a DroneRouteBookItem in the book slot, picked in the screen (the book's own offset applies); or a point a player marks from afar with a FireSupportDesignatorItem linked to this block. The point aimed at is the top centre of the target block.
 *
 * <p><b>Laying the guns.</b> For each weapon the firing elevation is solved with the projectile's real flight (Ballistics.solveElevation(): its speed, gravity and air drag, and its TimeFuse as the longest flight it gets), from the weapon's muzzle, flat or lobbed (highArc). The direction is turned into the vehicle's own frame (so a ship's roll and pitch are allowed for) and checked against the weapon's aim range: a target the weapon can't reach - too far, or outside the arc its turret can turn through - is out of range, and that unit doesn't fire. The weapon's seat gets an unseen gunner (FireSupportGunnerEntity) turned to face the solution: the turret follows it exactly as it would a player gunner (turret speed, limits, waiting until settled), and the gun is fired through AbstractVehicleEntity.tryFireWeapon() as an unmanned weapon - ammunition, magazine, reload and cooldown all as usual. A seat someone else is in (a player, another center's gunner) is left alone.
 *
 * <p><b>Weapons.</b> Per unit, one chosen weapon, or "auto": every usable weapon of the vehicle, one direction per seat (a seat's guns all follow the one gunner), each seat laid for the first of its weapons that can reach the target and also firing any other weapon in that seat that flies the same way (same speed and gravity) and can reach it from there. Usable: a turret-aimed weapon (it has an aim range) that fires shells, bullets or rockets - not bombs, torpedoes, depth charges, targeting pods or drop tanks. A CAS strike or Carrier launch can be chosen for a unit too (never by "auto"): it is sent at the target point - see tudursvehiclemod$isPointWeapon().
 *
 * <p><b>On and off.</b> From the screen, by redstone (when redstone control is on: powered means on), or from afar with the designator (when remote control is on). Turning on with no unit able to reach the target leaves it off, with a warning saying why for each unit; with only some able to, those fire and the others are listed. It stays on until turned off, or until nothing can fire any more - every reachable weapon out of ammunition, or no unit left that can reach the target (the target moved out of range, the vehicles were lost) - and goes off by itself then, saying so.
 *
 * <p><b>Remote control.</b> With remote control on, this block's chunk and each linked vehicle's chunk are kept loaded (ChunkForceTracker), so the designator works from anywhere - the block has to be loaded to hear it, and the vehicles to fire. Off, nothing is force-loaded, and the designator is refused. */
public class FireSupportCenterBlockEntity extends BlockEntity implements MenuProvider, Container {

	public static final int UNIT_SLOTS = 6;
	public static final int BOOK_SLOT = UNIT_SLOTS;
	private static final int SLOT_COUNT = UNIT_SLOTS + 1;

	/** How the target point is given - see this class's doc. */
	public enum TargetMode {
		RELATIVE, WAYPOINT, DESIGNATOR;

		public TargetMode next() {
			return values()[(this.ordinal() + 1) % values().length];
		}
	}

	/** Where a unit stands, shown per slot in the screen and named in warnings. ENGAGING and READY are the only states in which it counts as able to fire. */
	public enum UnitStatus {
		EMPTY, UNREGISTERED, NOT_FOUND, DESTROYED, NO_WEAPON, OUT_OF_RANGE, OUT_OF_ARC, SEAT_OCCUPIED, NO_AMMO, READY, ENGAGING, NO_TARGET;

		public boolean canFire() {
			return this == READY || this == ENGAGING;
		}

		/** A reason a linked unit isn't firing that is worth a warning (an empty slot isn't). */
		public boolean isWarning() {
			return this != EMPTY && this != READY && this != ENGAGING;
		}

		public String translationKey() {
			return "gui.tudursvehiclemod.fire_support.status." + this.name().toLowerCase(java.util.Locale.ROOT);
		}
	}

	/** Weapon types that fly as shells, bullets or rockets and so can be laid on a point - see this class's doc. */
	private static final java.util.Set<WeaponType> USABLE_TYPES = java.util.EnumSet.of(
			WeaponType.MACHINE_GUN, WeaponType.ROCKET, WeaponType.MK_ROCKET, WeaponType.DISPENSER, WeaponType.SMOKE, WeaponType.OTHER);

	/** Longest flight solved for, in ticks, for a weapon with no TimeFuse. */
	private static final int DEFAULT_MAX_FLIGHT_TICKS = 1200;
	/** How often (ticks) every unit's firing solution is worked out again while on - the vehicles can move and turn a little (a ship on the swell). Between those, the gunners just hold the last solution. */
	private static final int RESOLVE_INTERVAL_TICKS = 10;
	/** Weapons fly "the same way" (and can share one seat's direction) within this fraction of speed and gravity. */
	private static final double SAME_BALLISTICS_TOLERANCE = 0.01;
	/** How far (blocks) the players who get a warning about a redstone-triggered change may be. */
	private static final double REDSTONE_MESSAGE_RADIUS = 24.0;

	private final NonNullList<ItemStack> items = NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);

	private boolean active;
	private TargetMode targetMode = TargetMode.RELATIVE;
	private int relX;
	private int relY;
	private int relZ = 64;
	private int waypointIndex;
	private BlockPos designatedTarget;
	private boolean highArc;
	private boolean redstoneControl;
	private boolean remoteControl;
	/** Per unit: the weapon index chosen, or -1 for "auto" (see this class's doc). */
	private final int[] weaponChoice = new int[]{-1, -1, -1, -1, -1, -1};
	/** Per unit: the chunk its vehicle was last seen in - what's force-loaded for it while it isn't loaded (remote control only). */
	private final ChunkPos[] lastKnownChunk = new ChunkPos[UNIT_SLOTS];

	// ---- Not saved: worked out again as it runs. ----
	private final UnitStatus[] unitStatus = new UnitStatus[UNIT_SLOTS];
	/** Per unit, the seats being laid this round and how. */
	private final List<List<SeatPlan>> unitPlans = new ArrayList<>();
	/** Chunk force-loaded per unit (remote control). */
	private final ChunkPos[] forcedUnitChunk = new ChunkPos[UNIT_SLOTS];
	private boolean ownChunkForced;
	private int ticksUntilResolve;
	/** The target point as of the last evaluation - what CAS/Carrier weapons are sent at. */
	private Vec3 currentTarget;
	/** The last notice, shown at the foot of the screen: a translation key and its arguments. */
	private String lastMessageKey = "";
	private List<String> lastMessageArgs = List.of();

	/** One seat's gunner direction and the weapons it fires. */
	private record SeatPlan(int seat, float gunnerYaw, float gunnerPitch, int[] weaponIndices) {
	}

	/** A weapon's firing solution: where the gunner must look (yaw absolute, pitch as the vehicle's weapon tracking reads it), or the reason it has none. */
	private record Solution(UnitStatus failure, float gunnerYaw, float gunnerPitch) {
		static Solution failed(UnitStatus reason) {
			return new Solution(reason, 0f, 0f);
		}
	}

	public FireSupportCenterBlockEntity(BlockPos pos, BlockState state) {
		super(com.example.tudursvehiclemod.registry.ModBlockEntities.FIRE_SUPPORT_CENTER, pos, state);
		java.util.Arrays.fill(this.unitStatus, UnitStatus.EMPTY);
		for (int i = 0; i < UNIT_SLOTS; i++) {
			this.unitPlans.add(List.of());
		}
	}

	// ---------------------------------------------------------------- settings

	public boolean tudursvehiclemod$isActive() {
		return this.active;
	}

	public boolean tudursvehiclemod$isRemoteControlEnabled() {
		return this.remoteControl;
	}

	public TargetMode tudursvehiclemod$getTargetMode() {
		return this.targetMode;
	}

	/** Screen actions (network.FireSupportActionPayload) - see that payload's doc for the codes. actor is the player who sent it. */
	public void tudursvehiclemod$handleAction(ServerLevel world, ServerPlayer actor, int action, int a, int b, int c) {
		switch (action) {
			case ACTION_TOGGLE_ACTIVE -> this.tudursvehiclemod$setActive(world, !this.active, actor);
			case ACTION_CYCLE_MODE -> {
				this.targetMode = this.targetMode.next();
				this.tudursvehiclemod$onTargetChanged(world, actor);
			}
			case ACTION_SET_RELATIVE -> {
				int limit = com.example.tudursvehiclemod.VehicleModServerConfig.get().fireSupportMaxDistance;
				this.relX = Mth.clamp(a, -limit, limit);
				this.relY = Mth.clamp(b, -limit, limit);
				this.relZ = Mth.clamp(c, -limit, limit);
				this.tudursvehiclemod$onTargetChanged(world, actor);
			}
			case ACTION_STEP_WAYPOINT -> {
				int count = this.tudursvehiclemod$getBookWaypoints().size();
				this.waypointIndex = count == 0 ? 0 : Math.floorMod(this.waypointIndex + Integer.signum(a), count);
				this.tudursvehiclemod$onTargetChanged(world, actor);
			}
			case ACTION_TOGGLE_ARC -> {
				this.highArc = !this.highArc;
				this.tudursvehiclemod$onTargetChanged(world, actor);
			}
			case ACTION_TOGGLE_REDSTONE -> {
				this.redstoneControl = !this.redstoneControl;
				if (this.redstoneControl) {
					this.tudursvehiclemod$onRedstonePowerChanged(world, world.hasNeighborSignal(this.getBlockPos()));
				}
			}
			case ACTION_TOGGLE_REMOTE -> {
				this.remoteControl = !this.remoteControl;
				this.tudursvehiclemod$updateOwnChunk(world);
				if (!this.remoteControl) {
					this.tudursvehiclemod$releaseUnitChunks(world);
				}
			}
			case ACTION_CYCLE_WEAPON -> {
				if (a >= 0 && a < UNIT_SLOTS) {
					this.weaponChoice[a] = this.tudursvehiclemod$nextWeaponChoice(world, a);
					this.ticksUntilResolve = 0;
				}
			}
			default -> {
			}
		}
		this.setChanged();
	}

	public static final int ACTION_TOGGLE_ACTIVE = 0;
	public static final int ACTION_CYCLE_MODE = 1;
	public static final int ACTION_SET_RELATIVE = 2;
	public static final int ACTION_STEP_WAYPOINT = 3;
	public static final int ACTION_TOGGLE_ARC = 4;
	public static final int ACTION_TOGGLE_REDSTONE = 5;
	public static final int ACTION_TOGGLE_REMOTE = 6;
	public static final int ACTION_CYCLE_WEAPON = 7;

	/** A designator marked targetPos. Only taken in designator mode - see FireSupportDesignatorItem. */
	public void tudursvehiclemod$setDesignatedTarget(ServerLevel world, BlockPos targetPos, ServerPlayer actor) {
		this.designatedTarget = targetPos.immutable();
		this.setChanged();
		this.tudursvehiclemod$onTargetChanged(world, actor);
	}

	/** The target moved (or how it's aimed at changed): re-solve at once, and if on, check that something can still reach it. */
	private void tudursvehiclemod$onTargetChanged(ServerLevel world, ServerPlayer actor) {
		this.ticksUntilResolve = 0;
		if (this.active) {
			this.tudursvehiclemod$evaluateAll(world);
			this.tudursvehiclemod$stopIfNothingCanFire(world, actor);
		}
	}

	private int tudursvehiclemod$nextWeaponChoice(ServerLevel world, int unit) {
		AbstractVehicleEntity vehicle = this.tudursvehiclemod$findUnitVehicle(world, unit);
		if (vehicle == null) {
			return -1;
		}
		List<WeaponDefinition> weapons = vehicle.getDefinition().weapons();
		for (int index = this.weaponChoice[unit] + 1; index < weapons.size(); index++) {
			if (tudursvehiclemod$isUsableWeapon(vehicle, weapons.get(index)) || tudursvehiclemod$isPointWeapon(vehicle, weapons.get(index))) {
				return index;
			}
		}
		return -1;
	}

	// ---------------------------------------------------------------- on / off

	/** Turns support on or off. Turning on checks the target first - see this class's doc. actor (may be null) is told the outcome; with none (redstone), players near the block are. Returns whether it is on afterwards. */
	public boolean tudursvehiclemod$setActive(ServerLevel world, boolean on, ServerPlayer actor) {
		if (!on) {
			if (this.active) {
				this.active = false;
				this.tudursvehiclemod$releaseAllGunners(world);
				this.tudursvehiclemod$notify(world, actor, "message.tudursvehiclemod.fire_support.stopped", List.of());
				this.setChanged();
			}
			return false;
		}
		if (this.active) {
			return true;
		}
		if (this.tudursvehiclemod$resolveTarget() == null) {
			this.tudursvehiclemod$notify(world, actor, "message.tudursvehiclemod.fire_support.no_target", List.of());
			return false;
		}
		this.tudursvehiclemod$evaluateAll(world);
		List<String> problems = this.tudursvehiclemod$describeProblems(world);
		if (!this.tudursvehiclemod$anyUnitCanFire()) {
			this.tudursvehiclemod$notify(world, actor, problems.isEmpty()
					? "message.tudursvehiclemod.fire_support.no_units"
					: "message.tudursvehiclemod.fire_support.none_in_range", problems);
			return false;
		}
		this.active = true;
		this.ticksUntilResolve = RESOLVE_INTERVAL_TICKS;
		this.setChanged();
		this.tudursvehiclemod$notify(world, actor, problems.isEmpty()
				? "message.tudursvehiclemod.fire_support.started"
				: "message.tudursvehiclemod.fire_support.started_partial", problems);
		return true;
	}

	/** Redstone control: powered means on. A no-op unless redstone control is enabled. */
	public void tudursvehiclemod$onRedstonePowerChanged(ServerLevel world, boolean powered) {
		if (this.redstoneControl && powered != this.active) {
			this.tudursvehiclemod$setActive(world, powered, null);
		}
	}

	private void tudursvehiclemod$stopIfNothingCanFire(ServerLevel world, ServerPlayer actor) {
		if (!this.active || this.tudursvehiclemod$anyUnitCanFire()) {
			return;
		}
		boolean outOfAmmo = false;
		boolean anyOtherReason = false;
		for (UnitStatus status : this.unitStatus) {
			if (status == UnitStatus.NO_AMMO) {
				outOfAmmo = true;
			} else if (status.isWarning()) {
				anyOtherReason = true;
			}
		}
		this.active = false;
		this.tudursvehiclemod$releaseAllGunners(world);
		this.setChanged();
		this.tudursvehiclemod$notify(world, actor, outOfAmmo && !anyOtherReason
				? "message.tudursvehiclemod.fire_support.out_of_ammo"
				: "message.tudursvehiclemod.fire_support.none_in_range", this.tudursvehiclemod$describeProblems(world));
	}

	private boolean tudursvehiclemod$anyUnitCanFire() {
		for (UnitStatus status : this.unitStatus) {
			if (status.canFire()) {
				return true;
			}
		}
		return false;
	}

	/** Every unit with something to warn about, as "name" TAB "status translation key" - translated where it's shown (a dedicated server has no translations of its own). */
	private List<String> tudursvehiclemod$describeProblems(ServerLevel world) {
		List<String> problems = new ArrayList<>();
		for (int i = 0; i < UNIT_SLOTS; i++) {
			if (this.unitStatus[i].isWarning()) {
				problems.add(this.tudursvehiclemod$unitName(world, i) + "\t" + this.unitStatus[i].translationKey());
			}
		}
		return problems;
	}

	/** One entry of tudursvehiclemod$describeProblems() as text: "name: reason". */
	public static Component tudursvehiclemod$problemText(String entry) {
		int tab = entry.indexOf('\t');
		if (tab < 0) {
			return Component.literal(entry);
		}
		return Component.literal(entry.substring(0, tab) + ": ").append(Component.translatable(entry.substring(tab + 1)));
	}

	/** Shows a notice to actor (actionbar, and the list in chat when there is one), or with no actor to the players near this block; and keeps it for the screen. */
	private void tudursvehiclemod$notify(ServerLevel world, ServerPlayer actor, String key, List<String> details) {
		this.lastMessageKey = key;
		this.lastMessageArgs = List.copyOf(details);
		Component headline = Component.translatable(key);
		List<ServerPlayer> recipients = new ArrayList<>();
		if (actor != null) {
			recipients.add(actor);
		} else {
			Vec3 center = Vec3.atCenterOf(this.getBlockPos());
			for (ServerPlayer player : world.players()) {
				if (player.distanceToSqr(center) <= REDSTONE_MESSAGE_RADIUS * REDSTONE_MESSAGE_RADIUS) {
					recipients.add(player);
				}
			}
		}
		for (ServerPlayer player : recipients) {
			player.displayClientMessage(headline, true);
			for (String detail : details) {
				player.displayClientMessage(Component.literal(" - ").append(tudursvehiclemod$problemText(detail)).withStyle(net.minecraft.ChatFormatting.YELLOW), false);
			}
		}
	}

	// ---------------------------------------------------------------- target

	/** The point aimed at, or null if the current mode has none (no book or waypoint, nothing designated yet). */
	public Vec3 tudursvehiclemod$resolveTarget() {
		BlockPos block = this.tudursvehiclemod$resolveTargetBlock();
		return block == null ? null : new Vec3(block.getX() + 0.5, block.getY() + 1.0, block.getZ() + 0.5);
	}

	private BlockPos tudursvehiclemod$resolveTargetBlock() {
		return switch (this.targetMode) {
			case RELATIVE -> this.getBlockPos().offset(this.relX, this.relY, this.relZ);
			case WAYPOINT -> {
				List<DroneRouteBookWaypoint> waypoints = this.tudursvehiclemod$getBookWaypoints();
				if (waypoints.isEmpty()) {
					yield null;
				}
				DroneRouteBookWaypoint waypoint = waypoints.get(Mth.clamp(this.waypointIndex, 0, waypoints.size() - 1));
				net.minecraft.core.Vec3i offset = DroneRouteBookItem.tudursvehiclemod$getOffset(this.items.get(BOOK_SLOT));
				yield new BlockPos(waypoint.x() + offset.getX(), waypoint.y() + offset.getY(), waypoint.z() + offset.getZ());
			}
			case DESIGNATOR -> this.designatedTarget;
		};
	}

	private List<DroneRouteBookWaypoint> tudursvehiclemod$getBookWaypoints() {
		ItemStack book = this.items.get(BOOK_SLOT);
		return book.getItem() instanceof DroneRouteBookItem ? DroneRouteBookItem.tudursvehiclemod$getWaypoints(book) : List.of();
	}

	// ---------------------------------------------------------------- tick

	public static void tick(Level world, BlockPos pos, BlockState state, FireSupportCenterBlockEntity blockEntity) {
		if (!(world instanceof ServerLevel serverWorld)) {
			return;
		}
		blockEntity.tudursvehiclemod$serverTick(serverWorld);
	}

	private void tudursvehiclemod$serverTick(ServerLevel world) {
		if (this.remoteControl) {
			this.tudursvehiclemod$updateOwnChunk(world);
			this.tudursvehiclemod$updateUnitChunks(world);
		}
		if (!this.active) {
			// Statuses for the screen, now and then; and gunners left from before (a reload) are let go - they'd leave on their own anyway.
			if (world.getGameTime() % 20 == 0) {
				this.tudursvehiclemod$evaluateAll(world);
				this.tudursvehiclemod$releaseAllGunners(world);
			}
			return;
		}
		if (--this.ticksUntilResolve <= 0) {
			this.ticksUntilResolve = RESOLVE_INTERVAL_TICKS;
			this.tudursvehiclemod$evaluateAll(world);
			this.tudursvehiclemod$stopIfNothingCanFire(world, null);
			if (!this.active) {
				return;
			}
		}
		for (int unit = 0; unit < UNIT_SLOTS; unit++) {
			this.tudursvehiclemod$engage(world, unit);
		}
	}

	/** Works out every unit's status and firing plan against the current target. */
	private void tudursvehiclemod$evaluateAll(ServerLevel world) {
		Vec3 target = this.tudursvehiclemod$resolveTarget();
		this.currentTarget = target;
		for (int unit = 0; unit < UNIT_SLOTS; unit++) {
			List<SeatPlan> plans = new ArrayList<>();
			this.unitStatus[unit] = this.tudursvehiclemod$evaluateUnit(world, unit, target, plans);
			this.unitPlans.set(unit, plans);
		}
	}

	private UnitStatus tudursvehiclemod$evaluateUnit(ServerLevel world, int unit, Vec3 target, List<SeatPlan> plansOut) {
		ItemStack stick = this.items.get(unit);
		if (!(stick.getItem() instanceof DroneControlStickItem)) {
			return UnitStatus.EMPTY;
		}
		if (DroneControlStickItem.getRegisteredVehicleId(stick) == null) {
			return UnitStatus.UNREGISTERED;
		}
		AbstractVehicleEntity vehicle = this.tudursvehiclemod$findUnitVehicle(world, unit);
		if (vehicle == null) {
			return UnitStatus.NOT_FOUND;
		}
		if (vehicle.tudursvehiclemod$isDestroyed()) {
			return UnitStatus.DESTROYED;
		}
		List<WeaponDefinition> weapons = vehicle.getDefinition().weapons();
		int choice = this.weaponChoice[unit];
		// A CAS strike or Carrier launch, chosen for this unit: sent at the target point itself, no laying needed - see tudursvehiclemod$isPointWeapon().
		if (choice >= 0 && choice < weapons.size() && tudursvehiclemod$isPointWeapon(vehicle, weapons.get(choice))) {
			if (target == null) {
				return UnitStatus.NO_TARGET;
			}
			if (!vehicle.tudursvehiclemod$hasShotsLeft(choice)) {
				return UnitStatus.NO_AMMO;
			}
			plansOut.add(new SeatPlan(POINT_WEAPON_SEAT, 0f, 0f, new int[]{choice}));
			return this.active ? UnitStatus.ENGAGING : UnitStatus.READY;
		}
		// Usable weapons by seat, in definition order.
		Map<Integer, List<Integer>> bySeat = new LinkedHashMap<>();
		for (int index = 0; index < weapons.size(); index++) {
			if ((choice < 0 || choice == index) && tudursvehiclemod$isUsableWeapon(vehicle, weapons.get(index))) {
				bySeat.computeIfAbsent(weapons.get(index).seatIndex(), seat -> new ArrayList<>()).add(index);
			}
		}
		if (bySeat.isEmpty()) {
			return UnitStatus.NO_WEAPON;
		}
		if (target == null) {
			return UnitStatus.NO_TARGET;
		}
		// The most telling reason a seat can't fire, if none can: occupied < no ammo < out of arc < out of range.
		UnitStatus worst = UnitStatus.OUT_OF_RANGE;
		for (Map.Entry<Integer, List<Integer>> entry : bySeat.entrySet()) {
			int seat = entry.getKey();
			Entity occupant = vehicle.tudursvehiclemod$getSeatOccupant(seat);
			if (occupant != null && !(occupant instanceof FireSupportGunnerEntity gunner && gunner.tudursvehiclemod$isOwnedBy(this.getBlockPos()))) {
				worst = tudursvehiclemod$moreTelling(worst, UnitStatus.SEAT_OCCUPIED);
				continue;
			}
			SeatPlan plan = null;
			for (int index : entry.getValue()) {
				if (!vehicle.tudursvehiclemod$hasShotsLeft(index)) {
					worst = tudursvehiclemod$moreTelling(worst, UnitStatus.NO_AMMO);
					continue;
				}
				WeaponDefinition weapon = weapons.get(index);
				if (plan == null) {
					Solution solution = this.tudursvehiclemod$solve(vehicle, weapon, target);
					if (solution.failure() != null) {
						worst = tudursvehiclemod$moreTelling(worst, solution.failure());
						continue;
					}
					plan = new SeatPlan(seat, solution.gunnerYaw(), solution.gunnerPitch(), new int[]{index});
				} else if (tudursvehiclemod$flySameWay(weapons.get(plan.weaponIndices()[0]), weapon)
						&& tudursvehiclemod$canPointAt(vehicle, weapon, plan.gunnerYaw(), plan.gunnerPitch())) {
					int[] more = java.util.Arrays.copyOf(plan.weaponIndices(), plan.weaponIndices().length + 1);
					more[more.length - 1] = index;
					plan = new SeatPlan(seat, plan.gunnerYaw(), plan.gunnerPitch(), more);
				}
			}
			if (plan != null) {
				plansOut.add(plan);
			}
		}
		if (plansOut.isEmpty()) {
			return worst;
		}
		return this.active ? UnitStatus.ENGAGING : UnitStatus.READY;
	}

	private static UnitStatus tudursvehiclemod$moreTelling(UnitStatus current, UnitStatus candidate) {
		java.util.List<UnitStatus> order = java.util.List.of(UnitStatus.OUT_OF_RANGE, UnitStatus.OUT_OF_ARC, UnitStatus.NO_AMMO, UnitStatus.SEAT_OCCUPIED);
		return order.indexOf(candidate) > order.indexOf(current) ? candidate : current;
	}

	/** SeatPlan.seat() of a CAS/Carrier plan: no gunner, the weapon is sent at the target point (AbstractVehicleEntity.tudursvehiclemod$tryFireWeaponAt()). */
	private static final int POINT_WEAPON_SEAT = -1;

	/** A CAS strike or a Carrier launch: given the target point as its own target point (the centre of its route), in place of the one a player marks by aiming. Never chosen by "auto" - a unit fires one only when it is chosen for that unit, since it sends aircraft rather than shells. No range limit: the aircraft fly there. */
	public static boolean tudursvehiclemod$isPointWeapon(AbstractVehicleEntity vehicle, WeaponDefinition weapon) {
		if (vehicle instanceof com.example.tudursvehiclemod.entity.AircraftEntity
				|| vehicle instanceof com.example.tudursvehiclemod.entity.HelicopterEntity) {
			return false;
		}
		return weapon.weaponType() == WeaponType.CAS || weapon.weaponType() == WeaponType.CARRIER;
	}

	/** Usable for fire support - see this class's doc. Aircraft can't hold a gun on a point. */
	public static boolean tudursvehiclemod$isUsableWeapon(AbstractVehicleEntity vehicle, WeaponDefinition weapon) {
		if (vehicle instanceof com.example.tudursvehiclemod.entity.AircraftEntity
				|| vehicle instanceof com.example.tudursvehiclemod.entity.HelicopterEntity) {
			return false;
		}
		return weapon.aimRange().isPresent() && USABLE_TYPES.contains(weapon.weaponType()) && weapon.velocity() > 0f;
	}

	private static boolean tudursvehiclemod$flySameWay(WeaponDefinition a, WeaponDefinition b) {
		return tudursvehiclemod$close(a.velocity(), b.velocity()) && tudursvehiclemod$close(a.gravity(), b.gravity());
	}

	private static boolean tudursvehiclemod$close(double a, double b) {
		return Math.abs(a - b) <= Math.max(Math.abs(a), Math.abs(b)) * SAME_BALLISTICS_TOLERANCE + 1.0e-6;
	}

	/** Whether weapon's turret can point where a gunner facing (gunnerYaw, gunnerPitch) looks. */
	private static boolean tudursvehiclemod$canPointAt(AbstractVehicleEntity vehicle, WeaponDefinition weapon, float gunnerYaw, float gunnerPitch) {
		WeaponAimRange range = weapon.aimRange().orElse(null);
		if (range == null) {
			return false;
		}
		float relativeYaw = Mth.wrapDegrees(gunnerYaw - vehicle.getYRot());
		return vehicle.tudursvehiclemod$isAimWithinRange(relativeYaw, gunnerPitch, range);
	}

	/** The firing solution of weapon on vehicle for target - see this class's doc. */
	private Solution tudursvehiclemod$solve(AbstractVehicleEntity vehicle, WeaponDefinition weapon, Vec3 target) {
		Vec3 muzzle = tudursvehiclemod$aimOrigin(vehicle, weapon);
		Vec3 toTarget = target.subtract(muzzle);
		double horizontal = Math.sqrt(toTarget.x * toTarget.x + toTarget.z * toTarget.z);
		int maxTicks = weapon.timeFuseTicks() >= 0 ? Math.max(1, weapon.timeFuseTicks()) : DEFAULT_MAX_FLIGHT_TICKS;
		double worldYaw = Math.toDegrees(Math.atan2(-toTarget.x, toTarget.z));
		Solution outOfArc = null;
		// The preferred arc first, then the other: a turret that can't elevate for a lob may still reach flat, and the other way round.
		for (boolean lob : new boolean[]{this.highArc, !this.highArc}) {
			Ballistics.Elevation elevation = Ballistics.solveElevation(horizontal, toTarget.y, weapon.velocity(), weapon.gravity(), lob, maxTicks);
			if (elevation == null) {
				return outOfArc != null ? outOfArc : Solution.failed(UnitStatus.OUT_OF_RANGE);
			}
			// World direction -> the vehicle's own frame (yaw relative to its heading, pitch as its weapon tracking reads it), allowing for its pitch and roll.
			Vec3 worldDirection = Vec3.directionFromRotation((float) -elevation.degreesUp(), (float) worldYaw);
			org.joml.Vector3f local = new org.joml.Vector3f((float) worldDirection.x, (float) worldDirection.y, (float) worldDirection.z);
			vehicle.tudursvehiclemod$getBodyOrientation().conjugate(new org.joml.Quaternionf()).transform(local);
			float relativeYaw = (float) Math.toDegrees(Math.atan2(-local.x, local.z));
			float relativePitch = (float) -Math.toDegrees(Math.asin(Mth.clamp(local.y, -1.0f, 1.0f)));
			if (vehicle.tudursvehiclemod$isAimWithinRange(relativeYaw, relativePitch, weapon.aimRange().get())) {
				return new Solution(null, Mth.wrapDegrees(vehicle.getYRot() + relativeYaw), relativePitch);
			}
			outOfArc = Solution.failed(UnitStatus.OUT_OF_ARC);
		}
		return outOfArc;
	}

	/** Where the weapon's line of fire starts, for solving: its turret's traverse pivot (in plan) at the height of its elevation pivot. The muzzle itself moves as the turret turns - the weapon's offsets are given at rest, and a muzzle 20 blocks out from a battleship's turret centre swings several blocks sideways with it, putting the solution a degree or more off - while the line of fire always runs through the traverse pivot in plan, whichever way the turret faces. A weapon with no turret part falls back to its muzzle at rest. */
	private static Vec3 tudursvehiclemod$aimOrigin(AbstractVehicleEntity vehicle, WeaponDefinition weapon) {
		Double yawPivotX = null;
		Double yawPivotZ = null;
		Double pitchPivotY = null;
		for (com.example.tudursvehiclemod.asset.WeaponPart part : vehicle.getDefinition().weaponParts()) {
			if (part.weaponName().isEmpty() || !part.weaponName().get().equalsIgnoreCase(weapon.weaponName())) {
				continue;
			}
			java.util.Optional<com.example.tudursvehiclemod.asset.WeaponPart.ChildInfo> parent = part.childInfo();
			if (yawPivotX == null) {
				if (part.yawFollow()) {
					yawPivotX = part.pivotX();
					yawPivotZ = part.pivotZ();
				} else if (parent.isPresent() && parent.get().parentYawFollow()) {
					yawPivotX = parent.get().parentPivotX();
					yawPivotZ = parent.get().parentPivotZ();
				}
			}
			if (pitchPivotY == null) {
				if (part.pitchFollow()) {
					pitchPivotY = part.pivotY();
				} else if (parent.isPresent() && parent.get().parentPitchFollow()) {
					pitchPivotY = parent.get().parentPivotY();
				}
			}
		}
		if (yawPivotX == null || weapon.offsets().isEmpty()) {
			return vehicle.tudursvehiclemod$getWeaponMuzzleWorldPos(weapon);
		}
		double height = pitchPivotY != null ? pitchPivotY : weapon.offsets().get(0).y();
		float scale = vehicle.getDefinition().scale();
		org.joml.Vector3f local = new org.joml.Vector3f((float) (yawPivotX * scale), (float) (height * scale), (float) (yawPivotZ * scale));
		vehicle.tudursvehiclemod$getBodyOrientation().transform(local);
		return new Vec3(vehicle.getX() + local.x, vehicle.getY() + vehicle.getRenderYOffsetCurrent() + local.y, vehicle.getZ() + local.z);
	}

	/** Seats this unit's gunners, turns them to their solutions and fires every weapon of the plan that is ready. */
	private void tudursvehiclemod$engage(ServerLevel world, int unit) {
		List<SeatPlan> plans = this.unitPlans.get(unit);
		AbstractVehicleEntity vehicle = plans.isEmpty() ? null : this.tudursvehiclemod$findUnitVehicle(world, unit);
		if (vehicle == null) {
			return;
		}
		java.util.Set<Integer> seatsInUse = new java.util.HashSet<>();
		for (SeatPlan plan : plans) {
			if (plan.seat() == POINT_WEAPON_SEAT) {
				if (this.currentTarget != null) {
					for (int index : plan.weaponIndices()) {
						vehicle.tudursvehiclemod$tryFireWeaponAt(index, this.currentTarget);
					}
				}
				continue;
			}
			FireSupportGunnerEntity gunner = this.tudursvehiclemod$gunnerFor(world, vehicle, plan.seat());
			if (gunner == null) {
				continue;
			}
			seatsInUse.add(plan.seat());
			gunner.tudursvehiclemod$refresh();
			gunner.tudursvehiclemod$lookAt(plan.gunnerYaw(), plan.gunnerPitch());
			for (int index : plan.weaponIndices()) {
				// Ready or not is tryFireWeapon()'s business: cooldown, reload, an unsettled turret all just make it decline this tick.
				vehicle.tryFireWeapon(index, null);
			}
		}
		this.tudursvehiclemod$releaseGunners(vehicle, seatsInUse);
	}

	/** This center's gunner in seat, seating a new one if the seat is free; null if someone else has it. */
	private FireSupportGunnerEntity tudursvehiclemod$gunnerFor(ServerLevel world, AbstractVehicleEntity vehicle, int seat) {
		Entity occupant = vehicle.tudursvehiclemod$getSeatOccupant(seat);
		if (occupant instanceof FireSupportGunnerEntity gunner && gunner.tudursvehiclemod$isOwnedBy(this.getBlockPos())) {
			return gunner;
		}
		if (occupant != null) {
			return null;
		}
		FireSupportGunnerEntity gunner = new FireSupportGunnerEntity(com.example.tudursvehiclemod.registry.ModEntityTypes.FIRE_SUPPORT_GUNNER, world);
		gunner.snapTo(vehicle.getX(), vehicle.getY(), vehicle.getZ(), vehicle.getYRot(), 0f);
		gunner.tudursvehiclemod$setOwner(this.getBlockPos());
		if (!world.addFreshEntity(gunner)) {
			return null;
		}
		if (!vehicle.tudursvehiclemod$mountToSeat(gunner, seat)) {
			gunner.discard();
			return null;
		}
		return gunner;
	}

	/** Removes this center's gunners from vehicle, except those in keepSeats. */
	private void tudursvehiclemod$releaseGunners(AbstractVehicleEntity vehicle, java.util.Set<Integer> keepSeats) {
		for (Entity passenger : List.copyOf(vehicle.getPassengers())) {
			if (passenger instanceof FireSupportGunnerEntity gunner && gunner.tudursvehiclemod$isOwnedBy(this.getBlockPos())
					&& !keepSeats.contains(vehicle.tudursvehiclemod$getAssignedSeatIndex(gunner))) {
				gunner.stopRiding();
				gunner.discard();
			}
		}
	}

	private void tudursvehiclemod$releaseAllGunners(ServerLevel world) {
		for (int unit = 0; unit < UNIT_SLOTS; unit++) {
			AbstractVehicleEntity vehicle = this.tudursvehiclemod$findUnitVehicle(world, unit);
			if (vehicle != null) {
				this.tudursvehiclemod$releaseGunners(vehicle, java.util.Set.of());
			}
			this.unitPlans.set(unit, List.of());
		}
	}

	// ---------------------------------------------------------------- vehicles and chunks

	private AbstractVehicleEntity tudursvehiclemod$findUnitVehicle(ServerLevel world, int unit) {
		ItemStack stick = this.items.get(unit);
		if (!(stick.getItem() instanceof DroneControlStickItem)) {
			return null;
		}
		UUID id = DroneControlStickItem.getRegisteredVehicleId(stick);
		AbstractVehicleEntity vehicle = DroneCenterBlockEntity.tudursvehiclemod$findBoundVehicle(world, id);
		if (vehicle != null) {
			ChunkPos chunk = new ChunkPos(vehicle.blockPosition());
			if (!chunk.equals(this.lastKnownChunk[unit])) {
				this.lastKnownChunk[unit] = chunk;
				this.setChanged();
			}
		}
		return vehicle;
	}

	/** The name shown for a unit: the stick's own name if it was given one, else the vehicle's. */
	public String tudursvehiclemod$unitName(ServerLevel world, int unit) {
		ItemStack stick = this.items.get(unit);
		if (stick.isEmpty()) {
			return "-";
		}
		if (stick.has(net.minecraft.core.component.DataComponents.CUSTOM_NAME)) {
			return stick.getHoverName().getString();
		}
		AbstractVehicleEntity vehicle = world == null ? null : this.tudursvehiclemod$findUnitVehicle(world, unit);
		if (vehicle != null) {
			return vehicle.getDefinition().displayName(vehicle.getVehicleDefinitionId());
		}
		return stick.getHoverName().getString();
	}

	private Object tudursvehiclemod$unitRequester(int unit) {
		return new ChunkForceTracker.Purpose(this, "unit_" + unit);
	}

	private Object tudursvehiclemod$ownRequester() {
		return new ChunkForceTracker.Purpose(this, "self");
	}

	/** This block's own chunk stays loaded while remote control is on (see this class's doc). Also re-requested after a reload (VehicleMod's BLOCK_ENTITY_LOAD listener), since ChunkForceTracker's bookkeeping starts empty. */
	public void tudursvehiclemod$updateOwnChunk(ServerLevel world) {
		boolean want = this.remoteControl && !this.isRemoved();
		if (want == this.ownChunkForced) {
			return;
		}
		ChunkPos own = new ChunkPos(this.getBlockPos());
		if (want) {
			ChunkForceTracker.requestWithoutLoading(world, own, this.tudursvehiclemod$ownRequester());
		} else {
			ChunkForceTracker.release(world, own, this.tudursvehiclemod$ownRequester());
		}
		this.ownChunkForced = want;
	}

	/** Keeps each linked vehicle's chunk loaded (remote control): where it is, or where it was last seen while it isn't loaded. */
	private void tudursvehiclemod$updateUnitChunks(ServerLevel world) {
		for (int unit = 0; unit < UNIT_SLOTS; unit++) {
			ItemStack stick = this.items.get(unit);
			ChunkPos want = null;
			if (stick.getItem() instanceof DroneControlStickItem && DroneControlStickItem.getRegisteredVehicleId(stick) != null) {
				this.tudursvehiclemod$findUnitVehicle(world, unit);
				want = this.lastKnownChunk[unit];
			}
			ChunkPos have = this.forcedUnitChunk[unit];
			if (java.util.Objects.equals(want, have)) {
				continue;
			}
			if (have != null) {
				ChunkForceTracker.release(world, have, this.tudursvehiclemod$unitRequester(unit));
			}
			if (want != null) {
				ChunkForceTracker.request(world, want, this.tudursvehiclemod$unitRequester(unit));
			}
			this.forcedUnitChunk[unit] = want;
		}
	}

	private void tudursvehiclemod$releaseUnitChunks(ServerLevel world) {
		for (int unit = 0; unit < UNIT_SLOTS; unit++) {
			if (this.forcedUnitChunk[unit] != null) {
				ChunkForceTracker.release(world, this.forcedUnitChunk[unit], this.tudursvehiclemod$unitRequester(unit));
				this.forcedUnitChunk[unit] = null;
			}
		}
	}

	/** Breaking the block: lets the vehicles go and releases every chunk (see DroneCenterBlockEntity.onBlockReplaced() for why this, not the block's onStateReplaced()). super scatters the slots. */
	@Override
	public void preRemoveSideEffects(BlockPos pos, BlockState oldState) {
		if (this.getLevel() instanceof ServerLevel serverWorld) {
			this.active = false;
			this.tudursvehiclemod$releaseAllGunners(serverWorld);
			ChunkForceTracker.releaseAllOwnedBy(serverWorld, this);
			this.ownChunkForced = false;
			java.util.Arrays.fill(this.forcedUnitChunk, null);
		}
		super.preRemoveSideEffects(pos, oldState);
	}

	// ---------------------------------------------------------------- screen state

	/** Everything the screen shows (network.FireSupportStatePayload). */
	public CompoundTag tudursvehiclemod$encodeState(ServerLevel world) {
		CompoundTag nbt = new CompoundTag();
		nbt.putBoolean("Active", this.active);
		nbt.putString("Mode", this.targetMode.name());
		nbt.putInt("RelX", this.relX);
		nbt.putInt("RelY", this.relY);
		nbt.putInt("RelZ", this.relZ);
		List<DroneRouteBookWaypoint> waypoints = this.tudursvehiclemod$getBookWaypoints();
		nbt.putInt("WaypointIndex", waypoints.isEmpty() ? 0 : Mth.clamp(this.waypointIndex, 0, waypoints.size() - 1));
		nbt.putInt("WaypointCount", waypoints.size());
		nbt.putBoolean("HighArc", this.highArc);
		nbt.putBoolean("Redstone", this.redstoneControl);
		nbt.putBoolean("Remote", this.remoteControl);
		BlockPos target = this.tudursvehiclemod$resolveTargetBlock();
		if (target != null) {
			nbt.putIntArray("Target", new int[]{target.getX(), target.getY(), target.getZ()});
			nbt.putInt("TargetDistance", (int) Math.round(Math.sqrt(target.distSqr(this.getBlockPos()))));
		}
		ListTag units = new ListTag();
		for (int unit = 0; unit < UNIT_SLOTS; unit++) {
			CompoundTag entry = new CompoundTag();
			entry.putString("Status", this.unitStatus[unit].name());
			entry.putString("Name", this.items.get(unit).isEmpty() ? "" : this.tudursvehiclemod$unitName(world, unit));
			entry.putString("Weapon", this.tudursvehiclemod$weaponLabel(world, unit));
			units.add(entry);
		}
		nbt.put("Units", units);
		nbt.putString("MessageKey", this.lastMessageKey);
		ListTag args = new ListTag();
		for (String arg : this.lastMessageArgs) {
			args.add(StringTag.valueOf(arg));
		}
		nbt.put("MessageArgs", args);
		return nbt;
	}

	/** "" for auto, else the chosen weapon's name. */
	private String tudursvehiclemod$weaponLabel(ServerLevel world, int unit) {
		int choice = this.weaponChoice[unit];
		if (choice < 0) {
			return "";
		}
		AbstractVehicleEntity vehicle = this.tudursvehiclemod$findUnitVehicle(world, unit);
		if (vehicle == null || choice >= vehicle.getDefinition().weapons().size()) {
			return "#" + (choice + 1);
		}
		String name = vehicle.getDefinition().weapons().get(choice).displayName();
		return name == null || name.isBlank() ? "#" + (choice + 1) : name;
	}

	// ---------------------------------------------------------------- NamedScreenHandlerFactory / Inventory

	@Override
	public Component getDisplayName() {
		return Component.translatable("block.tudursvehiclemod.fire_support_center");
	}

	@Override
	public AbstractContainerMenu createMenu(int syncId, Inventory playerInventory, Player player) {
		return new com.example.tudursvehiclemod.screen.FireSupportScreenHandler(syncId, playerInventory, this, this.getBlockPos());
	}

	@Override
	public int getContainerSize() {
		return SLOT_COUNT;
	}

	@Override
	public boolean isEmpty() {
		for (ItemStack stack : this.items) {
			if (!stack.isEmpty()) {
				return false;
			}
		}
		return true;
	}

	@Override
	public ItemStack getItem(int slot) {
		return this.items.get(slot);
	}

	@Override
	public ItemStack removeItem(int slot, int amount) {
		ItemStack result = ContainerHelper.removeItem(this.items, slot, amount);
		if (!result.isEmpty()) {
			this.tudursvehiclemod$onSlotChanged(slot, result);
		}
		return result;
	}

	@Override
	public ItemStack removeItemNoUpdate(int slot) {
		ItemStack result = ContainerHelper.takeItem(this.items, slot);
		if (!result.isEmpty()) {
			this.tudursvehiclemod$onSlotChanged(slot, result);
		}
		return result;
	}

	@Override
	public void setItem(int slot, ItemStack stack) {
		ItemStack previous = this.items.get(slot);
		this.items.set(slot, stack);
		if (stack.getCount() > this.getMaxStackSize()) {
			stack.setCount(this.getMaxStackSize());
		}
		if (!ItemStack.matches(previous, stack)) {
			this.tudursvehiclemod$onSlotChanged(slot, previous);
		}
	}

	/** A unit's stick (or the book) changed: the old vehicle's gunners leave, its chunk is let go, its weapon choice is reset. */
	private void tudursvehiclemod$onSlotChanged(int slot, ItemStack previous) {
		if (slot < UNIT_SLOTS && this.getLevel() instanceof ServerLevel world) {
			if (previous.getItem() instanceof DroneControlStickItem
					&& DroneCenterBlockEntity.tudursvehiclemod$findBoundVehicle(world, DroneControlStickItem.getRegisteredVehicleId(previous))
					instanceof AbstractVehicleEntity oldVehicle) {
				this.tudursvehiclemod$releaseGunners(oldVehicle, java.util.Set.of());
			}
			this.unitPlans.set(slot, List.of());
			this.weaponChoice[slot] = -1;
			this.lastKnownChunk[slot] = null;
			if (this.forcedUnitChunk[slot] != null) {
				ChunkForceTracker.release(world, this.forcedUnitChunk[slot], this.tudursvehiclemod$unitRequester(slot));
				this.forcedUnitChunk[slot] = null;
			}
		}
		this.ticksUntilResolve = 0;
		this.setChanged();
	}

	@Override
	public boolean stillValid(Player player) {
		return this.getLevel() != null && this.getLevel().getBlockEntity(this.getBlockPos()) == this
				&& player.distanceToSqr(Vec3.atCenterOf(this.getBlockPos())) <= 64.0;
	}

	@Override
	public boolean canPlaceItem(int slot, ItemStack stack) {
		return slot < UNIT_SLOTS ? stack.getItem() instanceof DroneControlStickItem : stack.getItem() instanceof DroneRouteBookItem;
	}

	@Override
	public int getMaxStackSize() {
		return 1;
	}

	@Override
	public void clearContent() {
		this.items.clear();
	}

	// ---------------------------------------------------------------- persistence

	@Override
	protected void loadAdditional(ValueInput view) {
		super.loadAdditional(view);
		this.items.clear();
		ContainerHelper.loadAllItems(view, this.items);
		this.active = view.getBooleanOr("Active", false);
		try {
			this.targetMode = TargetMode.valueOf(view.getStringOr("TargetMode", TargetMode.RELATIVE.name()));
		} catch (IllegalArgumentException e) {
			this.targetMode = TargetMode.RELATIVE;
		}
		this.relX = view.getIntOr("RelX", 0);
		this.relY = view.getIntOr("RelY", 0);
		this.relZ = view.getIntOr("RelZ", 64);
		this.waypointIndex = view.getIntOr("WaypointIndex", 0);
		this.designatedTarget = tudursvehiclemod$decodePos(view.getStringOr("DesignatedTarget", ""));
		this.highArc = view.getBooleanOr("HighArc", false);
		this.redstoneControl = view.getBooleanOr("RedstoneControl", false);
		this.remoteControl = view.getBooleanOr("RemoteControl", false);
		String[] choices = view.getStringOr("WeaponChoice", "").split(",");
		for (int unit = 0; unit < UNIT_SLOTS; unit++) {
			this.weaponChoice[unit] = -1;
			if (unit < choices.length) {
				try {
					this.weaponChoice[unit] = Integer.parseInt(choices[unit].trim());
				} catch (NumberFormatException ignored) {
				}
			}
			BlockPos chunk = tudursvehiclemod$decodePos(view.getStringOr("LastKnownChunk" + unit, ""));
			this.lastKnownChunk[unit] = chunk == null ? null : new ChunkPos(chunk.getX(), chunk.getZ());
		}
		this.ticksUntilResolve = 0;
	}

	@Override
	protected void saveAdditional(ValueOutput view) {
		super.saveAdditional(view);
		ContainerHelper.saveAllItems(view, this.items);
		view.putBoolean("Active", this.active);
		view.putString("TargetMode", this.targetMode.name());
		view.putInt("RelX", this.relX);
		view.putInt("RelY", this.relY);
		view.putInt("RelZ", this.relZ);
		view.putInt("WaypointIndex", this.waypointIndex);
		if (this.designatedTarget != null) {
			view.putString("DesignatedTarget", this.designatedTarget.getX() + "," + this.designatedTarget.getY() + "," + this.designatedTarget.getZ());
		}
		view.putBoolean("HighArc", this.highArc);
		view.putBoolean("RedstoneControl", this.redstoneControl);
		view.putBoolean("RemoteControl", this.remoteControl);
		StringBuilder choices = new StringBuilder();
		for (int unit = 0; unit < UNIT_SLOTS; unit++) {
			if (unit > 0) {
				choices.append(',');
			}
			choices.append(this.weaponChoice[unit]);
			if (this.lastKnownChunk[unit] != null) {
				view.putString("LastKnownChunk" + unit, this.lastKnownChunk[unit].x + ",0," + this.lastKnownChunk[unit].z);
			}
		}
		view.putString("WeaponChoice", choices.toString());
	}

	private static BlockPos tudursvehiclemod$decodePos(String encoded) {
		String[] parts = encoded.split(",");
		if (parts.length != 3) {
			return null;
		}
		try {
			return new BlockPos(Integer.parseInt(parts[0].trim()), Integer.parseInt(parts[1].trim()), Integer.parseInt(parts[2].trim()));
		} catch (NumberFormatException e) {
			return null;
		}
	}
}
