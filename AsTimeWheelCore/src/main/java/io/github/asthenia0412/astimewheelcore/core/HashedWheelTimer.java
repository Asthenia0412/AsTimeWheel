package io.github.asthenia0412.astimewheelcore.core;

import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

public class HashedWheelTimer {
    private static final int WORKER_STATE_INIT = 0;
    private static final int WORKER_STATE_STARTED = 1;
    private static final int WORKER_STATE_SHUTDOWN = 2;

    private static final AtomicIntegerFieldUpdater<HashedWheelTimer> WORKER_STATE_UPDATER =
            AtomicIntegerFieldUpdater.newUpdater(HashedWheelTimer.class, "workerState");

    private final long tickDuration;
    private final TimeUnit timeUnit;
    private final int ticksPerWheel;
    private final Worker worker = new Worker();
    private final Thread workerThread;
    private final ConcurrentLinkedQueue<Timeout> timeouts = new ConcurrentLinkedQueue<>();
    // 修改为使用ConcurrentLinkedQueue替代Set
    final ConcurrentLinkedQueue<Timeout> cancelledTimeouts = new ConcurrentLinkedQueue<>();
    private volatile int workerState;
    private final CountDownLatch startTimeInitialized = new CountDownLatch(1);
    private volatile long startTime;

    public HashedWheelTimer(long tickDuration, TimeUnit unit, int ticksPerWheel) {
        this(tickDuration, unit, ticksPerWheel, Executors.defaultThreadFactory());
    }

    public HashedWheelTimer(long tickDuration, TimeUnit unit, int ticksPerWheel, ThreadFactory threadFactory) {
        if (unit == null || threadFactory == null) {
            throw new NullPointerException();
        }
        if (tickDuration <= 0 || ticksPerWheel <= 0) {
            throw new IllegalArgumentException();
        }

        this.tickDuration = tickDuration;
        this.timeUnit = unit;
        this.ticksPerWheel = ticksPerWheel;
        this.workerThread = threadFactory.newThread(worker);
    }

    public Timeout newTimeout(TimerTask task, long delay, TimeUnit unit) {
        if (task == null || unit == null) {
            throw new NullPointerException();
        }

        if (delay <= 0) {
            delay = 1;
        }

        if (WORKER_STATE_UPDATER.get(this) == WORKER_STATE_SHUTDOWN) {
            throw new IllegalStateException("Timer already stopped");
        }

        start();

        long deadline = System.nanoTime() + unit.toNanos(delay) - startTime;
        Timeout timeout = new Timeout(this, task, deadline);
        timeouts.add(timeout);
        return timeout;
    }

    public void start() {
        switch (WORKER_STATE_UPDATER.get(this)) {
            case WORKER_STATE_INIT:
                if (WORKER_STATE_UPDATER.compareAndSet(this, WORKER_STATE_INIT, WORKER_STATE_STARTED)) {
                    workerThread.start();
                }
                break;
            case WORKER_STATE_STARTED:
                break;
            case WORKER_STATE_SHUTDOWN:
                throw new IllegalStateException("Cannot be started once stopped");
            default:
                throw new Error("Invalid WorkerState");
        }

        while (startTime == 0) {
            try {
                startTimeInitialized.await();
            } catch (InterruptedException ignore) {}
        }
    }

    public void stop() {
        if (WORKER_STATE_UPDATER.compareAndSet(this, WORKER_STATE_STARTED, WORKER_STATE_SHUTDOWN)) {
            workerThread.interrupt();
        }
    }

    public boolean isRunning() {
        return WORKER_STATE_UPDATER.get(this) == WORKER_STATE_STARTED;
    }

    private final class Worker implements Runnable {
        private final long tickDurationNanos;
        private long currentTick = 0;
        private final HashedWheelBucket[] wheel;

        Worker() {
            this.tickDurationNanos = timeUnit.toNanos(tickDuration);
            this.wheel = createWheel(ticksPerWheel);
        }

        @Override
        public void run() {
            startTime = System.nanoTime();
            if (startTime == 0) {
                startTime = 1;
            }
            startTimeInitialized.countDown();

            while (WORKER_STATE_UPDATER.get(HashedWheelTimer.this) == WORKER_STATE_STARTED) {
                final long deadline = waitForNextTick();
                if (deadline > 0) {
                    int idx = (int) (currentTick & (wheel.length - 1));
                    processCancelledTasks();
                    HashedWheelBucket bucket = wheel[idx];
                    transferTimeoutsToBuckets();
                    bucket.expireTimeouts(deadline);
                    currentTick++;
                }
            }
        }

        private long waitForNextTick() {
            long deadline = startTime + (currentTick + 1) * tickDurationNanos;

            for (;;) {
                final long currentTime = System.nanoTime();
                long sleepTimeMs = (deadline - currentTime + 999999) / 1000000;

                if (sleepTimeMs <= 0) {
                    return currentTime;
                }

                try {
                    Thread.sleep(sleepTimeMs);
                } catch (InterruptedException ignored) {
                    if (WORKER_STATE_UPDATER.get(HashedWheelTimer.this) == WORKER_STATE_SHUTDOWN) {
                        return -1;
                    }
                }
            }
        }

        private void transferTimeoutsToBuckets() {
            for (int i = 0; i < 100000; i++) {
                Timeout timeout = timeouts.poll();
                if (timeout == null) {
                    break;
                }
                if (timeout.state() == Timeout.STATE_CANCELLED) {
                    continue;
                }

                long calculated = timeout.deadline() / tickDurationNanos;
                timeout.setRemainingRounds((calculated - currentTick) / wheel.length);

                final long ticks = Math.max(calculated, currentTick);
                int stopIndex = (int) (ticks & (wheel.length - 1));

                HashedWheelBucket bucket = wheel[stopIndex];
                bucket.addTimeout(timeout);
            }
        }

        private void processCancelledTasks() {
            for (;;) {
                Timeout timeout = cancelledTimeouts.poll();
                if (timeout == null) {
                    break;
                }
                try {
                    timeout.remove();
                } catch (Throwable t) {}
            }
        }

        private HashedWheelBucket[] createWheel(int ticksPerWheel) {
            ticksPerWheel = normalizeTicksPerWheel(ticksPerWheel);
            HashedWheelBucket[] wheel = new HashedWheelBucket[ticksPerWheel];
            for (int i = 0; i < wheel.length; i++) {
                wheel[i] = new HashedWheelBucket();
            }
            return wheel;
        }

        private int normalizeTicksPerWheel(int ticksPerWheel) {
            int normalizedTicksPerWheel = 1;
            while (normalizedTicksPerWheel < ticksPerWheel) {
                normalizedTicksPerWheel <<= 1;
            }
            return normalizedTicksPerWheel;
        }
    }
}