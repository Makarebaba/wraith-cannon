package com.wraithcannon;

import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * 机制数值配置。全部可在 config/wraith_cannon-common.toml 里改，不用重新编译。
 */
public class Config {

    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();

    // ---- 近战（攻击方式一） ----
    // 近战伤害 / 攻速 / 耐久要在物品注册时就写入，放在单独的启动配置 StartupConfig 里
    // （config/wraith_cannon-startup.toml，修改后需重启游戏）。

    public static final ModConfigSpec.IntValue MELEE_COOLDOWN_TICKS = BUILDER
            .comment("近战命中目标后，给这把炮加多少 tick 的冷却（期间无法右键蓄力），防止「砸完立刻蓄力开炮」。默认 0 = 不限制")
            .translation("wraith_cannon.configuration.melee.cooldownTicks")
            .defineInRange("melee.cooldownTicks", 0, 0, 200);

    // ---- 喷雾（攻击方式二） ----
    public static final ModConfigSpec.IntValue CHARGE_TICKS = BUILDER
            .comment("右键长按多久算蓄力完成（tick，30 = 1.5 秒）")
            .translation("wraith_cannon.configuration.mist.chargeTicks")
            .defineInRange("mist.chargeTicks", 30, 1, 200);

    public static final ModConfigSpec.IntValue MIST_COOLDOWN_TICKS = BUILDER
            .comment("喷雾后的冷却（tick）")
            .translation("wraith_cannon.configuration.mist.cooldownTicks")
            .defineInRange("mist.cooldownTicks", 25, 0, 600);

    /**
     * 注意：ModConfigSpec 只在键缺失时才使用代码里的默认值，
     * 已生成的配置文件里的旧值不会被新默认值覆盖；删除配置文件即可恢复默认。
     */
    public static final ModConfigSpec.DoubleValue MIST_RANGE = BUILDER
            .comment("喷雾最远触及距离（格，从眼睛到雾团前沿）。原版剑 3 格，默认 27 格 = 剑的 9 倍")
            .translation("wraith_cannon.configuration.mist.reachBlocks")
            .defineInRange("mist.reachBlocks", 27.0D, 1.0D, 128.0D);

    public static final ModConfigSpec.DoubleValue MIST_DAMAGE = BUILDER
            .comment("浓雾头部「直接轰中」目标的伤害")
            .translation("wraith_cannon.configuration.mist.damage")
            .defineInRange("mist.damage", 20.0D, 0.0D, 1000.0D);

    public static final ModConfigSpec.DoubleValue MIST_GRAZE_DAMAGE = BUILDER
            .comment("喷射轨迹「刮到」目标（没被头部轰中）的伤害，每个目标只结算一次")
            .translation("wraith_cannon.configuration.mist.grazeDamage")
            .defineInRange("mist.grazeDamage", 3.0D, 0.0D, 1000.0D);

    public static final ModConfigSpec.DoubleValue MIST_TICK_DAMAGE = BUILDER
            .comment("停留的浓雾对雾内目标每 0.5 秒（10 tick）结算一次的伤害")
            .translation("wraith_cannon.configuration.mist.tickDamage")
            .defineInRange("mist.tickDamage", 5.0D, 0.0D, 1000.0D);

    public static final ModConfigSpec.DoubleValue MIST_RECOIL = BUILDER
            .comment("喷雾后坐力强度（速度冲量，格/tick）。0 = 无；默认 0.4 = 轻微后退；上限 3.0 以防把自己弹飞。",
                    "水平后坐沿视线反方向；越接近垂直射击水平后坐越小（最多减弱 70%）。垂直分量见 mist.verticalRecoilMultiplier，水中见 mist.waterRecoilMultiplier。",
                    "Recoil strength after firing (velocity impulse, blocks/tick). 0 = none; default 0.4 = a slight push; max 3.0 so you cannot launch yourself too far.",
                    "Horizontal recoil points opposite to where you aim and weakens as the shot gets closer to vertical (up to -70%).",
                    "For the vertical part see mist.verticalRecoilMultiplier; for water see mist.waterRecoilMultiplier.")
            .translation("wraith_cannon.configuration.mist.recoil")
            .defineInRange("mist.recoil", 0.4D, 0.0D, 3.0D);

