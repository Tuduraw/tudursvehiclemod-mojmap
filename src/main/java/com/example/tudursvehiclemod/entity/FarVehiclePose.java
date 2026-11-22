package com.example.tudursvehiclemod.entity;

import net.minecraft.network.RegistryFriendlyByteBuf;

/** What a far vehicle's stand-in needs, beyond where it is and which way it faces, to be drawn as the real one is (entity.FarVehicleSync, client.farvehicle.FarVehicleProxies): its moving parts.
 *
 * <ul>
 * <li>toggle: each toggle part's progress (landing gear, hatches, canopies, weapon bays, folding wings - VehicleDefinition.toggleParts(), in that order), 0-255 for 0-1.</li>
 * <li>partAngles: each weapon part's yaw and pitch, then its parent stage's yaw and pitch (VehicleDefinition.weaponParts(), in that order, 4 per part), in 1/100 degree; {@link #NONE} where the vehicle hasn't one (the part's resting position is drawn).</li>
 * <li>spin: how fast its spinning parts (propellers, rotors) turn, as a multiple of each part's own rate.</li>
 * <li>destroyed: whether it is a wreck.</li>
 * </ul>
 * Both sides read the same vehicle definition, so the lists line up by index. */
public record FarVehiclePose(boolean destroyed, float spin, byte[] toggle, short[] partAngles) {

	/** No angle (partAngles). */
	public static final short NONE = Short.MIN_VALUE;

	public static final FarVehiclePose EMPTY = new FarVehiclePose(false, 0f, new byte[0], new short[0]);

	static short encodeAngle(Float degrees) {
		if (degrees == null || !Float.isFinite(degrees)) {
			return NONE;
		}
		return (short) Math.round(net.minecraft.util.Mth.wrapDegrees(degrees) * 100f);
	}

	static Float decodeAngle(short value) {
		return value == NONE ? null : value / 100f;
	}

	public void write(RegistryFriendlyByteBuf buf) {
		buf.writeBoolean(this.destroyed);
		buf.writeFloat(this.spin);
		buf.writeByteArray(this.toggle);
		buf.writeVarInt(this.partAngles.length);
		for (short angle : this.partAngles) {
			buf.writeShort(angle);
		}
	}

	public static FarVehiclePose read(RegistryFriendlyByteBuf buf) {
		boolean destroyed = buf.readBoolean();
		float spin = buf.readFloat();
		byte[] toggle = buf.readByteArray(1024);
		int count = buf.readVarInt();
		if (count < 0 || count > 4096) {
			throw new IllegalArgumentException("Too many part angles: " + count);
		}
		short[] angles = new short[count];
		for (int i = 0; i < count; i++) {
			angles[i] = buf.readShort();
		}
		return new FarVehiclePose(destroyed, spin, toggle, angles);
	}
}
