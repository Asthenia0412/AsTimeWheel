package io.github.asthenia0412.astimewheelcore.autoconfigure;

import java.util.concurrent.TimeUnit;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 时间轮定时器的配置属性类
 * <p>
 * 这个类用于从Spring Boot的配置文件中(如application.yml)加载时间轮定时器的配置参数，
 * 并通过Spring的配置属性绑定机制自动注入。
 * </p>
 *
 * <p><b>配置示例：</b></p>
 * <pre>
 * timewheel:
 *   tick-duration: 100       # 每个tick的持续时间
 *   time-unit: MILLISECONDS # 时间单位
 *   ticks-per-wheel: 512    # 时间轮的槽数
 * </pre>
 *
 * <p><b>线程安全：</b></p>
 * 由Spring容器管理的单例Bean，配置加载后不可变，线程安全
 */
@ConfigurationProperties(prefix = "timewheel")  // 指定配置前缀为"timewheel"
public class TimeWheelProperties {

    /**
     * 每个tick的持续时间
     * <p>
     * 默认值：100
     * 单位：由timeUnit指定
     * </p>
     *
     * <p><b>影响：</b></p>
     * 1. 值越小，定时精度越高，但CPU开销越大
     * 2. 值越大，吞吐量越高，但定时精度降低
     */
    private long tickDuration = 100;

    /**
     * 时间单位
     * <p>
     * 默认值：TimeUnit.MILLISECONDS(毫秒)
     * </p>
     *
     * <p><b>可选值：</b></p>
     * - NANOSECONDS: 纳秒
     * - MICROSECONDS: 微秒
     * - MILLISECONDS: 毫秒(推荐)
     * - SECONDS: 秒
     * - 其他TimeUnit枚举值
     */
    private TimeUnit timeUnit = TimeUnit.MILLISECONDS;

    /**
     * 时间轮的槽数(ticks per wheel)
     * <p>
     * 默认值：512
     * </p>
     *
     * <p><b>设计考虑：</b></p>
     * 1. 值越大，能表示的延迟范围越大，但内存占用越高
     * 2. 实际大小会被规范化为2的幂次(如512→512，500→512)
     * 3. 典型值范围：64-2048
     */
    private int ticksPerWheel = 512;

    /**
     * 获取tick持续时间
     * @return tick持续时间(数值部分)
     */
    public long getTickDuration() {
        return tickDuration;
    }

    /**
     * 设置tick持续时间
     * @param tickDuration 新的tick持续时间(必须大于0)
     * @throws IllegalArgumentException 如果tickDuration <= 0
     */
    public void setTickDuration(long tickDuration) {
        if (tickDuration <= 0) {
            throw new IllegalArgumentException("tickDuration must be positive");
        }
        this.tickDuration = tickDuration;
    }

    /**
     * 获取时间单位
     * @return 当前时间单位枚举
     */
    public TimeUnit getTimeUnit() {
        return timeUnit;
    }

    /**
     * 设置时间单位
     * @param timeUnit 新的时间单位(非null)
     * @throws NullPointerException 如果timeUnit为null
     */
    public void setTimeUnit(TimeUnit timeUnit) {
        if (timeUnit == null) {
            throw new NullPointerException("timeUnit cannot be null");
        }
        this.timeUnit = timeUnit;
    }

    /**
     * 获取时间轮槽数
     * @return 当前设置的槽数
     */
    public int getTicksPerWheel() {
        return ticksPerWheel;
    }

    /**
     * 设置时间轮槽数
     * @param ticksPerWheel 新的槽数(必须大于0)
     * @throws IllegalArgumentException 如果ticksPerWheel <= 0
     */
    public void setTicksPerWheel(int ticksPerWheel) {
        if (ticksPerWheel <= 0) {
            throw new IllegalArgumentException("ticksPerWheel must be positive");
        }
        this.ticksPerWheel = ticksPerWheel;
    }
}