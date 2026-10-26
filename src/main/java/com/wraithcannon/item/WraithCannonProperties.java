package com.wraithcannon.item;

import com.wraithcannon.StartupConfig;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.component.ItemAttributeModifiers;

/**
 * 冤魂炮的物品属性：近战伤害、攻速、耐久。
 *
 * <p>这些数值要在物品注册时写入，所以读的是 STARTUP 配置（{@link StartupConfig}，注册时立即加载），
 * 而不是普通的 {@code Config}（COMMON，注册时还没加载，读取会抛 IllegalStateException）。
 * 因此 {@code WraithCannon} 的构造器必须先注册启动配置，再触碰 {@code ModItems}。
 */
public final class WraithCannonProperties {

    /** 玩家空手基础攻速（原版固定为 4.0），攻速修饰符 = 目标攻速 - 4.0。 */
    private static final double PLAYER_BASE_ATTACK_SPEED = 4.0D;

    private WraithCannonProperties() {
    }

    public static int durability() {
        return StartupConfig.DURABILITY.get();
    }

    /** 攻击伤害 + 攻速修饰符。 */
    public static ItemAttributeModifiers buildAttributeModifiers() {
        return ItemAttributeModifiers.builder()
                .add(Attributes.ATTACK_DAMAGE,
                        new AttributeModifier(
                                Item.BASE_ATTACK_DAMAGE_ID,
                                StartupConfig.ATTACK_DAMAGE.get(),
                                AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .add(Attributes.ATTACK_SPEED,
                        new AttributeModifier(
                                Item.BASE_ATTACK_SPEED_ID,
                                StartupConfig.ATTACK_SPEED.get() - PLAYER_BASE_ATTACK_SPEED,
                                AttributeModifier.Operation.ADD_VALUE),
                        EquipmentSlotGroup.MAINHAND)
                .build();
    }

    public static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath(com.wraithcannon.WraithCannon.MOD_ID, path);
    }
}

