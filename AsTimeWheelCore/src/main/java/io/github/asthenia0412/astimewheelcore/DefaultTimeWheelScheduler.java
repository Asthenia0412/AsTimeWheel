package io.github.asthenia0412.astimewheelcore;

import io.github.asthenia0412.astimewheelcore.TimeWheelScheduler;
import io.github.asthenia0412.astimewheelcore.core.HashedWheelTimer;
import io.github.asthenia0412.astimewheelcore.core.Timeout;
import io.github.asthenia0412.astimewheelcore.core.TimerTask;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

public class DefaultTimeWheelScheduler implements TimeWheelScheduler {
    private final HashedWheelTimer timer;
    private final Map<String, Timeout> taskMap = new ConcurrentHashMap<>();
    private final AtomicLong taskIdGenerator = new AtomicLong(0);

    public DefaultTimeWheelScheduler(HashedWheelTimer timer) {
        this.timer = timer;
    }

    @Override
    public String schedule(Runnable task, long delay, TimeUnit unit) {
        String taskId = "task-" + taskIdGenerator.incrementAndGet();
        Timeout timeout = timer.newTimeout(new TimerTask() {
            @Override
            public void run(Timeout timeout) throws Exception {
                task.run();
                taskMap.remove(taskId);
            }
        }, delay, unit);
        taskMap.put(taskId, timeout);
        return taskId;
    }

    @Override
    public String scheduleAtFixedRate(Runnable task, long initialDelay, long period, TimeUnit unit) {
        String taskId = "task-" + taskIdGenerator.incrementAndGet();
        TimerTask timerTask = new TimerTask() {
            @Override
            public void run(Timeout timeout) throws Exception {
                task.run();
                if (taskMap.containsKey(taskId)) {
                    timer.newTimeout(this, period, unit);
                }
            }
        };
        Timeout timeout = timer.newTimeout(timerTask, initialDelay, unit);
        taskMap.put(taskId, timeout);
        return taskId;
    }

    @Override
    public boolean cancel(String taskId) {
        Timeout timeout = taskMap.remove(taskId);
        if (timeout != null) {
            timeout.cancel();
            return true;
        }
        return false;
    }

    @Override
    public void shutdown() {
        timer.stop();
        taskMap.clear();
    }
}