package com.example.tudursvehiclemod.client.render;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

/** A completely standalone entity model, built entirely from its own ModelPartBuilder calls below - it shares no geometry, no ModelData, and no construction code with PlayerEntityModel or vanilla's own player rendering whatsoever, so nothing this class does can ever affect an actual player or interact with any other mod's own player-model changes.
 *
 * Proportions match Steve's own well-established body part dimensions exactly (so an ordinary player skin still maps onto it correctly), but the legs' own rest pose is built seated from the start (see SEATED_LEG_PITCH's own doc) - there is no "standing" variant of this model at all, matching a dummy pilot's own always-seated nature (see DummyPilotEntity's own tick(), which discards itself the instant it isn't riding a vehicle).
 *
 * Base body parts (head, body, arms, legs) each carry their own 1.8+ overlay layer (hat/jacket/sleeves/pants) as a direct child, supporting Minecraft 1.8+ player-skin layers - see getTexturedModelData()'s own doc for the exact UV/dilation values. Still no cape or ears at all - a dummy pilot is a fixed-shape passenger, not a full player-feature surface. */
public class DummyPilotModel extends EntityModel<LivingEntityRenderState> {

	/** Forward bend (radians) for each leg's own hip pivot, baked into its rest pose from construction - roughly 90 degrees, a normal seated angle. */
	private static final float SEATED_LEG_PITCH = (float) (-Math.PI / 2.0);

	private final ModelPart head;
	private final ModelPart body;
	private final ModelPart rightArm;
	private final ModelPart leftArm;

	public DummyPilotModel(ModelPart root) {
		super(root);
		this.head = root.getChild("head");
		this.body = root.getChild("body");
		this.rightArm = root.getChild("right_arm");
		this.leftArm = root.getChild("left_arm");
		// Legs are never separately posed at runtime (see this class's own doc) - no field needed for them beyond their own baked-in construction below.
	}

	/** Builds this model's own geometry from scratch - see this class's own doc for why nothing here is derived from or shared with PlayerEntityModel. Dimensions and pivots match vanilla's own long-standing Steve body proportions exactly, so an ordinary 64x64 player skin still maps onto every part correctly; only the leg pivots' own rotation differs from a standing biped. Each base part also gets its own 1.8+ overlay layer (hat/jacket/sleeves/pants) as a direct child - see this class's own doc for why nesting (rather than a separate root sibling) is what actually makes these render at all with no further code needed. */
	public static LayerDefinition getTexturedModelData() {
		MeshDefinition modelData = new MeshDefinition();
		PartDefinition root = modelData.getRoot();
		PartDefinition headData = root.addOrReplaceChild("head", CubeListBuilder.create().texOffs(0, 0)
				.addBox(-4.0f, -8.0f, -4.0f, 8, 8, 8, CubeDeformation.NONE), PartPose.offset(0.0f, 0.0f, 0.0f));
		headData.addOrReplaceChild("hat", CubeListBuilder.create().texOffs(32, 0)
				.addBox(-4.0f, -8.0f, -4.0f, 8, 8, 8, new CubeDeformation(0.5f)), PartPose.offset(0.0f, 0.0f, 0.0f));

		PartDefinition bodyData = root.addOrReplaceChild("body", CubeListBuilder.create().texOffs(16, 16)
				.addBox(-4.0f, 0.0f, -2.0f, 8, 12, 4, CubeDeformation.NONE), PartPose.offset(0.0f, 0.0f, 0.0f));
		bodyData.addOrReplaceChild("jacket", CubeListBuilder.create().texOffs(16, 32)
				.addBox(-4.0f, 0.0f, -2.0f, 8, 12, 4, new CubeDeformation(0.25f)), PartPose.offset(0.0f, 0.0f, 0.0f));

		PartDefinition rightArmData = root.addOrReplaceChild("right_arm", CubeListBuilder.create().texOffs(40, 16)
				.addBox(-3.0f, -2.0f, -2.0f, 4, 12, 4, CubeDeformation.NONE), PartPose.offset(-5.0f, 2.0f, 0.0f));
		rightArmData.addOrReplaceChild("right_sleeve", CubeListBuilder.create().texOffs(40, 32)
				.addBox(-3.0f, -2.0f, -2.0f, 4, 12, 4, new CubeDeformation(0.25f)), PartPose.offset(0.0f, 0.0f, 0.0f));

		PartDefinition leftArmData = root.addOrReplaceChild("left_arm", CubeListBuilder.create().texOffs(40, 16).mirror()
				.addBox(-1.0f, -2.0f, -2.0f, 4, 12, 4, CubeDeformation.NONE), PartPose.offset(5.0f, 2.0f, 0.0f));
		leftArmData.addOrReplaceChild("left_sleeve", CubeListBuilder.create().texOffs(40, 32).mirror()
				.addBox(-1.0f, -2.0f, -2.0f, 4, 12, 4, new CubeDeformation(0.25f)), PartPose.offset(0.0f, 0.0f, 0.0f));

		// Per this class's own doc: the ONLY parts whose own rest transform includes rotation - baked seated from construction, never touched at runtime.
		PartDefinition rightLegData = root.addOrReplaceChild("right_leg", CubeListBuilder.create().texOffs(0, 16)
						.addBox(-2.0f, 0.0f, -2.0f, 4, 12, 4, CubeDeformation.NONE),
				PartPose.offsetAndRotation(-1.9f, 12.0f, 0.0f, SEATED_LEG_PITCH, 0.0f, 0.0f));
		rightLegData.addOrReplaceChild("right_pants", CubeListBuilder.create().texOffs(0, 32)
				.addBox(-2.0f, 0.0f, -2.0f, 4, 12, 4, new CubeDeformation(0.25f)), PartPose.offset(0.0f, 0.0f, 0.0f));

		PartDefinition leftLegData = root.addOrReplaceChild("left_leg", CubeListBuilder.create().texOffs(0, 16).mirror()
						.addBox(-2.0f, 0.0f, -2.0f, 4, 12, 4, CubeDeformation.NONE),
				PartPose.offsetAndRotation(1.9f, 12.0f, 0.0f, SEATED_LEG_PITCH, 0.0f, 0.0f));
		leftLegData.addOrReplaceChild("left_pants", CubeListBuilder.create().texOffs(0, 32).mirror()
				.addBox(-2.0f, 0.0f, -2.0f, 4, 12, 4, new CubeDeformation(0.25f)), PartPose.offset(0.0f, 0.0f, 0.0f));
		return LayerDefinition.create(modelData, 64, 64);
	}

	/** Only the head tracks the render state at all (a natural "looking around" cue) - arms/body/legs stay exactly at their own construction-time rest pose, since this pilot never walks, swings its arms, or otherwise animates beyond that. */
	@Override
	public void setupAnim(LivingEntityRenderState state) {
		this.setAngles(state);
	}

	/** Keeps its Yarn name on purpose: addon mods built against the release (Yarn) jar call this typed method directly, e.g. super.setAngles(...) from their own renderer. In the release jar only the bridge method gets the intermediary name; this typed body stays "setAngles". The Mojang-named override above just forwards here. */
	public void setAngles(LivingEntityRenderState state) {
		this.head.yRot = state.yRot * ((float) Math.PI / 180f);
		this.head.xRot = state.xRot * ((float) Math.PI / 180f);
	}
}
