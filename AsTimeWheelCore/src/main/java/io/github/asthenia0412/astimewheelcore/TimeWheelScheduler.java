package io.github.asthenia0412.astimewheelcore;

import java.util.concurrent.TimeUnit;

public interface TimeWheelScheduler {
    String schedule(Runnable task, long delay, TimeUnit unit);
    String scheduleAtFixedRate(Runnable task, long initialDelay, long period, TimeUnit unit);
    boolean cancel(String taskId);
    void shutdown();
}