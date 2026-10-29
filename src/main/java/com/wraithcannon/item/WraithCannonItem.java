package com.wraithcannon.item;

import com.wraithcannon.Config;
import com.wraithcannon.ModSounds;
import com.wraithcannon.client.ClientAnimState;
import com.wraithcannon.entity.ChaosMistCloud;
import com.wraithcannon.entity.ModEntities;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import com.wraithcannon.network.RecoilPayload;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.SingletonGeoAnimatable;
import software.bernie.geckolib.animatable.client.GeoRenderProvider;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimationState;
import software.bernie.geckolib.animation.PlayState;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.List;
import java.util.function.Consumer;

/**
 * 冤魂炮。
 *
 * <p>两种攻击模式：
 * <ul>
 *   <li><b>模式一（近战）</b>：左键挥击。伤害走原版属性管线，动画由
 *       {@link #onEntitySwing} 在<b>客户端</b>立即触发 {@code swing_melee}（不等服务端回包，
 *       所以点击即砸，没有网络延迟）。</li>
 *   <li><b>模式二（喷雾）</b>：右键长按蓄力，松开喷射。蓄力时播
 *       {@code charge_loop}，按住满 {@code Config.CHARGE_TICKS} 即为蓄满，
 *       松开时播 {@code fire} 并生成 {@link ChaosMistCloud}。
 *       「是否蓄满」只看按住了多少 tick，不在物品栈上存任何状态
 *       （改物品栈组件会让客户端重放「换装」动画，造成画面闪一下）。</li>
 * </ul>
 *
 * <p>蓄力 / 发射 / 取消的动画由服务端触发（{@code triggerAnim}），保证联机时别人也能看到。
 * 前提是构造器里调用了 {@code SingletonGeoAnimatable.registerSyncedAnimatable(this)}。
 * 挥击动画例外：每个客户端（包括旁观者）在 {@code swing()} 时各自本地触发，
 * 因为服务端回包至少晚一个网络往返。
 */
public class WraithCannonItem extends Item implements GeoItem {

    // ---- 控制器名 ----
    /**
     * 只用一个控制器：predicate 按玩家状态选「待机 / 蓄力定格」，
     * 砸击、开火是一次性触发动画，播完自动交还给 predicate。
     */
    private static final String CONTROLLER_MAIN = "main";

    // ---- 动画定义（键名必须是 .animation.json 里的完整名字） ----
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.wraith_cannon.idle");
    private static final RawAnimation SWING_SMASH = RawAnimation.begin().thenPlay("animation.wraith_cannon.swing_melee");
    /**
     * 蓄力姿势：{@code charge_hold} 是一段 600 秒的长动画（上肩 0.45 秒，随后定格并带轻微脉动），
     * 机制与待机完全相同——predicate 里 {@code thenLoop}，控制器始终处于 RUNNING 状态。
     *
     * <p>不用 {@code thenPlayAndHold}：定格会让控制器进入 PAUSED 状态，
     * 与「predicate 每帧重新设置动画」的用法不兼容。长循环则始终是 RUNNING，
     * 也不会遇到循环重启（600 秒内玩家早已松手）。
     */
    private static final RawAnimation CHARGE_HOLD = RawAnimation.begin()
            .thenLoop("animation.wraith_cannon.charge_hold");
    private static final RawAnimation FIRE = RawAnimation.begin().thenPlay("animation.wraith_cannon.fire");

    // ---- 触发器名（只有「一次性」动画走触发器；待机 / 蓄力由 predicate 按玩家状态选择） ----
    private static final String TRIGGER_SWING = "swing";
    private static final String TRIGGER_FIRE = "fire";

    /** 持有缓慢效果的单次时长与续期阈值（tick）。 */
    private static final int SLOW_DURATION_TICKS = 16;
    private static final int SLOW_REFRESH_BELOW_TICKS = 8;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public WraithCannonItem(Properties properties) {
        super(properties);
        // 物品是「单例 animatable」：全局只有一个 Item 对象，
        // 必须注册成可同步的，否则服务端 triggerAnim 不会发给客户端。
        SingletonGeoAnimatable.registerSyncedAnimatable(this);
    }

    // ============================================================
    // GeckoLib 样板
    // ============================================================

