package com.wraithcannon.item;

import software.bernie.geckolib.renderer.GeoItemRenderer;

/**
 * 冤魂炮渲染器。
 *
 * <p>必须放在 client 源集——它引用了 {@code HumanoidModel} 等纯客户端类，
 * 混进 common 会在专用服务端启动时炸 {@code NoClassDefFoundError}。
 */
public class WraithCannonRenderer extends GeoItemRenderer<WraithCannonItem> {

    public WraithCannonRenderer() {
        super(new WraithCannonModel());
    }
}

