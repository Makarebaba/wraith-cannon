package com.wraithcannon;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 启动配置：config/wraith_cannon-startup.toml。
 *
 * <p>近战伤害、攻速、耐久必须在物品注册时就写入物品属性，而普通的 COMMON 配置此时还没加载。
 * NeoForge 的 STARTUP 类型在注册时立即读取，正好用于这类数值。修改后需要重启游戏；
 * 它不会从服务器同步，联机时请让服务端与客户端保持一致，否则提示栏 / 耐久条的显示会与实际不符。
 */
public class StartupConfig {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    public static final ModConfigSpec.DoubleValue ATTACK_DAMAGE = BUILDER
            .comment("近战附加伤害。物品提示栏显示的总伤害 = 1（空手基础）+ 此值，默认 9（显示 10）。修改后需重启游戏")
            .translation("wraith_cannon.configuration.weapon.attackDamage")
            .defineInRange("weapon.attackDamage", 9.0D, 0.0D, 1000.0D);

    public static final ModConfigSpec.DoubleValue ATTACK_SPEED = BUILDER
            .comment("近战攻击速度（每秒几刀）。默认 1.0，与原版斧头一致；原版剑 1.6，匕首类更高。修改后需重启游戏")
            .translation("wraith_cannon.configuration.weapon.attackSpeed")
            .defineInRange("weapon.attackSpeed", 1.0D, 0.1D, 20.0D);

    public static final ModConfigSpec.IntValue DURABILITY = BUILDER
            .comment("耐久（可喷射次数 / 近战次数的总额度）。默认 1500。修改后需重启游戏，已有的物品会按新上限显示")
            .translation("wraith_cannon.configuration.weapon.durability")
            .defineInRange("weapon.durability", 1500, 1, 100000);

    public static final ModConfigSpec SPEC = BUILDER.build();

    private StartupConfig() {
    }
}
