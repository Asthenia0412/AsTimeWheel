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

@Configuration
@ConditionalOnClass(TimeWheelScheduler.class)
@EnableConfigurationProperties(TimeWheelProperties.class)
public class TimeWheelAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public HashedWheelTimer hashedWheelTimer(TimeWheelProperties properties) {
        return new HashedWheelTimer(
                properties.getTickDuration(),
                properties.getTimeUnit(),
                properties.getTicksPerWheel()
        );
    }

    @Bean
    @ConditionalOnMissingBean
    public TimeWheelScheduler timeWheelScheduler(HashedWheelTimer hashedWheelTimer) {
        return new DefaultTimeWheelScheduler(hashedWheelTimer);
    }
}