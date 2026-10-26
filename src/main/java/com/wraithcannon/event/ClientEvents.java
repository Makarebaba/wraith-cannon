package com.wraithcannon.event;

import com.wraithcannon.WraithCannon;
import com.wraithcannon.entity.ModEntities;
import com.wraithcannon.particle.ChaosMistParticle;
import com.wraithcannon.particle.ModParticles;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

/**
 * 客户端注册。
 *
 * <p>做两件事：
 * <ol>
 *   <li>把自定义粒子接上渲染工厂</li>
 *   <li>给 {@code ChaosMistCloud} 注册一个空渲染器</li>
 * </ol>
 *
 * <p><b>为什么必须注册实体渲染器</b>：1.21.1 的
 * {@code EntityRenderDispatcher.shouldRender} 会直接对
 * {@code getRenderer(entity)} 的返回值调用方法，拿不到渲染器就是 NPE，
 * 整个渲染线程崩掉。云雾实体本身没有模型，用
 * {@link NoopRenderer} 占位即可——它的视觉全靠粒子。
 *
 * <p>物品渲染器不在这里注册：GeckoLib 通过
 * {@code WraithCannonItem#createGeoRenderer} 自己取。
 */
@EventBusSubscriber(modid = WraithCannon.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientEvents {

    @SubscribeEvent
    public static void registerParticleProviders(RegisterParticleProvidersEvent event) {
        // registerSpriteSet 要求 assets/wraith_cannon/particles/chaos_mist.json 存在，
        // 否则日志会报 "Failed to load description"
        event.registerSpriteSet(ModParticles.CHAOS_MIST.get(), ChaosMistParticle.Provider::new);
    }

    @SubscribeEvent
    public static void registerEntityRenderers(EntityRenderersEvent.RegisterRenderers event) {
        // 云雾没有模型，视觉完全由粒子承担。
        // 不注册会直接崩客户端，见类注释。
        event.registerEntityRenderer(ModEntities.CHAOS_MIST.get(), NoopRenderer::new);
    }
}

