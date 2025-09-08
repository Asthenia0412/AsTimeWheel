package io.github.asthenia0412.astimewheelcore.autoconfigure;

import java.util.concurrent.TimeUnit;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "timewheel")
public class TimeWheelProperties {
    private long tickDuration = 100;
    private TimeUnit timeUnit = TimeUnit.MILLISECONDS;
    private int ticksPerWheel = 512;

    public long getTickDuration() {
        return tickDuration;
    }

    public void setTickDuration(long tickDuration) {
        this.tickDuration = tickDuration;
    }

    public TimeUnit getTimeUnit() {
        return timeUnit;
    }

    public void setTimeUnit(TimeUnit timeUnit) {
        this.timeUnit = timeUnit;
    }

    public int getTicksPerWheel() {
        return ticksPerWheel;
    }

    public void setTicksPerWheel(int ticksPerWheel) {
        this.ticksPerWheel = ticksPerWheel;
    }
}