package com.wraithcannon.entity;

import com.wraithcannon.ModSounds;
import com.wraithcannon.particle.ModParticles;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 冤魂炮喷出的「五彩斑斓的黑」浓雾。
 *
 * <p>无模型的纯逻辑 + 粒子实体。雾团中心是 <b>age 的纯函数</b>（{@link #centerAt(int)}），
 * 服务端判定、客户端粒子都用同一个函数算；实体自己也每 tick 被挪到雾团中心，
 * 碰撞箱（F3+B）因此跟着雾走，而不是留在炮口。
 *
 * <p>时间线（20 tick = 1 秒）：
 * <ol>
 *   <li><b>喷射 0 ~ {@value #JET_TICKS} tick（1 秒）</b>：雾团从炮口沿视线冲向终点，先快后慢。</li>
 *   <li><b>聚集 {@value #JET_TICKS} tick 之后</b>：停在终点附近缓慢漂散、收缩，到寿命结束（默认 3 秒）。</li>
 * </ol>
 *
 * <p>三档伤害：
 * <ul>
 *   <li><b>直接轰中</b>（喷射阶段被雾团头部扫到）：{@code damage}，每个目标一次；</li>
 *   <li><b>刮到</b>（站在喷射轨迹的走廊里，但没被头部扫到）：{@code grazeDamage}，每个目标一次；</li>
 *   <li><b>停留浓雾</b>（聚集阶段待在雾里）：{@code tickDamage}，每 {@value #HIT_INTERVAL} tick 一次。</li>
 * </ul>
 * 结算前把目标的 {@code invulnerableTime} 清零，保证数值就是配置的数值，不被原版无敌帧吞掉。
 */
public class ChaosMistCloud extends Entity implements OwnableEntity {

    /** 喷射阶段长度：1 秒。 */
    public static final int JET_TICKS = 20;
    /** 云团停止出粒子的时间点（粒子自己还会再活 1.5~2 秒）。 */
    public static final int EMIT_END_TICKS = 30;
    /** 持续伤害判定间隔（tick）。 */
    public static final int HIT_INTERVAL = 10;

    private static final EntityDataAccessor<Float> DATA_RADIUS =
            SynchedEntityData.defineId(ChaosMistCloud.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_LIFETIME =
            SynchedEntityData.defineId(ChaosMistCloud.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Vector3f> DATA_DIR =
            SynchedEntityData.defineId(ChaosMistCloud.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Float> DATA_TRAVEL =
            SynchedEntityData.defineId(ChaosMistCloud.class, EntityDataSerializers.FLOAT);
    /** 喷射起点（炮口）。同步给客户端，不依赖「第一帧的实体位置」。 */
    private static final EntityDataAccessor<Vector3f> DATA_ORIGIN =
            SynchedEntityData.defineId(ChaosMistCloud.class, EntityDataSerializers.VECTOR3);

    @Nullable
    private UUID ownerUuid;

    // ---- 服务端参数（NBT 持久化） ----
    private float damage = 20.0F;
    private float grazeDamage = 3.0F;
    private float tickDamage = 5.0F;
    private float headRatio = 1.0F;
    private float grazeRatio = 2.0F;
    private float grazeForm = 4.0F;
    private int age;
    /** 已被「直接轰中」结算过的目标。 */
    private final Set<UUID> directHit = new HashSet<>();
    /** 已被「刮到」结算过的目标。 */
    private final Set<UUID> grazed = new HashSet<>();
    /** 目标 UUID → 上次吃停留浓雾伤害时的 age。 */
    private final Map<UUID, Integer> lastCloudHit = new HashMap<>();

    public ChaosMistCloud(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.setNoGravity(true);
        this.setInvulnerable(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(DATA_RADIUS, 1.5F);
        builder.define(DATA_LIFETIME, 60);
        builder.define(DATA_DIR, new Vector3f(0, 0, 1));
        builder.define(DATA_TRAVEL, 3.0F);
        builder.define(DATA_ORIGIN, new Vector3f());
    }

    /**
     * 由物品在喷射瞬间调用（实体此时已 moveTo 到炮口）。
     *
     * @param radius      雾团最大半径
     * @param travel      起点到终点的距离（格）
     * @param damage      头部直接轰中
     * @param grazeDamage 轨迹刮到
     * @param tickDamage  停留浓雾每 10 tick
     * @param headRatio   头部判定半径 / 雾团半径
     * @param grazeRatio  刮到走廊半径 / 雾团半径（0 = 关闭）
     * @param grazeForm   刮到走廊成形距离（格）
     */
    public void configure(float radius, int lifetimeTicks, float travel,
                          float damage, float grazeDamage, float tickDamage,
                          float headRatio, float grazeRatio, float grazeForm, Vec3 direction) {
        this.entityData.set(DATA_RADIUS, radius);
        this.entityData.set(DATA_LIFETIME, Math.max(lifetimeTicks, JET_TICKS + 1));
        this.entityData.set(DATA_TRAVEL, travel);
        Vec3 dir = direction.lengthSqr() < 1.0E-6 ? new Vec3(0, 0, 1) : direction.normalize();
        this.entityData.set(DATA_DIR, dir.toVector3f());
        this.entityData.set(DATA_ORIGIN, this.position().toVector3f());
        this.damage = damage;
        this.grazeDamage = grazeDamage;
        this.tickDamage = tickDamage;
        this.headRatio = headRatio;
        this.grazeRatio = grazeRatio;
        this.grazeForm = grazeForm;
    }

    public float getRadius() {
        return this.entityData.get(DATA_RADIUS);
    }

    public int getLifetime() {
        return this.entityData.get(DATA_LIFETIME);
    }

    private Vec3 getStoredDirection() {
        return new Vec3(this.entityData.get(DATA_DIR));
    }

    public void setOwnerUuid(@Nullable UUID uuid) {
        this.ownerUuid = uuid;
    }

    @Nullable
    @Override
    public UUID getOwnerUUID() {
        return this.ownerUuid;
    }

    @Nullable
    @Override
    public LivingEntity getOwner() {
        return OwnableEntity.super.getOwner();
    }

    // ============================================================
    // 路径 / 半径：age 的纯函数
    // ============================================================

    private Vec3 getOrigin() {
        return new Vec3(this.entityData.get(DATA_ORIGIN));
    }

    /** 雾团中心在第 {@code t} tick 的位置。 */
    private Vec3 centerAt(int t) {
        float travel = this.entityData.get(DATA_TRAVEL);
        double k = Math.min(1.0, (double) t / JET_TICKS);
        double u = 1.0 - k;
        double eased = 1.0 - u * u * u;                        // easeOutCubic：先猛后缓
        Vec3 pos = getOrigin().add(getStoredDirection().scale(travel * eased));
        if (t > JET_TICKS) {
            // 聚集后沿原方向再缓慢漂 0.6 格（滞后感）
            double drift = 0.6 * (1.0 - Math.pow(0.93, t - JET_TICKS));
            pos = pos.add(getStoredDirection().scale(drift));
        }
        return pos;
    }

    /** 雾团半径：喷射中从 70% 涨到 100%；聚集后缓慢收缩到 0。 */
    private float radiusAt(int t) {
        float r = getRadius();
        int life = getLifetime();
        if (t <= JET_TICKS) {
            return r * (0.7F + 0.3F * (float) t / JET_TICKS);
        }
        float f = 1.0F - (float) (t - JET_TICKS) / (float) Math.max(1, life - JET_TICKS);
        return r * Math.max(f, 0.0F);
    }

    /**
     * 碰撞箱 = 以雾团中心为中心、边长等于当前直径的立方体。
     *
     * <p>必须判 {@code entityData == null}：{@code Entity} 构造函数里第一次 {@code setPos}
     * 就会调用本方法，那时 entityData 还没创建。
     */
    @Override
    protected AABB makeBoundingBox() {
        if (this.entityData == null) {
            return super.makeBoundingBox();
        }
        double r = Math.max(radiusAt(this.age), 0.25F);
        Vec3 c = this.position();
        return new AABB(c.x - r, c.y - r, c.z - r, c.x + r, c.y + r, c.z + r);
    }

    // ============================================================
    // 生命周期
    // ============================================================

    @Override
    public void tick() {
        super.tick();
        this.age++;

        // 实体跟着雾团走：服务端权威，客户端靠原版位置同步（updateInterval = 1）。
        // 位置在 center 处，碰撞箱由 makeBoundingBox 以 position 为中心生成。
        // 客户端也直接放到理论位置（与服务端同一个函数），F3+B 的碰撞箱不会滞后于粒子。
        // 每 tick 只算一次路径点，下面的粒子 / 伤害 / 音效共用
        Vec3 prev = centerAt(this.age - 1);
        Vec3 cur = centerAt(this.age);
        this.setPos(cur.x, cur.y, cur.z);

        if (this.level().isClientSide) {
            spawnClientParticles(prev, cur);
        } else {
            applyCloudDamage(prev, cur);
            playCloudSounds(cur);
        }

        if (this.age >= getLifetime()) {
            this.discard();
        }
    }

    /** 客户端：喷射阶段沿路径密集出粒子（有轨迹），聚集阶段在雾团内部翻腾。 */
    private void spawnClientParticles(Vec3 prev, Vec3 cur) {
        float radius = radiusAt(this.age);
        if (radius < 0.05F || this.age > EMIT_END_TICKS) {
            return;
        }
        boolean jet = this.age <= JET_TICKS;
        // 喷射前几 tick 一步就是十几格，粒子数按本 tick 扫过的长度放大，否则轨迹是稀疏的点
        int count = jet ? Math.min(32, 6 + (int) (prev.distanceTo(cur) * 3.0)) : 4;
        // 玩家调低了「粒子效果」或雾团很远时少生成一些（见 ParticleLoad）
        count = Math.max(1, Math.round(count * com.wraithcannon.client.ParticleLoad.scale(cur)));
        Vec3 dir = getStoredDirection();
        double speed = jet ? 0.22 * (1.0 - (double) this.age / JET_TICKS) + 0.03 : 0.015;

        for (int i = 0; i < count; i++) {
            // 在本 tick 扫过的线段上随机取点，轨迹才连续
            Vec3 base = prev.lerp(cur, this.random.nextDouble());
            double ox = (this.random.nextDouble() - 0.5) * radius * 1.6;
            double oy = (this.random.nextDouble() - 0.5) * radius * 1.3;
            double oz = (this.random.nextDouble() - 0.5) * radius * 1.6;

            this.level().addParticle(
                    ModParticles.CHAOS_MIST.get(),
                    base.x + ox, base.y + oy, base.z + oz,
                    dir.x * speed + ox * 0.02,
                    dir.y * speed + oy * 0.02,
                    dir.z * speed + oz * 0.02);
        }
    }

    // ============================================================
    // 音效（服务端触发，附近所有人都能听到）
    // ============================================================

    /**
     * <ul>
     *   <li>喷射阶段每 3 tick 一次微弱的划空声，声源跟着雾头走；</li>
     *   <li>喷射结束（雾团到位）那一刻放一次逐渐消散的长音；</li>
     *   <li>全程极少见地随机传来一声冤魂低语（约每团雾 40% 概率出现一次）。</li>
     * </ul>
     */
    private void playCloudSounds(Vec3 pos) {
        if (this.age <= JET_TICKS && this.age % 3 == 1) {
            ModSounds.play(this.level(), pos, ModSounds.MIST_FLOW, 1.0F, 0.9F + this.random.nextFloat() * 0.2F);
        }
        if (this.age == JET_TICKS) {
            ModSounds.play(this.level(), pos, ModSounds.MIST_DISSIPATE, 1.0F, 1.0F);
        }
        if (this.age <= 50 && this.random.nextInt(120) == 0) {
            ModSounds.play(this.level(), pos, ModSounds.MIST_WHISPER, 1.0F, 0.9F + this.random.nextFloat() * 0.2F);
        }
    }

    // ============================================================
    // 伤害
    // ============================================================

    /**
     * 头部（直接轰中）判定半径占雾团半径的比例。
     * 默认 1.0（整个雾团都算头部）；调小则需要更精确地正中目标。
     */
    /** 走廊实际半径的硬上限，防止极端配置把查询盒撑到全图。 */
    private static final double MAX_CORRIDOR = 32.0;

    private void applyCloudDamage(Vec3 prev, Vec3 cur) {
        if (!(this.level() instanceof ServerLevel serverLevel)) {
            return;
        }
        double radius = radiusAt(this.age);
        if (radius < 0.2) {
            return;
        }
        double headR = radius * this.headRatio;
        double corridorR = Math.min(MAX_CORRIDOR, Math.max(headR, radius * this.grazeRatio));
        boolean grazeOn = this.grazeRatio > 0.0F;
        boolean jet = this.age <= JET_TICKS;
        // 停留阶段每 2 tick 才扫一次（持续伤害间隔是 10 tick，目标最多晚 1 tick 吃到第一下）
        if (!jet && (this.age - JET_TICKS) % 2 != 1) {
            return;
        }

        LivingEntity owner = getOwner();
        Vec3 origin = getOrigin();
        // 查询盒只覆盖「本 tick 需要判定的区域」：
        //   喷射：本 tick 扫过的线段 ± 走廊半径（不扫整条路径）
        //   停留：雾团球体
        AABB box = jet
                ? new AABB(prev, cur).inflate(corridorR + 2.0)
                : new AABB(cur, cur).inflate(radius + 2.0);
        java.util.List<LivingEntity> targets = serverLevel.getEntitiesOfClass(LivingEntity.class, box,
                e -> e.isAlive() && e != owner && !isSparedPet(e));
        if (targets.isEmpty()) {
            return;
        }
        // 伤害来源只在真有目标时才构造
        DamageSource source = owner instanceof Player p
                ? serverLevel.damageSources().playerAttack(p)
                : owner != null
                        ? serverLevel.damageSources().mobAttack(owner)
                        : serverLevel.damageSources().magic();

        double headAlong = cur.subtract(origin).dot(getStoredDirection());
        for (LivingEntity target : targets) {
            Vec3 center = target.getBoundingBox().getCenter();
            double half = target.getBbWidth() * 0.5;
            UUID id = target.getUUID();

            if (jet) {
                // ① 头部扫过：直接轰中
                double headReach = headR + half;
                if (!this.directHit.contains(id)
                        && distToSegmentSqr(center, prev, cur) <= headReach * headReach) {
                    this.directHit.add(id);
                    this.grazed.add(id);
                    deal(target, source, this.damage);
                    continue;
                }
                // ② 站在轨迹走廊里：刮到
                if (!grazeOn || this.grazed.contains(id)) {
                    continue;
                }
                // 走廊从炮口由窄变宽：沿发射方向走满 grazeForm 格才达到完整宽度，
                // 贴身目标（宠物 / 队友）只落在很窄的走廊里
                double along = Math.max(0.0, center.subtract(origin).dot(getStoredDirection()));
                double form = this.grazeForm <= 0.0F ? 1.0 : Math.min(1.0, along / this.grazeForm);
                double corridor = corridorR * form + half;
                // 头部先判：只有雾头已经越过目标（或喷射结束）仍没轰中，才结算「刮到」，
                // 这样同一发里每个目标只吃最高的一档
                boolean headPassed = headAlong >= along || this.age >= JET_TICKS;
                if (headPassed
                        && distToSegmentSqr(center, origin, cur) <= corridor * corridor) {
                    this.grazed.add(id);
                    deal(target, source, this.grazeDamage);
                }
            } else {
                // ③ 停留浓雾：待在雾团球体里，每 HIT_INTERVAL tick 一次
                double reach = radius + half;
                if (center.distanceToSqr(cur) > reach * reach) {
                    continue;
                }
                Integer last = this.lastCloudHit.get(id);
                if (last != null && this.age - last < HIT_INTERVAL) {
                    continue;
                }
                this.lastCloudHit.put(id, this.age);
                deal(target, source, this.tickDamage);
            }
        }
    }

    /** 发射者自己的宠物不吃伤害（可配置）。 */
    private boolean isSparedPet(LivingEntity e) {
        return this.ownerUuid != null
                && com.wraithcannon.Config.SPARE_PETS.get()
                && e instanceof OwnableEntity pet
                && this.ownerUuid.equals(pet.getOwnerUUID());
    }

    private static void deal(LivingEntity target, DamageSource source, float amount) {
        if (amount <= 0.0F) {
            return;
        }
        target.invulnerableTime = 0;   // 不让原版无敌帧吞掉伤害
        target.hurt(source, amount);
        // 浓雾的拖拽感
        target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 30, 1, false, false, false));
    }

    private static double distToSegmentSqr(Vec3 p, Vec3 a, Vec3 b) {
        Vec3 ab = b.subtract(a);
        double len2 = ab.lengthSqr();
        double t = len2 < 1.0E-8 ? 0.0 : Math.max(0.0, Math.min(1.0, p.subtract(a).dot(ab) / len2));
        return p.distanceToSqr(a.add(ab.scale(t)));
    }

    // ============================================================
    // 杂项
    // ============================================================

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 64.0 * 64.0;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.age = tag.getInt("Age");
        this.entityData.set(DATA_RADIUS, tag.getFloat("Radius"));
        this.entityData.set(DATA_LIFETIME, tag.getInt("Lifetime"));
        this.entityData.set(DATA_TRAVEL, tag.getFloat("Travel"));
        this.entityData.set(DATA_DIR, new Vector3f(
                tag.getFloat("DirX"), tag.getFloat("DirY"), tag.getFloat("DirZ")));
        this.entityData.set(DATA_ORIGIN, new Vector3f(
                tag.getFloat("OriginX"), tag.getFloat("OriginY"), tag.getFloat("OriginZ")));
        this.damage = tag.getFloat("Damage");
        this.grazeDamage = tag.getFloat("GrazeDamage");
        this.headRatio = tag.contains("HeadRatio") ? tag.getFloat("HeadRatio") : 1.0F;
        this.grazeRatio = tag.contains("GrazeRatio") ? tag.getFloat("GrazeRatio") : 2.0F;
        this.grazeForm = tag.contains("GrazeForm") ? tag.getFloat("GrazeForm") : 4.0F;
        this.tickDamage = tag.getFloat("TickDamage");
        if (tag.hasUUID("Owner")) {
            this.ownerUuid = tag.getUUID("Owner");
        }
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        tag.putInt("Age", this.age);
        tag.putFloat("Radius", getRadius());
        tag.putInt("Lifetime", getLifetime());
        tag.putFloat("Travel", this.entityData.get(DATA_TRAVEL));
        Vector3f d = this.entityData.get(DATA_DIR);
        tag.putFloat("DirX", d.x);
        tag.putFloat("DirY", d.y);
        tag.putFloat("DirZ", d.z);
        Vector3f o = this.entityData.get(DATA_ORIGIN);
        tag.putFloat("OriginX", o.x);
        tag.putFloat("OriginY", o.y);
        tag.putFloat("OriginZ", o.z);
        tag.putFloat("Damage", this.damage);
        tag.putFloat("GrazeDamage", this.grazeDamage);
        tag.putFloat("HeadRatio", this.headRatio);
        tag.putFloat("GrazeRatio", this.grazeRatio);
        tag.putFloat("GrazeForm", this.grazeForm);
        tag.putFloat("TickDamage", this.tickDamage);
        if (this.ownerUuid != null) {
            tag.putUUID("Owner", this.ownerUuid);
        }
    }
}

