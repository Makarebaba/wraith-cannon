package com.wraithcannon;

import com.wraithcannon.entity.ModEntities;
import com.wraithcannon.item.ModItems;
import com.wraithcannon.particle.ModParticles;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 冤魂炮主类。
 *
 * <p>视觉资源（geo 模型 / 贴图 / 动画）都在 assets 下，替换对应的
 * .geo.json / .animation.json / .png 即可更换外观，Java 代码无需改动。
 */
@Mod(WraithCannon.MOD_ID)
public class WraithCannon {

    public static final String MOD_ID = "wraith_cannon";
    public static final Logger LOGGER = LoggerFactory.getLogger("WraithCannon");

    public WraithCannon(IEventBus modEventBus, ModContainer modContainer) {
        // 必须最先注册：物品属性（伤害 / 攻速 / 耐久）在 ModItems 类加载时就要读取启动配置
        modContainer.registerConfig(ModConfig.Type.STARTUP, StartupConfig.SPEC);
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.SPEC);

        ModItems.ITEMS.register(modEventBus);
        ModItems.TABS.register(modEventBus);
        ModEntities.ENTITY_TYPES.register(modEventBus);
        ModParticles.PARTICLE_TYPES.register(modEventBus);
        ModSounds.SOUND_EVENTS.register(modEventBus);
    }
}