    @Override
    public void createGeoRenderer(Consumer<GeoRenderProvider> consumer) {
        consumer.accept(new GeoRenderProvider() {
            private WraithCannonRenderer renderer;

            @Override
            public net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer getGeoItemRenderer() {
                // 延迟实例化：构造时机太早会拿到未初始化的 Minecraft 单例
                if (this.renderer == null) {
                    this.renderer = new WraithCannonRenderer();
                }
                return this.renderer;
            }
        });
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // 转场 0 tick：转场期间新动画不推进，左键「点了不立刻砸」可能就来自这段延迟。
        // 待机 / 蓄力由 predicate 根据「当前客户端玩家是否在按住右键」决定，不依赖服务端回包，
        // 所以取消蓄力会立刻回待机，不会卡在蓄力姿势；砸击和开火是一次性触发动画，播完自动回 predicate。
        controllers.add(new AnimationController<>(this, CONTROLLER_MAIN, 0, this::animationPredicate)
                .triggerableAnim(TRIGGER_SWING, SWING_SMASH)
                .triggerableAnim(TRIGGER_FIRE, FIRE));
    }

    private PlayState animationPredicate(AnimationState<WraithCannonItem> state) {
        return state.setAndContinue(ClientAnimState.isChargingCannon() ? CHARGE_HOLD : IDLE);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return this.cache;
    }

    // ============================================================
    // 模式一：近战
    // ============================================================

    /**
     * 左键挥击时触发砸击动画。
     *
     * <p>这个方法在挖方块时也会被调用，所以只能当「有没有挥手」用，
     * 不能当命中判定；真正的伤害走原版 {@code Player#attack} 的属性管线。
     *
     * @return false 表示不拦截原版手臂摆动
     */
    @Override
    public boolean onEntitySwing(ItemStack stack, LivingEntity entity, InteractionHand hand) {
        // 只在客户端触发：客户端点击当帧就播；别的玩家的挥击经 ClientboundAnimatePacket
        // 到达他们客户端后也会走到这里。
        // 已经在挥臂中（连续挖方块每 tick 都会调 swing）不重复触发，否则动画永远停在第一帧。
        // 蓄力中左键不触发砸击，免得打断扛肩姿势。
        // 右键开门 / 拉杆 / 放置方块也会触发手臂摆动并走到这里，但那不是砸击。
        // 本地玩家用「攻击键是否按住」区分；其他玩家的挥击包没有这个信息，仍按原逻辑播动画。
        if (hand == InteractionHand.MAIN_HAND
                && entity.level().isClientSide()
                && !entity.swinging
                && !entity.isUsingItem()
                && ClientAnimState.isRealSwing(entity)) {
            triggerAnim(entity, GeoItem.getId(stack), CONTROLLER_MAIN, TRIGGER_SWING);
            // 挥空声只在本机播（本地玩家），避免右键交互时服务端广播砸击声
            if (ClientAnimState.isLocalPlayer(entity)) {
                entity.level().playLocalSound(entity.getX(), entity.getY(), entity.getZ(),
                        ModSounds.SWING_WHOOSH.get(), net.minecraft.sounds.SoundSource.PLAYERS,
                        1.0F, 0.95F + entity.getRandom().nextFloat() * 0.1F, false);
            }
        }
        return false;
    }

    // ============================================================
    // 模式二：蓄力喷雾
    // ============================================================

