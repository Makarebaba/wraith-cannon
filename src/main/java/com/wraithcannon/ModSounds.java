package com.wraithcannon;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

/**
 * 音效事件。
 *
 * <p>所有事件都挂在本模组的命名空间下，具体播什么在
 * {@code assets/wraith_cannon/sounds.json} 里配置：大多数条目用 {@code "type": "event"}
 * 直接引用原版音效事件并调整音量 / 音调（原版自己的 {@code entity.parrot.imitate.vex}
 * 就是这么写的），只有「浓雾消散」是自制的 ogg。
 * 想换声音只改 sounds.json / 替换 ogg，不用改 Java。
 *
 * <p>事件清单与触发时机见仓库根目录之外提供的《音效制作清单》。
 */
public class ModSounds {

    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(BuiltInRegistries.SOUND_EVENT, WraithCannon.MOD_ID);

    // ---- 握把 / 扛起 ----
    /** 衣物摩擦（皮革）。 */
    public static final Supplier<SoundEvent> HANDLE_RUSTLE = register("handle_rustle");
    /** 轻微金属碰撞（锁链）。 */
    public static final Supplier<SoundEvent> HANDLE_CLINK = register("handle_clink");
    /** 握把收起的锁扣声。 */
    public static final Supplier<SoundEvent> HANDLE_LATCH = register("handle_latch");

    // ---- 蓄力 ----
    /** 蓄力中的能量收集脉冲（每几 tick 一次，音调逐渐升高）。 */
    public static final Supplier<SoundEvent> CHARGE_PULSE = register("charge_pulse");
    /** 蓄满提示。 */
    public static final Supplier<SoundEvent> CHARGE_READY = register("charge_ready");

    // ---- 近战 ----
    public static final Supplier<SoundEvent> SWING_WHOOSH = register("swing_whoosh");
    public static final Supplier<SoundEvent> MELEE_IMPACT = register("melee_impact");

    // ---- 发射 ----
    public static final Supplier<SoundEvent> BLAST = register("blast");
    public static final Supplier<SoundEvent> BLAST_HISS = register("blast_hiss");

    // ---- 浓雾 ----
    /** 雾团飞行时微弱的划空声。 */
    public static final Supplier<SoundEvent> MIST_FLOW = register("mist_flow");
    /** 雾团到位后逐渐消散（自制 ogg）。 */
    public static final Supplier<SoundEvent> MIST_DISSIPATE = register("mist_dissipate");

    // ---- 冤魂低语（待机 / 浓雾中，少见随机） ----
    public static final Supplier<SoundEvent> IDLE_WHISPER = register("idle_whisper");
    public static final Supplier<SoundEvent> MIST_WHISPER = register("mist_whisper");

    private static Supplier<SoundEvent> register(String name) {
        return SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(
                ResourceLocation.fromNamespaceAndPath(WraithCannon.MOD_ID, name)));
    }

    // ---- 服务端播放小工具（player 传 null = 附近所有人都能听到，包括触发者） ----

    public static void play(Level level, Entity at, Supplier<SoundEvent> sound, float volume, float pitch) {
        if (!level.isClientSide()) {
            level.playSound(null, at.getX(), at.getY(), at.getZ(), sound.get(), SoundSource.PLAYERS, volume, pitch);
        }
    }

    public static void play(Level level, Vec3 pos, Supplier<SoundEvent> sound, float volume, float pitch) {
        if (!level.isClientSide()) {
            level.playSound(null, pos.x, pos.y, pos.z, sound.get(), SoundSource.PLAYERS, volume, pitch);
        }
    }

    private ModSounds() {
    }
}
