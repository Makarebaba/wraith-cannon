package com.wraithcannon.client;

import com.wraithcannon.item.WraithCannonItem;
import net.minecraft.client.Minecraft;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;

/**
 * 客户端状态查询。单独放一个类，是为了让 {@link WraithCannonItem} 在专用服务端上
 * 不会因为引用 {@link Minecraft} 而在类加载时出问题（只有动画 predicate 运行时才会碰到本类，
 * 而 predicate 只在客户端渲染时执行）。
 */
public final class ClientAnimState {

    private ClientAnimState() {
    }

    /** 当前客户端玩家是否正在用主手冤魂炮蓄力（右键按住中）。 */
    public static boolean isChargingCannon() {
        return isChargingCannon(Minecraft.getInstance().player);
    }

    /** 指定玩家是否正在用主手冤魂炮蓄力。 */
    public static boolean isChargingCannon(Player player) {
        return player != null
                && player.isUsingItem()
                && player.getUsedItemHand() == InteractionHand.MAIN_HAND
                && player.getUseItem().getItem() instanceof WraithCannonItem;
    }

    /** 是不是本机玩家。 */
    public static boolean isLocalPlayer(net.minecraft.world.entity.Entity e) {
        return e == Minecraft.getInstance().player;
    }

    /**
     * 这次手臂摆动是不是「左键攻击」。本机玩家看攻击键是否按住；
     * 其他玩家无从得知，一律当作真实挥击。
     */
    public static boolean isRealSwing(net.minecraft.world.entity.Entity e) {
        return !isLocalPlayer(e) || Minecraft.getInstance().options.keyAttack.isDown();
    }

    // ---- 开火后枪口上跳：把总角度分几 tick 逐步抬起镜头 ----
    private static float pendingKick;

    /** 本机玩家最近一次开火的时间（System.nanoTime）；0 = 从未开火。 */
    private static long lastFireNanos;

    /** fire 动画总长（秒），与 animation.json 一致。 */
    public static final float FIRE_ANIM_SECONDS = 0.8F;
    /** fire 动画里「保持扛肩姿势」的时长（秒）：0.45 秒时姿势回到与蓄力定格相同，之后才开始回落到待机。 */
    public static final float FIRE_HOLD_SECONDS = 0.45F;

    /**
     * 开火后「扛肩偏移」的保留系数：0.45 秒内保持 1，随后在 fire 动画回落段（0.45~0.8 秒）内
     * 按 easeInOutCubic 降到 0；不在开火动画里返回 0。
     *
     * <p>fire 动画的起始姿势就是「扛肩」姿势（带 Java 侧的手部偏移），若在松开右键那一帧把偏移
     * 瞬间清零，武器会先跳到别处再播动画；因此让偏移与动画同步回落。
     */
    public static float fireShoulderFactor() {
        if (lastFireNanos == 0L) {
            return 0.0F;
        }
        float t = (System.nanoTime() - lastFireNanos) / 1.0E9F;
        if (t < 0.0F || t >= FIRE_ANIM_SECONDS) {
            return 0.0F;
        }
        if (t <= FIRE_HOLD_SECONDS) {
            return 1.0F;
        }
        float u = (t - FIRE_HOLD_SECONDS) / (FIRE_ANIM_SECONDS - FIRE_HOLD_SECONDS);
        float eased = u < 0.5F ? 4.0F * u * u * u : 1.0F - (float) Math.pow(-2.0F * u + 2.0F, 3) / 2.0F;
        return 1.0F - eased;
    }

    public static void kickMuzzle(Player player) {
        if (isLocalPlayer(player)) {
            lastFireNanos = System.nanoTime();
            pendingKick += com.wraithcannon.Config.MUZZLE_CLIMB.get().floatValue();
        }
    }

    /** 每个客户端 tick 调一次。 */
    public static void tickKick() {
        if (pendingKick <= 0.0F) {
            return;
        }
        Player p = Minecraft.getInstance().player;
        if (p == null) {
            pendingKick = 0.0F;
            return;
        }
        float step = pendingKick > 0.4F ? pendingKick * 0.5F : pendingKick;
        pendingKick -= step;
        p.setXRot(net.minecraft.util.Mth.clamp(p.getXRot() - step, -90.0F, 90.0F));
    }
}
