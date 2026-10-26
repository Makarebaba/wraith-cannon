package com.wraithcannon.item;

import com.wraithcannon.WraithCannon;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.state.BoneSnapshot;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.GeoModel;

/**
 * 冤魂炮的 GeckoLib 模型绑定。
 *
 * <p>资源路径是 1.21.1 / GeckoLib 4.9.x 的规则（参见 GeckoLib 的
 * {@code GeckoLibCache} 与 {@code DefaultedGeoModel}）：
 * <ul>
 *   <li>模型：{@code assets/wraith_cannon/geo/**.geo.json}</li>
 *   <li>动画：{@code assets/wraith_cannon/animations/**.animation.json}</li>
 *   <li>贴图：{@code assets/wraith_cannon/textures/**.png}</li>
 * </ul>
 * 注意不是 wiki v5 文档里的 {@code geckolib/models/}，那是 1.21.2+ 的路径。
 */
public class WraithCannonModel extends GeoModel<WraithCannonItem> {

    private static final ResourceLocation MODEL =
            ResourceLocation.fromNamespaceAndPath(WraithCannon.MOD_ID, "geo/item/wraith_cannon.geo.json");
    private static final ResourceLocation TEXTURE =
            ResourceLocation.fromNamespaceAndPath(WraithCannon.MOD_ID, "textures/item/wraith_cannon.png");
    private static final ResourceLocation ANIMATION =
            ResourceLocation.fromNamespaceAndPath(WraithCannon.MOD_ID, "animations/wraith_cannon.animation.json");

    @Override
    public ResourceLocation getModelResource(WraithCannonItem animatable) {
        return MODEL;
    }

    @Override
    public ResourceLocation getTextureResource(WraithCannonItem animatable) {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(WraithCannonItem animatable) {
        return ANIMATION;
    }

    /**
     * 只有第一人称播骨骼动画；第三人称、地面掉落、展示框、物品栏图标一律静态。
     *
     * <p>做法：骨骼动画照常算完（同一个模型对象被所有视角共用，算与不算都会留下上一次的姿势），
     * 然后在这个回调里把所有骨骼强制写回「初始快照」。
     * 回调位于 {@code GeoModel#handleAnimations} 末尾、渲染之前（见 GeckoLib 1.21.1 源码）。
     * 渲染视角由 {@code GeoItemRenderer} 写入 {@link DataTickets#ITEM_RENDER_PERSPECTIVE}。
     */
    @Override
    public void setCustomAnimations(WraithCannonItem animatable, long instanceId,
                                    AnimationState<WraithCannonItem> animationState) {
        ItemDisplayContext ctx = animationState.getData(DataTickets.ITEM_RENDER_PERSPECTIVE);
        if (ctx == ItemDisplayContext.FIRST_PERSON_RIGHT_HAND || ctx == ItemDisplayContext.FIRST_PERSON_LEFT_HAND) {
            return;
        }
        for (GeoBone bone : getAnimationProcessor().getRegisteredBones()) {
            BoneSnapshot init = bone.getInitialSnapshot();
            bone.updateRotation(init.getRotX(), init.getRotY(), init.getRotZ());
            bone.updatePosition(init.getOffsetX(), init.getOffsetY(), init.getOffsetZ());
            bone.updateScale(init.getScaleX(), init.getScaleY(), init.getScaleZ());
        }
    }
}

