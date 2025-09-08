package io.github.asthenia0412.astimewheelcore.core;

import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicIntegerFieldUpdater;

/**
 * 基于哈希时间轮算法实现的高性能定时器。
 * <p>
 * 该定时器使用循环数组结构（时间轮）来高效管理大量定时任务，每个时间槽（bucket）
 * 维护一个双向链表来存储任务。工作线程定期推进时间轮，处理到期的任务。
 * </p>
 *
 * <p><b>主要特性：</b></p>
 * <ul>
 *   <li>O(1) 时间复杂度添加/取消任务</li>
 *   <li>单线程处理所有任务，减少上下文切换开销</li>
 *   <li>自动适应系统时钟变化</li>
 *   <li>支持任务取消和批量清理</li>
 * </ul>
 *
 * <p><b>线程安全：</b></p>
 * 任务添加和取消操作是线程安全的，但任务执行在单线程中顺序进行。
 */
public class HashedWheelTimer {
    /** 初始化状态，定时器已创建但未启动 */
    private static final int WORKER_STATE_INIT = 0;

    /** 已启动状态，定时器正在运行 */
    private static final int WORKER_STATE_STARTED = 1;

    /** 已关闭状态，定时器已停止 */
    private static final int WORKER_STATE_SHUTDOWN = 2;

    /**
     * 原子状态更新器，用于无锁状态转换
     */
    private static final AtomicIntegerFieldUpdater<HashedWheelTimer> WORKER_STATE_UPDATER =
            AtomicIntegerFieldUpdater.newUpdater(HashedWheelTimer.class, "workerState");

    /** 每个tick的持续时间（单位由timeUnit指定） */
    private final long tickDuration;

    /** 时间单位 */
    private final TimeUnit timeUnit;

    /** 时间轮的槽数量 */
    private final int ticksPerWheel;

    /** 工作线程任务 */
    private final Worker worker = new Worker();

    /** 实际执行的工作线程 */
    private final Thread workerThread;

    /** 待处理的任务队列（线程安全） */
    private final ConcurrentLinkedQueue<Timeout> timeouts = new ConcurrentLinkedQueue<>();

    /** 已取消的任务队列（线程安全） */
    final ConcurrentLinkedQueue<Timeout> cancelledTimeouts = new ConcurrentLinkedQueue<>();

    /** 定时器状态（volatile保证可见性） */
    private volatile int workerState;

    /** 用于同步启动时间的闩锁 */
    private final CountDownLatch startTimeInitialized = new CountDownLatch(1);

    /** 定时器启动时间（纳秒，volatile保证可见性） */
    private volatile long startTime;

    /**
     * 构造定时器实例（使用默认线程工厂）
     *
     * @param tickDuration 每个tick的持续时间（必须大于0）
     * @param unit 时间单位（非null）
     * @param ticksPerWheel 时间轮槽数（必须大于0）
     * @throws NullPointerException 如果unit为null
     * @throws IllegalArgumentException 如果tickDuration或ticksPerWheel <= 0
     */
    public HashedWheelTimer(long tickDuration, TimeUnit unit, int ticksPerWheel) {
        this(tickDuration, unit, ticksPerWheel, Executors.defaultThreadFactory());
    }

    /**
     * 构造定时器实例（自定义线程工厂）
     *
     * @param tickDuration 每个tick的持续时间（必须大于0）
     * @param unit 时间单位（非null）
     * @param ticksPerWheel 时间轮槽数（必须大于0）
     * @param threadFactory 用于创建工作线程的工厂（非null）
     * @throws NullPointerException 如果unit或threadFactory为null
     * @throws IllegalArgumentException 如果tickDuration或ticksPerWheel <= 0
     */
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

    /**
     * 添加新定时任务
     *
     * @param task 要执行的任务（非null）
     * @param delay 延迟时间（小于等于0时自动调整为1）
     * @param unit 延迟时间单位（非null）
     * @return 关联的Timeout对象，可用于取消任务
     * @throws NullPointerException 如果task或unit为null
     * @throws IllegalStateException 如果定时器已关闭
     */
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

    /**
     * 启动定时器工作线程
     * <p>
     * 如果定时器尚未启动，则创建工作线程并开始处理任务。
     * 如果已经启动或已关闭，则不做任何操作或抛出异常。
     * </p>
     *
     * @throws IllegalStateException 如果定时器已关闭
     */
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

