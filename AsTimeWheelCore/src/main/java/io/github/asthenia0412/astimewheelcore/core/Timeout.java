package io.github.asthenia0412.astimewheelcore.core;

import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

public class Timeout {
    public static final int STATE_NEW = 0;
    public static final int STATE_CANCELLED = 1;
    public static final int STATE_EXPIRED = 2;

    private static final AtomicIntegerFieldUpdater<Timeout> STATE_UPDATER =
            AtomicIntegerFieldUpdater.newUpdater(Timeout.class, "state");

    private final HashedWheelTimer timer;
    private final TimerTask task;
    private final long deadline;
    private volatile int state = STATE_NEW;
    private volatile long remainingRounds;
    volatile HashedWheelBucket bucket;
    Timeout next;
    Timeout prev;

    public Timeout(HashedWheelTimer timer, TimerTask task, long deadline) {
        this.timer = timer;
        this.task = task;
        this.deadline = deadline;
    }

    public TimerTask task() {
        return task;
    }

    public long deadline() {
        return deadline;
    }

    public int state() {
        return state;
    }

    void setRemainingRounds(long remainingRounds) {
        this.remainingRounds = remainingRounds;
    }

    long getRemainingRounds() {
        return this.remainingRounds;
    }

    void expire() {
        if (!STATE_UPDATER.compareAndSet(this, STATE_NEW, STATE_EXPIRED)) {
            return;
        }

        try {
            task.run(this);
        } catch (Throwable t) {
            Thread.currentThread().getUncaughtExceptionHandler()
                    .uncaughtException(Thread.currentThread(), t);
        }
    }

    public void cancel() {
        if (state() != STATE_NEW) {
            return;
        }

        if (!STATE_UPDATER.compareAndSet(this, STATE_NEW, STATE_CANCELLED)) {
            return;
        }

        // 直接访问包级私有的cancelledTimeouts
        timer.cancelledTimeouts.add(this);
    }

    void remove() {
        HashedWheelBucket bucket = this.bucket;
        if (bucket != null) {
            bucket.remove(this);
        }
    }
}