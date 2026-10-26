package com.wraithcannon.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * 浓雾粒子的「负载系数」。
 *
 * <p>浓雾粒子类型注册为 {@code overrideLimiter = true}（为了远处也能看见），
 * 代价是原版「粒子效果：减少 / 最少」设置对它不再生效。这里手动补回：
 * 玩家调低粒子设置，或雾团离得很远（细节看不清）时，按比例减少生成数量。
 */
public final class ParticleLoad {

    private static final double FAR_SQR = 40.0 * 40.0;

    private ParticleLoad() {
    }

    /** 0 ~ 1 的系数，乘到「本 tick 要生成的粒子数」上。 */
    public static float scale(Vec3 at) {
        Minecraft mc = Minecraft.getInstance();
        float s = switch (mc.options.particles().get()) {
            case ALL -> 1.0F;
            case DECREASED -> 0.5F;
            case MINIMAL -> 0.25F;
        };
        LocalPlayer player = mc.player;
        if (player != null && player.distanceToSqr(at) > FAR_SQR) {
            s *= 0.5F;
        }
        return s;
    }
}