        // 等待工作线程初始化完成
        while (startTime == 0) {
            try {
                startTimeInitialized.await();
            } catch (InterruptedException ignore) {}
        }
    }

    /**
     * 停止定时器
     * <p>
     * 中断工作线程并丢弃所有未处理任务。
     * 已开始执行的任务会继续完成。
     * 该方法幂等，多次调用不会产生副作用。
     * </p>
     */
    public void stop() {
        if (WORKER_STATE_UPDATER.compareAndSet(this, WORKER_STATE_STARTED, WORKER_STATE_SHUTDOWN)) {
            workerThread.interrupt();
        }
    }

    /**
     * 检查定时器是否运行中
     *
     * @return true如果定时器正在运行，false否则
     */
    public boolean isRunning() {
        return WORKER_STATE_UPDATER.get(this) == WORKER_STATE_STARTED;
    }

    /**
     * 定时器工作线程实现
     */
    private final class Worker implements Runnable {
        /** 每个tick的纳秒数（预计算优化） */
        private final long tickDurationNanos;

        /** 当前tick计数 */
        private long currentTick = 0;

        /** 时间轮数组 */
        private final HashedWheelBucket[] wheel;

        Worker() {
            this.tickDurationNanos = timeUnit.toNanos(tickDuration);
            this.wheel = createWheel(ticksPerWheel);
        }

        /**
         * 主工作循环
         */
        @Override
        public void run() {
            // 初始化启动时间
            startTime = System.nanoTime();
            if (startTime == 0) {
                startTime = 1;  // 避免0值特殊含义
            }
            startTimeInitialized.countDown();

            // 主循环
            while (WORKER_STATE_UPDATER.get(HashedWheelTimer.this) == WORKER_STATE_STARTED) {
                final long deadline = waitForNextTick();
                if (deadline > 0) {
                    // 计算当前槽位
                    int idx = (int) (currentTick & (wheel.length - 1));

                    // 处理已取消的任务
                    processCancelledTasks();

                    // 获取当前槽位的桶
                    HashedWheelBucket bucket = wheel[idx];

                    // 将队列中的任务转移到时间轮
                    transferTimeoutsToBuckets();

                    // 处理过期任务
                    bucket.expireTimeouts(deadline);

                    // 推进tick计数器
                    currentTick++;
                }
            }
        }

        /**
         * 等待下一个tick到来
         *
         * @return 当前时间（纳秒），如果定时器已关闭返回-1
         */
        private long waitForNextTick() {
            long deadline = startTime + (currentTick + 1) * tickDurationNanos;

            for (;;) {
                final long currentTime = System.nanoTime();
                // 计算需要睡眠的时间（毫秒），向上取整
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

        /**
         * 将队列中的任务转移到时间轮
         * <p>
         * 每次最多处理100,000个任务以避免长时间阻塞时间轮推进。
         * </p>
         */
        private void transferTimeoutsToBuckets() {
            for (int i = 0; i < 100000; i++) {
                Timeout timeout = timeouts.poll();
                if (timeout == null) {
                    break;
                }
                if (timeout.state() == Timeout.STATE_CANCELLED) {
                    continue;
                }

                // 计算任务应该被放置的轮次和槽位
                long calculated = timeout.deadline() / tickDurationNanos;
                timeout.setRemainingRounds((calculated - currentTick) / wheel.length);

                final long ticks = Math.max(calculated, currentTick);
                int stopIndex = (int) (ticks & (wheel.length - 1));

                HashedWheelBucket bucket = wheel[stopIndex];
                bucket.addTimeout(timeout);
            }
        }

        /**
         * 处理已取消的任务
         */
        private void processCancelledTasks() {
            for (;;) {
                Timeout timeout = cancelledTimeouts.poll();
                if (timeout == null) {
                    break;
                }
                try {
                    timeout.remove();
                } catch (Throwable t) {
                    // 忽略任何移除异常
                }
            }
        }

        /**
         * 创建时间轮数组
         *
         * @param ticksPerWheel 初始槽数
         * @return 大小调整为2的幂次的桶数组
         */
        private HashedWheelBucket[] createWheel(int ticksPerWheel) {
            ticksPerWheel = normalizeTicksPerWheel(ticksPerWheel);
            HashedWheelBucket[] wheel = new HashedWheelBucket[ticksPerWheel];
            for (int i = 0; i < wheel.length; i++) {
                wheel[i] = new HashedWheelBucket();
            }
            return wheel;
        }

        /**
         * 规范化时间轮槽数为2的幂次
         *
         * @param ticksPerWheel 原始槽数
         * @return 不小于原始槽数的最小2的幂次数
         */
        private int normalizeTicksPerWheel(int ticksPerWheel) {
            int normalizedTicksPerWheel = 1;
            while (normalizedTicksPerWheel < ticksPerWheel) {
                normalizedTicksPerWheel <<= 1;
            }
            return normalizedTicksPerWheel;
        }
    }
}