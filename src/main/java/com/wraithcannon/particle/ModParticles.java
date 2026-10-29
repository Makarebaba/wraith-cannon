package com.wraithcannon.particle;

import com.wraithcannon.WraithCannon;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/**
 * 自定义粒子类型。
 *
 * <p>构造参数是 {@code overrideLimiter}。{@code true} = 无视距离限制地渲染
 * （Forge 粒子文档：「an overrideLimiter which determines whether the particle renders regardless of distance」）。
 * 传 {@code false} 时，原版客户端会丢弃离镜头超过 32 格的粒子，
 * 并受「粒子效果：减少/最少」设置影响，远距离的雾会看不到。
 * 这里传 true，使长射程下整条雾带可见。
 */
public class ModParticles {

    public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES =
            DeferredRegister.create(BuiltInRegistries.PARTICLE_TYPE, WraithCannon.MOD_ID);

    public static final Supplier<SimpleParticleType> CHAOS_MIST =
            PARTICLE_TYPES.register("chaos_mist", () -> new SimpleParticleType(true));
}

