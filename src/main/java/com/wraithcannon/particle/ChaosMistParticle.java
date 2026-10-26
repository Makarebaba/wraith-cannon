package com.wraithcannon.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * 「五彩斑斓的黑」浓雾粒子。
 *
 * <p>设计要点：
 * <ul>
 *   <li>无重力：{@code gravity = 0}</li>
 *   <li>有滞后：{@code friction = 0.92}</li>
 *   <li>短暂聚集后消散：前 1/3 寿命膨胀，之后缩小并淡出</li>
 *   <li>多色暗调：贴图本身已经是暗紫/暗绿/暗金/暗红的烟雾</li>
 * </ul>
 *
 * <p><b>重要</b>：颜色来自贴图本身，不要再用 {@code setColor()} 二次压暗。
 * 贴图已经是暗色调，再乘一层暗色会让粒子变成几乎纯黑，看起来像黑色方块。
 * 想要颜色变化，靠 {@code particles/chaos_mist.json} 里列的多张贴图轮播。
 */
public class ChaosMistParticle extends TextureSheetParticle {

    private final SpriteSet spriteSet;
    private final float baseSize;

    protected ChaosMistParticle(ClientLevel level, double x, double y, double z,
                                double xSpeed, double ySpeed, double zSpeed,
                                SpriteSet spriteSet, float scale) {
        super(level, x, y, z, xSpeed, ySpeed, zSpeed);
        this.spriteSet = spriteSet;

        // ---- 物理：无重力 + 高滞后 ----
        this.gravity = 0.0F;
        this.friction = 0.92F;
        this.hasPhysics = false;

        // ---- 生命周期：1.5 ~ 2 秒 ----
        // 云团持续喷粒子到约第 1.5 秒（见 ChaosMistCloud.EMIT_END_TICKS），
        // 最晚出生的粒子再活 2 秒，整团雾在 3 秒左右散尽。
        this.lifetime = 30 + level.random.nextInt(11);

        // ---- 速度 ----
        this.xd = xSpeed;
        this.yd = ySpeed;
        this.zd = zSpeed;

        // ---- 外观 ----
        // 尺寸放大一些，否则 16x16 的贴图在游戏里几乎看不见
        this.baseSize = 0.9F * scale;
        this.quadSize = this.baseSize;

        // 不调用 setColor：完全用贴图本色。
        // 贴图如果是暗紫/暗绿/暗金，游戏里就是那个颜色。
        this.setAlpha(0.9F);

        // 从贴图序列里随机取一帧起步，避免整团雾同步闪
        this.pickSprite(spriteSet);
    }

    @Override
    public void tick() {
        super.tick();
        if (this.removed) {
            return;
        }
        // 帧动画：让雾翻腾
        this.setSpriteFromAge(this.spriteSet);

        // 后期淡出，避免突然消失
        float progress = (float) this.age / (float) this.lifetime;
        if (progress > 0.7F) {
            float fade = 1.0F - (progress - 0.7F) / 0.3F;
            this.setAlpha(Math.max(0.0F, 0.9F * fade));
        }
    }

    @Override
    public float getQuadSize(float scaleFactor) {
        float progress = (float) this.age / (float) this.lifetime;
        float curve;
        if (progress < 0.33F) {
            // 前 1 秒：迅速膨胀聚集
            curve = 0.5F + progress * 1.8F;
        } else {
            // 后 2 秒：缓慢收缩消散
            curve = 1.1F * (1.0F - (progress - 0.33F) / 0.67F);
        }
        return this.baseSize * Math.max(curve, 0.05F);
    }

    @Override
    protected int getLightColor(float partialTick) {
        // 全亮，否则暗色贴图在夜里完全看不见。
        // 0xF000F0 = 天空光照 15 + 方块光照 15
        return 0xF000F0;
    }

    @Override
    public ParticleRenderType getRenderType() {
        // 贴图带透明通道，必须用半透明层
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    /** Provider 工厂。 */
    public static class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet spriteSet;

        public Provider(SpriteSet spriteSet) {
            this.spriteSet = spriteSet;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level,
                                       double x, double y, double z,
                                       double xSpeed, double ySpeed, double zSpeed) {
            float scale = 0.9F + level.random.nextFloat() * 0.5F;
            return new ChaosMistParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, this.spriteSet, scale);
        }
    }
}

