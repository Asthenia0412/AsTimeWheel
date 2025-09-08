package io.github.asthenia0412.astimewheelcore.autoconfigure;

import io.github.asthenia0412.astimewheelcore.DefaultTimeWheelScheduler;
import io.github.asthenia0412.astimewheelcore.TimeWheelScheduler;
import io.github.asthenia0412.astimewheelcore.autoconfigure.TimeWheelProperties;
import io.github.asthenia0412.astimewheelcore.core.HashedWheelTimer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

/**
 * 时间轮自动配置类，用于在Spring Boot应用中自动配置时间轮调度器。
 * <p>
 * 该类在检测到类路径中存在 {@link TimeWheelScheduler} 类时自动激活，
 * 并会创建 {@link HashedWheelTimer} 和 {@link TimeWheelScheduler} 的默认实现。
 * </p>
 *
 * @Configuration 表示这是一个Spring配置类，会被Spring容器处理
 * @ConditionalOnClass 确保只有在类路径中存在指定类时才激活此配置
 * @EnableConfigurationProperties 启用对指定配置属性的支持
 */
@Configuration
@ConditionalOnClass(TimeWheelScheduler.class)
@EnableConfigurationProperties(TimeWheelProperties.class)
public class TimeWheelAutoConfiguration {

    /**
     * 创建并配置一个 {@link HashedWheelTimer} 实例。
     * <p>
     * 此Bean的创建条件：当容器中不存在其他 {@link HashedWheelTimer} 类型的Bean时。
     * </p>
     *
     * @param properties 时间轮配置属性，从application.yml/properties中注入
     * @return 配置好的HashedWheelTimer实例
     *
     * @Bean 表示该方法返回的对象应注册为Spring应用上下文中的Bean
     * @ConditionalOnMissingBean 确保只有在不存在该类型Bean时才创建
     *
     * 边界情况说明：
     * 1. 如果properties为null，会抛出IllegalArgumentException
     * 2. 如果tickDuration <= 0，会抛出IllegalArgumentException
     * 3. 如果ticksPerWheel <= 0，会抛出IllegalArgumentException
     */
    @Bean
    @ConditionalOnMissingBean
    public HashedWheelTimer hashedWheelTimer(TimeWheelProperties properties) {
        return new HashedWheelTimer(
                properties.getTickDuration(),
                properties.getTimeUnit(),
                properties.getTicksPerWheel()
        );
    }

    /**
     * 创建并配置一个 {@link TimeWheelScheduler} 实例。
     * <p>
     * 此Bean的创建条件：当容器中不存在其他 {@link TimeWheelScheduler} 类型的Bean时。
     * 使用已配置的 {@link HashedWheelTimer} 作为底层实现。
     * </p>
     *
     * @param hashedWheelTimer 由Spring自动注入的HashedWheelTimer实例
     * @return 配置好的TimeWheelScheduler实例
     *
     * @Bean 表示该方法返回的对象应注册为Spring应用上下文中的Bean
     * @ConditionalOnMissingBean 确保只有在不存在该类型Bean时才创建
     *
     * 边界情况说明：
     * 1. 如果hashedWheelTimer为null，会抛出IllegalArgumentException
     * 2. 如果hashedWheelTimer未正确初始化，可能导致调度器工作异常
     */
    @Bean
    @ConditionalOnMissingBean
    public TimeWheelScheduler timeWheelScheduler(HashedWheelTimer hashedWheelTimer) {
        return new DefaultTimeWheelScheduler(hashedWheelTimer);
    }
}