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

    public static void kickMuzzle(Player player) {
        if (isLocalPlayer(player)) {
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
