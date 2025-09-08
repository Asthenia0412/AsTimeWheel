package io.github.asthenia0412.astimewheelcore.core;

@FunctionalInterface
public interface TimerTask {
    void run(Timeout timeout) throws Exception;
}