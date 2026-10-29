package com.wraithcannon.event;

import com.wraithcannon.WraithCannon;
import com.wraithcannon.client.ClientAnimState;
import com.wraithcannon.item.WraithCannonItem;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;

/**
 * 「扛肩」姿势。
 *
 * <p>GeckoLib 只能动<b>物品模型</b>的骨骼，玩家的手臂不归它管，
 * 所以手始终抓在尾柄上。这里用两个客户端事件补上手臂：
 * <ul>
 *   <li><b>第一人称</b>：{@link RenderHandEvent} 里在原版手臂/物品变换之前，
 *       把整只手连同冤魂炮向上、向镜头方向平移，随蓄力时间渐入（0.45 秒，与 charge_start 同步）。</li>
 *   <li><b>第三人称</b>：{@link RenderLivingEvent.Pre} 里把持杖手臂切成原版弓的姿势
 *       {@code BOW_AND_ARROW}（双臂抬到胸肩高度向前）。
 *       时序（NeoForge 1.21.1）：{@code PlayerRenderer.render} 先 setModelProperties，
 *       再进 {@code LivingEntityRenderer.render} 触发本事件，此时写入的姿势会被后面的 setupAnim 使用。
 *       物品本身在第三人称不播动画（见 WraithCannonModel#setCustomAnimations），保持普通武器的静态外观。</li>
 * </ul>
 *
 * <p>手部偏移量集中在下面几个常量里，可按需微调。
 */
@EventBusSubscriber(modid = WraithCannon.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public class ShoulderPoseEvents {

    /** 上肩过渡时长（tick），与 charge_start 的 0.45 秒对应。 */
    private static final float RAISE_TICKS = 9.0F;

    // ---- 第一人称偏移（相对原版手持位置，单位：格 / 度） ----
    private static final float FP_UP = 0.40F;
    private static final float FP_TOWARD_CAMERA = 0.35F;
    private static final float FP_LEFT = -0.05F;
    private static final float FP_PITCH_DEG = -8.0F;

    /** 玩家是否正在用主手的冤魂炮蓄力。 */
    private static boolean isShouldering(LivingEntity entity) {
        return entity.isUsingItem()
                && entity.getUsedItemHand() == InteractionHand.MAIN_HAND
                && entity.getMainHandItem().getItem() instanceof WraithCannonItem;
    }

    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        Player player = net.minecraft.client.Minecraft.getInstance().player;
        if (event.getHand() == InteractionHand.OFF_HAND) {
            // 蓄力时隐藏副手物品渲染（和原版拉弓一样）；副手的交互逻辑不变
            if (player != null && isShouldering(player)) {
                event.setCanceled(true);
            }
            return;
        }
        if (player == null || !(player.getMainHandItem().getItem() instanceof WraithCannonItem)) {
            return;
        }
        float k;
        if (isShouldering(player)) {
            float t = Math.min(1.0F, (player.getTicksUsingItem() + event.getPartialTick()) / RAISE_TICKS);
            k = t * t * (3.0F - 2.0F * t);   // smoothstep
        } else {
            // 开火后：偏移与 fire 动画同步回落，而不是松手瞬间归零
            k = ClientAnimState.fireShoulderFactor();
        }
        if (k <= 0.0F) {
            return;
        }
        // 左撇子时镜像 X
        float side = player.getMainArm() == HumanoidArm.RIGHT ? 1.0F : -1.0F;

        var pose = event.getPoseStack();
        pose.translate(FP_LEFT * k * side, FP_UP * k, FP_TOWARD_CAMERA * k);
        pose.mulPose(com.mojang.math.Axis.XP.rotationDegrees(FP_PITCH_DEG * k));
    }

    @SubscribeEvent
    public static void onRenderLivingPre(RenderLivingEvent.Pre<?, ?> event) {
        LivingEntity entity = event.getEntity();
        if (!(entity instanceof Player) || !isShouldering(entity)) {
            return;
        }
        if (event.getRenderer().getModel() instanceof HumanoidModel<?> model) {
            if (entity.getMainArm() == HumanoidArm.RIGHT) {
                model.rightArmPose = HumanoidModel.ArmPose.BOW_AND_ARROW;
            } else {
                model.leftArmPose = HumanoidModel.ArmPose.BOW_AND_ARROW;
            }
        }
    }
}

