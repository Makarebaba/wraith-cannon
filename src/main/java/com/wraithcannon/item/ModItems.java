package com.wraithcannon.item;

import com.wraithcannon.WraithCannon;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModItems {

    public static final net.neoforged.neoforge.registries.DeferredRegister<net.minecraft.world.item.CreativeModeTab> TABS =
            net.neoforged.neoforge.registries.DeferredRegister.create(
                    net.minecraft.core.registries.Registries.CREATIVE_MODE_TAB, WraithCannon.MOD_ID);

    /** 独立创造栏「冤魂炮」，只放这一件武器，方便多模组环境里管理。 */
    public static final java.util.function.Supplier<net.minecraft.world.item.CreativeModeTab> TAB =
            TABS.register("wraith_cannon", () -> net.minecraft.world.item.CreativeModeTab.builder()
                    .title(net.minecraft.network.chat.Component.translatable("itemGroup.wraith_cannon"))
                    .icon(() -> new net.minecraft.world.item.ItemStack(ModItems.WRAITH_CANNON.get()))
                    .displayItems((params, output) -> output.accept(ModItems.WRAITH_CANNON.get()))
                    .build());


    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(WraithCannon.MOD_ID);

    public static final DeferredItem<WraithCannonItem> WRAITH_CANNON =
            ITEMS.registerItem("wraith_cannon", WraithCannonItem::new,
                    new Item.Properties()
                            .stacksTo(1)
                            .durability(WraithCannonProperties.durability())
                            .rarity(Rarity.RARE)
                            .attributes(WraithCannonProperties.buildAttributeModifiers()));
}

