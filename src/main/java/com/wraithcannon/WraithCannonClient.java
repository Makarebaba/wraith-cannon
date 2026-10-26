package com.wraithcannon;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.gui.ConfigurationScreen;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;

/**
 * 仅客户端入口：在「模组列表 → 配置」里提供 NeoForge 自带的配置界面。
 * 单独做成 dist = CLIENT 的 @Mod 类，专用服务端不会加载到任何客户端类。
 * Configured 等第三方配置模组会自动识别配置文件，不依赖这里。
 */
@Mod(value = WraithCannon.MOD_ID, dist = Dist.CLIENT)
public class WraithCannonClient {

    public WraithCannonClient(ModContainer container) {
        container.registerExtensionPoint(IConfigScreenFactory.class, ConfigurationScreen::new);
    }
}