    public static final ModConfigSpec.DoubleValue MIST_VERTICAL_RECOIL_MULTIPLIER = BUILDER
            .comment("垂直后坐倍率（默认 0.8）。垂直冲量 = mist.recoil × 此值 × 俯仰曲线。0 = 关闭垂直后坐，只保留水平后坐。",
                    "陆地：只有接近垂直射击才有垂直后坐——俯仰角绝对值低于 mist.verticalRecoilPitchThreshold 为 0，之后平滑增强，约 85 度达到最大。",
                    "向下射 → 向上推，向上射 → 向下压。",
                    "水中：不设角度门槛，按 sin(俯仰角) 线性变化，再乘 mist.waterRecoilMultiplier。",
                    "Vertical recoil multiplier (default 0.8). Vertical impulse = mist.recoil x this value x pitch curve. 0 = disable vertical recoil and keep only horizontal recoil.",
                    "On land: vertical recoil only applies to near-vertical shots - zero while |pitch| is below mist.verticalRecoilPitchThreshold, then ramping up smoothly to its maximum at about 85 degrees.",
                    "Shooting down pushes you up; shooting up pushes you down.",
                    "In water: no pitch threshold - the curve is linear in sin(pitch), then multiplied by mist.waterRecoilMultiplier.")
            .translation("wraith_cannon.configuration.mist.verticalRecoilMultiplier")
            .defineInRange("mist.verticalRecoilMultiplier", 0.8D, 0.0D, 3.0D);

    public static final ModConfigSpec.DoubleValue MIST_VERTICAL_RECOIL_PITCH_THRESHOLD = BUILDER
            .comment("陆地垂直后坐的俯仰角门槛（度，默认 70，范围 60~80）。|俯仰角| 低于此值时陆地上没有垂直后坐；调低则更容易触发。",
                    "Pitch threshold (degrees, default 70, range 60-80) for vertical recoil on land. Below this |pitch| there is no vertical recoil on land; lower values trigger it more easily.")
            .translation("wraith_cannon.configuration.mist.verticalRecoilPitchThreshold")
            .defineInRange("mist.verticalRecoilPitchThreshold", 70.0D, 60.0D, 80.0D);

    public static final ModConfigSpec.DoubleValue MIST_WATER_RECOIL_MULTIPLIER = BUILDER
            .comment("在水中时，整个后坐力（水平 + 垂直）乘以此倍率（默认 0.4，模拟水的阻力）。1.0 = 与陆地相同；0 = 水中无后坐力。",
                    "While in water, the whole recoil (horizontal + vertical) is multiplied by this value (default 0.4, simulating water drag). 1.0 = same as on land; 0 = no recoil in water.")
            .translation("wraith_cannon.configuration.mist.waterRecoilMultiplier")
            .defineInRange("mist.waterRecoilMultiplier", 0.4D, 0.0D, 2.0D);

    public static final ModConfigSpec.IntValue MIST_CLOUD_SECONDS = BUILDER
            .comment("浓雾云存在时长（秒），默认约 3 秒")
            .translation("wraith_cannon.configuration.mist.cloudSeconds")
            .defineInRange("mist.cloudSeconds", 3, 1, 30);

    public static final ModConfigSpec.DoubleValue MIST_CLOUD_RADIUS = BUILDER
            .comment("浓雾云的初始半径（格）")
            .translation("wraith_cannon.configuration.mist.cloudRadius")
            .defineInRange("mist.cloudRadius", 1.5D, 0.5D, 16.0D);

    public static final ModConfigSpec.DoubleValue MIST_HEAD_RATIO = BUILDER
            .comment("「直接轰中」判定半径 = 雾团半径 × 此值。1.0 = 整个雾团都算头部（默认）；",
                    "调小（如 0.6）则必须正中中心线才吃满伤害")
            .translation("wraith_cannon.configuration.mist.headRatio")
            .defineInRange("mist.headRatio", 1.0D, 0.1D, 3.0D);

    public static final ModConfigSpec.DoubleValue MIST_GRAZE_RATIO = BUILDER
            .comment("「刮到」走廊半径 = 雾团半径 × 此值（默认 2）。设为 0 = 关闭刮到判定。",
                    "不会小于头部判定，且走廊实际半径上限 32 格",
                    "Graze corridor radius = cloud radius x this value (default 2). 0 = disable grazing.",
                    "Never smaller than the direct-hit radius; the corridor radius is capped at 32 blocks.")
            .translation("wraith_cannon.configuration.mist.grazeRatio")
            .defineInRange("mist.grazeRatio", 2.0D, 0.0D, 8.0D);

