package com.wraithcannon.event;

import com.wraithcannon.WraithCannon;
import com.wraithcannon.Config;
import com.wraithcannon.client.ClientAnimState;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ComputeFovModifierEvent;

/**
 * 蓄力时的视角拉近（类似弓，但幅度更小）。
 *
 * <p>{@link ComputeFovModifierEvent}：NeoForge 在原版算完玩家 FOV 修正之后触发（仅客户端、主事件总线，
 * 不可取消），允许再调整一次；{@code setNewFovModifier} 设置最终值。
 *
 * <p>拉近量随蓄力进度按平方曲线增长（前慢后快，和拉弓的手感一致），蓄满时缩小 {@code feel.chargeZoom}（默认 0.15，上限 0.15 = 弓）。
 * 同时乘上玩家的「视野效果」辅助功能滑块（设为 0 就完全不拉近），和原版对待弓的方式一致。
 */
@EventBusSubscriber(modid = WraithCannon.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public class FovEvents {

        @SubscribeEvent
    public static void onComputeFov(ComputeFovModifierEvent event) {
        Player player = event.getPlayer();
        if (!ClientAnimState.isChargingCannon(player)) {
            return;
        }
        float progress = Mth.clamp(player.getTicksUsingItem() / (float) Config.CHARGE_TICKS.get(), 0.0F, 1.0F);
        float eased = progress * progress;
        float effectScale = Minecraft.getInstance().options.fovEffectScale().get().floatValue();
        event.setNewFovModifier(event.getNewFovModifier() * (1.0F - Config.FOV_MAX_ZOOM.get().floatValue() * eased * effectScale));
    }

    /** 枪口上跳的逐 tick 推进（顺带放在这个客户端事件类里）。 */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        ClientAnimState.tickKick();
    }
}
