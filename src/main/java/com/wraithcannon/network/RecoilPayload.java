package com.wraithcannon.network;

import com.wraithcannon.WraithCannon;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * 后坐力冲量（服务端 → 客户端）。
 *
 * <p><b>为什么不在服务端 {@code push} + {@code hurtMarked}</b>：玩家的移动由客户端主导，
 * 服务端上 {@code ServerPlayer} 的 deltaMovement 与客户端的实际速度可能不一致。{@code hurtMarked}
 * 会把「服务端那份速度」整体发给客户端，<b>覆盖</b>客户端当前速度，实测表现为水中游动 / 下沉 /
 * 起跳中的速度被清掉。
 * 目前的做法：服务端只算出冲量，发给该玩家的客户端，客户端在<b>自己当前速度上叠加</b>。
 *
 * <p>字段是「要叠加的速度增量」（格 / tick）。
 */
public record RecoilPayload(double x, double y, double z) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<RecoilPayload> TYPE =
            new CustomPacketPayload.Type<>(ResourceLocation.fromNamespaceAndPath(WraithCannon.MOD_ID, "recoil"));

    public static final StreamCodec<ByteBuf, RecoilPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.DOUBLE, RecoilPayload::x,
            ByteBufCodecs.DOUBLE, RecoilPayload::y,
            ByteBufCodecs.DOUBLE, RecoilPayload::z,
            RecoilPayload::new);

    @Override
    public CustomPacketPayload.Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    /** 客户端处理：默认在主线程执行。只用到公共类 Player，专用服务端加载本类是安全的。 */
    public static void handleOnClient(RecoilPayload payload, IPayloadContext context) {
        Player player = context.player();
        player.push(payload.x(), payload.y(), payload.z());
    }

    @EventBusSubscriber(modid = WraithCannon.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
    public static class Registration {
        @SubscribeEvent
        public static void register(RegisterPayloadHandlersEvent event) {
            PayloadRegistrar registrar = event.registrar("1");
            registrar.playToClient(TYPE, STREAM_CODEC, RecoilPayload::handleOnClient);
        }
    }
}
