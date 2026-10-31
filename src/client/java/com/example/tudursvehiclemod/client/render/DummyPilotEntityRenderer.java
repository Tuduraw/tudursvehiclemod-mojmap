package com.example.tudursvehiclemod.client.render;

import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

import com.example.tudursvehiclemod.entity.DummyPilotEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.resources.Identifier;

/** Renders DummyPilotEntity with its own completely standalone model (DummyPilotModel) - see that class's own doc for why it shares nothing with PlayerEntityModel - and simply swaps in whichever skin that particular pilot is configured with (see getTexture()).
 *
 * No feature renderers are added at all (no armor layer, no held-item layer, no cape): this pilot has no equipment by design (see DummyPilotEntity's own getEquippedStack()), so every one of those would render nothing while still costing a pass. */
public class DummyPilotEntityRenderer extends LivingEntityRenderer<DummyPilotEntity, DummyPilotEntityRenderer.State, DummyPilotModel> {

	/** The render state plus this pilot's own skin. 1.21.11 extracts every entity's render state first and draws them all afterwards, so the skin has to travel in the state - it used to be kept on the renderer (one instance shared by every dummy pilot), and every pilot was drawn with the skin of whichever one was extracted last. */
	public static class State extends LivingEntityRenderState {
		public String skinId;
	}

	public DummyPilotEntityRenderer(EntityRendererProvider.Context context) {
		super(context, new DummyPilotModel(context.bakeLayer(DummyPilotModelLayers.DUMMY_PILOT)), 0.5f);
	}

	@Override
	public State createRenderState() {
		return new State();
	}

	@Override
	public void extractRenderState(DummyPilotEntity entity, State state, float tickDelta) {
		this.updateRenderState(entity, state, tickDelta);
	}

	/** Keeps its Yarn name on purpose: addon mods built against the release (Yarn) jar call this typed method directly, e.g. super.updateRenderState(...) from their own renderer. In the release jar only the bridge method gets the intermediary name; this typed body stays "updateRenderState". The Mojang-named override above just forwards here. */
	public void updateRenderState(DummyPilotEntity entity, State state, float tickDelta) {
		super.extractRenderState(entity, state, tickDelta);
		state.skinId = entity.tudursvehiclemod$getSkinId();
	}

	@Override
	public Identifier getTextureLocation(State state) {
		return this.getTexture(state);
	}

	/** Keeps its Yarn name on purpose: addon mods built against the release (Yarn) jar call this typed method directly, e.g. super.getTexture(...) from their own renderer. In the release jar only the bridge method gets the intermediary name; this typed body stays "getTexture". The Mojang-named override above just forwards here. */
	public Identifier getTexture(State state) {
		// Resolves through DummyPilotSkins so an addon-supplied skin gets registered on first use, and anything unknown/unreadable falls back to the built-in default - see that class's own doc.
		return DummyPilotSkins.tudursvehiclemod$getTexture(state.skinId);
	}

	/** Vanilla's own default hasLabel() (confirmed via the decompiled EntityRenderer class - it references both hasCustomName() and the currently-hover-targeted entity) shows a custom-named entity's label whenever the player looks directly at it, REGARDLESS of setCustomNameVisible() - that flag only controls whether the label shows unconditionally (always-on) versus only-on-hover, never whether it can show at all. Overridden here so this screen's own toggle is a genuine, unconditional on/off switch: false means never, full stop, matching what a person setting it to "hidden" actually expects. */
	@Override
	protected boolean shouldShowName(DummyPilotEntity entity, double distanceSquared) {
		return this.hasLabel(entity, distanceSquared);
	}

	/** Keeps its Yarn name on purpose: addon mods built against the release (Yarn) jar call this typed method directly, e.g. super.hasLabel(...) from their own renderer. In the release jar only the bridge method gets the intermediary name; this typed body stays "hasLabel". The Mojang-named override above just forwards here. */
	protected boolean hasLabel(DummyPilotEntity entity, double distanceSquared) {
		return entity.isCustomNameVisible() && super.shouldShowName(entity, distanceSquared);
	}
}
