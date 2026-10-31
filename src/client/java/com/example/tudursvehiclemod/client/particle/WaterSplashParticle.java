package com.example.tudursvehiclemod.client.particle;

import com.example.tudursvehiclemod.particle.WaterSplashEffect;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.RandomSource;

/** This mod's own custom water-splash/column effect
 * (see textures/particle/water_splash.png - a bright white-blue droplet) -
 * a single instance is a droplet that bursts outward/upward then falls
 * back down under gravity, fading as it goes. Spawning MANY of these at
 * once with varied upward velocity (see
 * VehicleProjectileEntity's own tudursvehiclemod$spawnWaterSplash() and
 * AbstractVehicleEntity's own equivalent for explosions) is what actually
 * produces the "column" look for a big explosion, versus just a handful
 * for a small bullet-impact splash - this class itself doesn't know or
 * care which case it's part of, it only knows its own scale (see
 * WaterSplashEffect's own doc). */
public class WaterSplashParticle extends SingleQuadParticle {

	private static final int MIN_AGE = 12;
	private static final int MAX_AGE_EXTRA = 10;

	protected WaterSplashParticle(ClientLevel world, double x, double y, double z, float scale, TextureAtlasSprite sprite, RandomSource random) {
		super(world, x, y, z, sprite);
		this.lifetime = MIN_AGE + random.nextInt(MAX_AGE_EXTRA);
		this.gravity = 0.15f;
		this.hasPhysics = true;
		this.quadSize = scale;
		this.setColor(1.0f, 1.0f, 1.0f);
		this.alpha = 1.0f;
		// This particle's own burst velocity is
		// generated HERE, client-side, from the random parameter the
		// factory already receives - NOT from the server's own
		// velocityX/Y/Z spawnParticles() arguments. See
		// ExplosionFlashEffect's own doc for why relying on those for real
		// data proved unreliable; this sidesteps that uncertainty
		// entirely rather than risk it a second time for a DIFFERENT
		// piece of data (initial velocity instead of scale).
		double horizontalSpeed = 0.05 + random.nextDouble() * 0.12;
		double angle = random.nextDouble() * Math.PI * 2.0;
		this.xd = Math.cos(angle) * horizontalSpeed;
		this.zd = Math.sin(angle) * horizontalSpeed;
		this.yd = 0.2 + random.nextDouble() * 0.25;
	}

	@Override
	public void tick() {
		super.tick();
		float progress = (float) this.age / (float) this.lifetime;
		// Holds full brightness/size for most of its own life, then quickly fades right at the end (a droplet vanishing as it lands/dissipates, not a slow fade the whole way through).
		if (progress > 0.7f) {
			this.alpha = Math.max(0.0f, 1.0f - (progress - 0.7f) / 0.3f);
		}
	}

	@Override
	protected Layer getLayer() {
		return Layer.TRANSLUCENT;
	}

	public static class Factory implements ParticleProvider<WaterSplashEffect> {
		private final SpriteSet spriteProvider;

		public Factory(SpriteSet spriteProvider) {
			this.spriteProvider = spriteProvider;
		}

		@Override
		public Particle createParticle(WaterSplashEffect effect, ClientLevel world,
				double x, double y, double z, double velocityX, double velocityY, double velocityZ, RandomSource random) {
			TextureAtlasSprite sprite = this.spriteProvider.get(random);
			return new WaterSplashParticle(world, x, y, z, effect.scale(), sprite, random);
		}
	}
}