    /** 右键开始，进入蓄力。 */
    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        // 非主手不给用，避免双手各一把时状态打架
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResultHolder.pass(stack);
        }
        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.pass(stack);
        }

        player.startUsingItem(hand);

        // 把侧面握把扛上肩：衣物摩擦 + 轻微金属碰撞
        ModSounds.play(level, player, ModSounds.HANDLE_RUSTLE, 1.0F, 0.95F + level.random.nextFloat() * 0.1F);
        ModSounds.play(level, player, ModSounds.HANDLE_CLINK, 1.0F, 0.9F + level.random.nextFloat() * 0.2F);

        return InteractionResultHolder.consume(stack);
    }

    /**
     * 蓄力上限。
     *
     * <p>这里故意返回一个很大的值，让「按住右键」不会因为时间耗尽而自动结束。
     * 真正的蓄力判定在 {@link #onUseTick} 里做，玩家必须松手才开火
     * （走 {@link #releaseUsing}）。如果不这么做，时间一到会触发
     * {@code finishUsingItem}，而那个方法默认什么都不做，右键就「没反应」。
     */
    @Override
    public int getUseDuration(ItemStack stack, LivingEntity entity) {
        return 72000;
    }

    @Override
    public UseAnim getUseAnimation(ItemStack stack) {
        // NONE：不使用原版「拉弓/吃东西」的手臂动作，姿势交给骨骼动画
        return UseAnim.NONE;
    }

    @Override
    public boolean useOnRelease(ItemStack stack) {
        // 告诉原版「松开时要调用 releaseUsing」，而不是把使用时间耗完
        return true;
    }

    /**
     * 蓄力过程中的每 tick 回调（服务端和客户端都会调，这里只在服务端做事）。
     *
     * <p>已按住时间 = {@code getUseDuration - remainingUseDuration}，
     * 恰好等于 {@code Config.CHARGE_TICKS} 的那一 tick 给一次「蓄满」提示。
     */
    @Override
    public void onUseTick(Level level, LivingEntity living, ItemStack stack, int remainingUseDuration) {
        if (!(living instanceof Player player) || !(level instanceof ServerLevel serverLevel)) {
            return;
        }

        int elapsed = getUseDuration(stack, living) - remainingUseDuration;
        int chargeLimit = Config.CHARGE_TICKS.get();

        if (elapsed == chargeLimit) {
            Vec3 mouth = getEffectsMuzzle(player);
            serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    mouth.x, mouth.y, mouth.z, 6, 0.15, 0.15, 0.15, 0.01);
            ModSounds.play(level, player, ModSounds.CHARGE_READY, 1.0F, 1.0F);
        } else if (elapsed < chargeLimit) {
            if (elapsed % 4 == 0) {
                Vec3 mouth = getEffectsMuzzle(player);
                serverLevel.sendParticles(ParticleTypes.SMOKE,
                        mouth.x, mouth.y, mouth.z, 2, 0.1, 0.1, 0.1, 0.005);
            }
            // 能量收集：每 6 tick 一次轻脉冲，音调随蓄力进度从 0.6 升到 1.5
            if (elapsed % 6 == 0) {
                float progress = (float) elapsed / chargeLimit;
                ModSounds.play(level, player, ModSounds.CHARGE_PULSE, 0.6F + 0.4F * progress, 0.6F + 0.9F * progress);
            }
        }
    }

    /**
     * 松开右键。按住不足 {@code Config.CHARGE_TICKS} 等于取消，不消耗耐久、不进冷却。
     */
    @Override
    public void releaseUsing(ItemStack stack, Level level, LivingEntity living, int timeLeft) {
        if (!(living instanceof Player player)) {
            return;
        }
        int elapsed = getUseDuration(stack, living) - timeLeft;
        boolean charged = elapsed >= Config.CHARGE_TICKS.get();

        // 客户端（当前客户端玩家松手的那一帧）：立刻播开火动画，不等服务端回包，
        // 否则会先闪回待机姿势再跳到开火动画。没蓄满则什么都不播，predicate 自然回待机。
        if (level.isClientSide()) {
            if (charged) {
                triggerAnim(player, GeoItem.getId(stack), CONTROLLER_MAIN, TRIGGER_FIRE);
                ClientAnimState.kickMuzzle(player);
                // 客户端也要加短暂冷却，防止开火动画播放期间再次按右键导致闪烁
                // 冷却时长至少要覆盖开火动画长度（约 1 秒 = 20 tick）
                // 取 min：配置的冷却比 20 短时不要把客户端锁得比服务端还久
                int lock = Math.min(20, Config.MIST_COOLDOWN_TICKS.get());
                if (lock > 0) {
                    player.getCooldowns().addCooldown(this, lock);
                }
            }
            return;
        }
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        if (!charged) {
            // 没蓄满：取消，不消耗、不进冷却；握把收回
            ModSounds.play(level, player, ModSounds.HANDLE_RUSTLE, 0.8F, 1.0F);
            ModSounds.play(level, player, ModSounds.HANDLE_LATCH, 1.0F, 1.0F);
            return;
        }

        fireMist(serverLevel, player);

        // 开火动画的后半段（约 0.4 秒后）握把收回
        Vec3 foldAt = player.position();
        serverLevel.getServer().tell(new TickTask(serverLevel.getServer().getTickCount() + 8, () -> {
            ModSounds.play(serverLevel, foldAt, ModSounds.HANDLE_RUSTLE, 0.8F, 0.9F);
            ModSounds.play(serverLevel, foldAt, ModSounds.HANDLE_LATCH, 1.0F, 0.9F);
        }));

        player.getCooldowns().addCooldown(this, Config.MIST_COOLDOWN_TICKS.get());
        stack.hurtAndBreak(1, player, EquipmentSlot.MAINHAND);
    }

    /** 实际喷射：生成浓雾 + 后坐力。 */
    private void fireMist(ServerLevel level, Player player) {
        Vec3 look = player.getLookAngle();
        Vec3 mouth = getMuzzlePosition(player);

        ChaosMistCloud cloud = new ChaosMistCloud(ModEntities.CHAOS_MIST.get(), level);
        cloud.setOwnerUuid(player.getUUID());
        cloud.moveTo(mouth.x, mouth.y, mouth.z, player.getYRot(), player.getXRot());
        // 终点：雾团前沿距眼睛 MIST_RANGE 格。起点已在眼前 1.6 格，再扣掉雾半径。
        float radius = Config.MIST_CLOUD_RADIUS.get().floatValue();
        double reach = computeReach(level, player.getEyePosition(), look, Config.MIST_RANGE.get());
        // 雾团前沿相对停点的偏移：wallOverlap 为正则前伸，为负则后撤。
        // 注意：这一项对所有射击都生效，不只限于被墙挡住的情况（未被挡住时也加在满射程上）
        float travel = (float) Math.max(0.5, reach - 1.6 - radius + Config.WALL_OVERLAP.get());
        cloud.configure(
                radius,
                Config.MIST_CLOUD_SECONDS.get() * 20,
                travel,
                Config.MIST_DAMAGE.get().floatValue(),
                Config.MIST_GRAZE_DAMAGE.get().floatValue(),
                Config.MIST_TICK_DAMAGE.get().floatValue(),
                Config.MIST_HEAD_RATIO.get().floatValue(),
                Config.MIST_GRAZE_RATIO.get().floatValue(),
                Config.MIST_GRAZE_FORM_DISTANCE.get().floatValue(),
                look);
        level.addFreshEntity(cloud);

        applyRecoil(player, look);

        ModSounds.play(level, player, ModSounds.BLAST, 1.0F, 1.0F);
        ModSounds.play(level, player, ModSounds.BLAST_HISS, 1.0F, 1.0F);
        // 这里不放原版 EXPLOSION 粒子，它是白色烟团，
        // 它可能造成浓雾中间的白边 / 白斑。
    }

    /** 陆地垂直后坐达到最大值的俯仰角（度）；门槛由配置决定，且保证至少比门槛大 5 度。 */
    private static final double LAND_VERTICAL_FULL_DEG = 85.0;

    /**
     * 基于俯仰角的后坐力。
     *
     * <p>MC 的俯仰角约定：{@code xRot} 向<b>下</b>看为正（+90 = 看脚下），向上看为负（-90 = 看天）。
     * 后坐力方向与枪口方向相反：向下射 → 垂直分量向上；向上射 → 向下。
     *
     * <p><b>陆地</b>：垂直后坐只在接近垂直射击时出现——|俯仰角| 小于配置门槛（mist.verticalRecoilPitchThreshold，默认 70 度）为 0，
     * 之后按 smoothstep 平滑增强，约 {@value #LAND_VERTICAL_FULL_DEG} 度达到最大。
     * 原因：常见的斜向射击不应频繁把玩家弹离地面；目前的做法是只在接近垂直时才给垂直冲量。
     * <br><b>水中</b>：没有门槛，垂直分量按 {@code sin(俯仰角)} 线性变化（水里本来就没有「站稳」的概念，
     * 游泳时靠它调整上浮 / 下潜），再整体乘 {@code mist.waterRecoilMultiplier}。
     *
     * <p>水平分量沿视线水平投影的反方向；垂直越强水平越弱（最多 -70%）。
     *
     * <p>冲量不在服务端 {@code push}，而是发给该玩家的客户端叠加到当前速度上，原因见 {@link RecoilPayload}。
     */
    private static void applyRecoil(Player player, Vec3 look) {
        if (!(player instanceof ServerPlayer serverPlayer)) {
            return;
        }
        double recoil = Config.MIST_RECOIL.get();
        if (recoil <= 0.0) {
            return;
        }
        double pitch = player.getXRot();
        double sinPitch = Math.sin(Math.toRadians(pitch));   // +1 看脚下，-1 看天
        boolean inWater = player.isInWater();

        // 垂直曲线，范围 [-1, 1]：正 = 向上推
        double shape;
        if (inWater) {
            shape = sinPitch;
        } else {
            double start = Config.MIST_VERTICAL_RECOIL_PITCH_THRESHOLD.get();
            double full = Math.max(LAND_VERTICAL_FULL_DEG, start + 5.0);
            double t = Mth.clamp((Math.abs(pitch) - start) / (full - start), 0.0, 1.0);
            shape = Math.signum(sinPitch) * t * t * (3.0 - 2.0 * t);
        }

        double vertical = recoil * Config.MIST_VERTICAL_RECOIL_MULTIPLIER.get() * shape;
        double horizontal = recoil * (1.0 - Math.abs(shape) * 0.7);
        double hx = 0.0;
        double hz = 0.0;
        double hLen = Math.sqrt(look.x * look.x + look.z * look.z);
        if (hLen > 1.0E-4) {
            hx = -look.x / hLen * horizontal;
            hz = -look.z / hLen * horizontal;
        }

        if (inWater) {
            double water = Config.MIST_WATER_RECOIL_MULTIPLIER.get();
            hx *= water;
            hz *= water;
            vertical *= water;
        }

        PacketDistributor.sendToPlayer(serverPlayer, new RecoilPayload(hx, vertical, hz));
    }

    /**
     * 地形衰减：沿视线前进，累计射线「在实心方块里实际走过的长度」。
     * 每走过 1 格长度扣 {@code pierceRangeLoss} 射程；累计超过 {@code pierceMaxBlocks} 格就在当前位置停下。
     * 按长度而不是按方块个数算：斜着擦过方块边角只算那一小段，不会多算一整格。
     * 返回从眼睛算起的有效射程。每次开火只算一次，每个方块格只查一次方块状态。
     */
    private static double computeReach(ServerLevel level, Vec3 eye, Vec3 look, double reach) {
        final double step = 0.2;
        final double maxInside = Config.PIERCE_BLOCKS.get();
        final double loss = Config.PIERCE_LOSS.get();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int lastX = Integer.MIN_VALUE, lastY = 0, lastZ = 0;
        boolean solid = false;
        double inside = 0.0;
        double effective = reach;
        for (double d = step * 0.5; d < effective; d += step) {
            double x = eye.x + look.x * d, y = eye.y + look.y * d, z = eye.z + look.z * d;
            int bx = Mth.floor(x), by = Mth.floor(y), bz = Mth.floor(z);
            if (bx != lastX || by != lastY || bz != lastZ) {
                lastX = bx;
                lastY = by;
                lastZ = bz;
                cursor.set(bx, by, bz);
                // 未加载的区块：直接在此停下。ServerLevel#getBlockState 碰到未加载区块会同步加载 / 生成它，
                // 在主线程上就是一次明显的卡顿（模组多、区块加载慢的环境尤其明显）。
                if (!level.hasChunkAt(cursor)) {
                    return d;
                }
                solid = !level.getBlockState(cursor).getCollisionShape(level, cursor).isEmpty();
            }
            if (!solid) {
                continue;
            }
            inside += step;
            if (inside > maxInside) {
                return d;
            }
            effective -= loss * step;
        }
        return Math.max(effective, 0.0);
    }

    /** 炮口的大致世界坐标：眼睛前方 1.6 格，略往下。 */
    public static Vec3 getMuzzlePosition(Player player) {
        return player.getEyePosition().add(player.getLookAngle().scale(1.6)).subtract(0, 0.15, 0);
    }

    /**
     * 提示粒子的位置：扛在持炮手一侧肩上的炮口，而不是准星。
     * 在视线前 0.7 格（位于炮身后方，靠近玩家）的基础上向持炮手一侧偏 0.3 格、向下 0.05 格
     * （与第一人称手部偏移的方向一致）。
     */
    public static Vec3 getEffectsMuzzle(Player player) {
        Vec3 look = player.getLookAngle();
        double yaw = Math.toRadians(player.getYRot());
        Vec3 right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
        Vec3 up = right.cross(look).normalize();
        double side = player.getMainArm() == net.minecraft.world.entity.HumanoidArm.RIGHT ? 1.0 : -1.0;
        return player.getEyePosition()
                .add(look.scale(0.7))
                .add(right.scale(0.3 * side))
                .subtract(up.scale(0.05));
    }

    // ============================================================
    // 主手持有即缓慢
    // ============================================================

    @Override
    public void inventoryTick(ItemStack stack, Level level, Entity entity, int slotId, boolean isSelected) {
        if (level.isClientSide || !(entity instanceof Player player)) {
            return;
        }
        // 提前分配 GeckoLib 的动画实例 id。
        // 若等到第一次右键才分配，物品栈组件会在蓄力那一刻变化：客户端既会播「换装」动画，
        // 渲染器也会从「无 id」的动画状态切到「有 id」的新状态——推测这是首次长按右键时出现一下闪烁的原因。
        if (level instanceof ServerLevel serverLevel) {
            GeoItem.getOrAssignId(stack, serverLevel);
        }
        // 只在主手生效。isSelected = 快捷栏当前选中的槽位 = 主手；
        // 比每 tick 调 getMainHandItem().is() 少一次查找，背包里其它位置的冤魂炮直接跳过。
        if (!isSelected) {
            return;
        }

        // 不做任何操作时，极少见地、极微弱地传来一声冤魂低语（平均约 50 秒一次）
        if (!player.isUsingItem() && level.random.nextInt(1000) == 0) {
            ModSounds.play(level, player, ModSounds.IDLE_WHISPER, 0.5F, 0.9F + level.random.nextFloat() * 0.2F);
        }

        // 缓慢效果：只在「没有 / 快到期」时续期。
        // 每 tick 重新添加会让服务端每 tick 发效果更新包并反复重加移速修饰符，
        // 所以效果设 16 tick，剩余 < 8 tick 才续，约每 9 tick 一次；离开主手后最多拖 0.8 秒消失。
        // MobEffectInstance 的 visible 参数为 true 才显示粒子，因此「隐藏粒子」要传 !hide。
        if (!Config.SLOW_ENABLED.get()) {
            return;
        }
        MobEffectInstance current = player.getEffect(MobEffects.MOVEMENT_SLOWDOWN);
        if (current == null || current.getDuration() < SLOW_REFRESH_BELOW_TICKS) {
            boolean hide = Config.SLOW_HIDES_PARTICLES.get();
            int amplifier = Config.SLOW_AMPLIFIER.get();
            player.addEffect(new MobEffectInstance(
                    MobEffects.MOVEMENT_SLOWDOWN, SLOW_DURATION_TICKS, amplifier, false, !hide, true));
        }
    }

    /**
     * 同一种物品只是组件（耐久、GeckoLib id 等）变了，不要播放「换装」下沉动画。
     * 默认实现是 {@code !oldStack.equals(newStack)}，每次开火扣耐久都会触发一次。
     */
    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return !ItemStack.isSameItem(oldStack, newStack);
    }

    // ============================================================
    // 提示文本
    // ============================================================

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.wraith_cannon.mode_melee")
                .withStyle(ChatFormatting.DARK_GRAY));
        tooltip.add(Component.translatable("tooltip.wraith_cannon.mode_mist")
                .withStyle(ChatFormatting.DARK_GRAY));
    }

    /** 近战命中：沉重的砸地闷响（服务端，附近所有人都能听到）。 */
    @Override
    public boolean hurtEnemy(ItemStack stack, LivingEntity target, LivingEntity attacker) {
        ModSounds.play(attacker.level(), target, ModSounds.MELEE_IMPACT, 1.0F, 0.9F + attacker.getRandom().nextFloat() * 0.2F);
        // 近战命中后的额外冷却（默认 0 = 不限制）：期间无法右键蓄力。
        // 已处于更长的冷却（比如刚喷完）时不覆盖，addCooldown 会直接替换原有冷却。
        int cd = Config.MELEE_COOLDOWN_TICKS.get();
        if (cd > 0 && attacker instanceof Player player && !player.level().isClientSide()
                && !player.getCooldowns().isOnCooldown(this)) {
            player.getCooldowns().addCooldown(this, cd);
        }
        return true;
    }

    // ============================================================
    // 左键不能破坏方块（和原版剑一样）
    // ============================================================

    /**
     * 原版 Item 的这个方法就是「手持此物时能否开始破坏方块」，
     * 按其文档，近战武器宜重写成 false（除非它同时是工具）。
     * 返回 false 后，左键点方块不会出裂纹、不会破坏，创造 / 生存都一样。
     */
    @Override
    public boolean canAttackBlock(BlockState state, Level level, BlockPos pos, Player player) {
        return false;
    }

    // 挖方块不扣耐久

    @Override
    public boolean mineBlock(ItemStack stack, Level level, BlockState state, BlockPos pos, LivingEntity miningEntity) {
        return true;
    }
}

