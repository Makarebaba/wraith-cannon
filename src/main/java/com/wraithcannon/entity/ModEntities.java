package com.wraithcannon.entity;

import com.wraithcannon.WraithCannon;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModEntities {

    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, WraithCannon.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<ChaosMistCloud>> CHAOS_MIST =
            ENTITY_TYPES.register("chaos_mist",
                    () -> EntityType.Builder.<ChaosMistCloud>of(ChaosMistCloud::new, MobCategory.MISC)
                            // 云雾本身没有体积，判定靠扇形射线；这里给一个小包围盒，
                            // 只影响剔除与添加实体的位置校验
                            .sized(1.0F, 1.0F)
                            // 雾团位置由客户端按 age 自己算（见 ChaosMistCloud#centerAt），不需要服务端逐 tick 同步；
                            // 追踪范围 4 区块（64 格）足够看到，更大的范围会让远处无关玩家也被迫追踪这个实体。
                            .clientTrackingRange(4)
                            .updateInterval(20)
                            .build("chaos_mist"));
}