    public static final ModConfigSpec.DoubleValue MIST_GRAZE_FORM_DISTANCE = BUILDER
            .comment("「刮到」走廊从炮口由小变大的成形距离（格）：在距离发射点这么远处才达到完整宽度。",
                    "默认 4 格，让贴身的宠物 / 队友不会被刮到。0 = 不渐变（一出炮口就是完整宽度）")
            .translation("wraith_cannon.configuration.mist.grazeFormDistance")
            .defineInRange("mist.grazeFormDistance", 4.0D, 0.0D, 64.0D);

    public static final ModConfigSpec.DoubleValue PIERCE_BLOCKS = BUILDER
            .comment("雾在实心方块里最多能走多长（格，按射线实际穿过的长度算，擦边只算一小段），再多就被挡住（默认 2.2，最大 100）。0 = 不能穿墙。射程还会被 pierceRangeLoss 逐格扣减，想近似不衰减请同时把它设为 0")
            .translation("wraith_cannon.configuration.mist.pierceMaxBlocks")
            .defineInRange("mist.pierceMaxBlocks", 2.2D, 0.0D, 100.0D);

    public static final ModConfigSpec.DoubleValue PIERCE_LOSS = BUILDER
            .comment("射线在实心方块里每走 1 格长度，剩余射程扣掉多少格（默认 6）。0 = 穿墙不损失射程")
            .translation("wraith_cannon.configuration.mist.pierceRangeLoss")
            .defineInRange("mist.pierceRangeLoss", 6.0D, 0.0D, 64.0D);

    public static final ModConfigSpec.BooleanValue SPARE_PETS = BUILDER
            .comment("发射者自己驯服的宠物不受浓雾伤害（默认开启）")
            .translation("wraith_cannon.configuration.mist.sparePets")
            .define("mist.sparePets", true);

    public static final ModConfigSpec.DoubleValue WALL_OVERLAP = BUILDER
            .comment("雾团前沿相对停点的偏移（格，默认 0）。正值 = 越过停点前伸，负值 = 停点后撤。0 = 前沿刚好停在墙面。注意：本项对所有射击都生效，不只限于被墙挡住的情况（未被挡住时也会加在满射程上）")
            .translation("wraith_cannon.configuration.mist.wallOverlap")
            .defineInRange("mist.wallOverlap", 0.0D, -3.0D, 3.0D);

    // ---- 手感（仅客户端表现，不影响伤害） ----
    public static final ModConfigSpec.DoubleValue FOV_MAX_ZOOM = BUILDER
            .comment("蓄满时视野缩小比例。0 = 不拉近；原版弓是 0.15（上限，不允许更大）；默认 0.15")
            .translation("wraith_cannon.configuration.feel.chargeZoom")
            .defineInRange("feel.chargeZoom", 0.15D, 0.0D, 0.15D);

    public static final ModConfigSpec.DoubleValue MUZZLE_CLIMB = BUILDER
            .comment("开火后枪口上跳幅度（度，镜头向上抬起）。0 = 无；默认 5 度；上限 15")
            .translation("wraith_cannon.configuration.feel.muzzleClimbDegrees")
            .defineInRange("feel.muzzleClimbDegrees", 5.0D, 0.0D, 15.0D);

    // ---- 持有代价 ----
    public static final ModConfigSpec.BooleanValue SLOW_ENABLED = BUILDER
            .comment("主手持有时是否施加缓慢（默认开启）。关闭后持有不再减速，下面两项失效")
            .translation("wraith_cannon.configuration.held.slowEnabled")
            .define("held.slowEnabled", true);

    public static final ModConfigSpec.IntValue SLOW_AMPLIFIER = BUILDER
            .comment("主手持有时的缓慢等级（0 = 缓慢 I，10 = 缓慢 XI）。要完全去掉缓慢请关闭 held.slowEnabled")
            .translation("wraith_cannon.configuration.held.slowAmplifier")
            .defineInRange("held.slowAmplifier", 0, 0, 10);

    public static final ModConfigSpec.BooleanValue SLOW_HIDES_PARTICLES = BUILDER
            .comment("缓慢效果是否隐藏粒子（默认隐藏，避免玩家身上一直冒灰点）")
            .translation("wraith_cannon.configuration.held.hideSlowParticles")
            .define("held.hideSlowParticles", true);

    static final ModConfigSpec SPEC = BUILDER.build();
}

