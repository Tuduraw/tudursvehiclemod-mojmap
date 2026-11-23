package com.example.tudursvehiclemod.asset;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.Identifier;

/** One box on a vehicle where a hit is scaled by its own multiplier instead of the vehicle's armor_damage_factor / armor_min_damage - a weak spot (an engine), or a part with thicker or thinner armor. A vehicle definition's "damage_zones" list (vehicle JSON):
 *
 * <pre>
 * "damage_zones": [
 *   { "center": [0, 1.2, -2.5], "size": [1.5, 1.0, 2.0], "damage_factor": 2.0, "min_damage": 0 },
 *   { "center": [0, 2.4, 0], "size": [2.0, 1.0, 3.0], "damage_factor": 0.2, "min_damage": 10, "part": "$turret" }
 * ]
 * </pre>
 * <ul>
 * <li>center, size: the box's centre and its full size along x, y and z, in the model's (OBJ) own coordinates - before the vehicle's scale, turning with the vehicle's body (and with a moving part, if part names one).</li>
 * <li>damage_factor: the hit's damage is multiplied by this (default 1).</li>
 * <li>min_damage: if the damage after damage_factor is below this, the hit does no damage at all (default 0).</li>
 * <li>part (optional): the name of a moving part (a weapon part such as a turret, a toggle part, a spinning part, a VTOL rotor) the box moves with - centre and size are still in the model's coordinates, as at the part's resting position, and the box follows the part as it turns, slides or opens (AbstractVehicleEntity.tudursvehiclemod$getPartTransform()). A name the vehicle has no moving part by leaves the box on the body.</li>
 * </ul>
 * A hit inside a zone uses the zone's two values in place of the vehicle's own armor_damage_factor and armor_min_damage; armor_max_damage still caps it. Where zones overlap, the one listed first is used. A hit outside every zone, or damage with no point of impact (crashes, fire, drowning...), uses the vehicle's own values as before.
 *
 * <p>The point of impact: where a projectile touched the vehicle's model; for an explosion, its centre.
 *
 * <p>Kept beside the definitions rather than in VehicleDefinition itself (VehicleDefinitionReloadListener reads the list from the same file), so the definition record - which addons build directly - doesn't change. Server side only. */
public record DamageZone(List<Float> center, List<Float> size, float damageFactor, float minDamage, java.util.Optional<String> part) {

	public static final Codec<DamageZone> CODEC = RecordCodecBuilder.create(instance -> instance.group(
			Codec.FLOAT.listOf(3, 3).fieldOf("center").forGetter(DamageZone::center),
			Codec.FLOAT.listOf(3, 3).fieldOf("size").forGetter(DamageZone::size),
			Codec.FLOAT.optionalFieldOf("damage_factor", 1.0f).forGetter(DamageZone::damageFactor),
			Codec.FLOAT.optionalFieldOf("min_damage", 0.0f).forGetter(DamageZone::minDamage),
			Codec.STRING.optionalFieldOf("part").forGetter(DamageZone::part)
	).apply(instance, DamageZone::new));

	/** Whether the point (model coordinates, before scale - for a zone on a part, in the part's resting position) is inside this box. */
	public boolean contains(double x, double y, double z) {
		return Math.abs(x - this.center.get(0)) <= Math.abs(this.size.get(0)) * 0.5
				&& Math.abs(y - this.center.get(1)) <= Math.abs(this.size.get(1)) * 0.5
				&& Math.abs(z - this.center.get(2)) <= Math.abs(this.size.get(2)) * 0.5;
	}

	private static volatile Map<Identifier, List<DamageZone>> ZONES = Map.of();

	/** The damage zones of the vehicle definition with this id (empty when it has none). */
	public static List<DamageZone> of(Identifier vehicleId) {
		if (vehicleId == null) {
			return List.of();
		}
		return ZONES.getOrDefault(vehicleId, List.of());
	}

	/** Replaces every vehicle's damage zones (VehicleDefinitionReloadListener, on each reload). */
	public static void setAll(Map<Identifier, List<DamageZone>> zones) {
		ZONES = Map.copyOf(zones);
	}

	/** Adds or replaces one vehicle's zones - for an addon that builds its own definitions in code. An empty list removes them. */
	public static synchronized void set(Identifier vehicleId, List<DamageZone> zones) {
		Map<Identifier, List<DamageZone>> copy = new java.util.HashMap<>(ZONES);
		if (zones == null || zones.isEmpty()) {
			copy.remove(vehicleId);
		} else {
			copy.put(vehicleId, List.copyOf(zones));
		}
		ZONES = Map.copyOf(copy);
	}
}
