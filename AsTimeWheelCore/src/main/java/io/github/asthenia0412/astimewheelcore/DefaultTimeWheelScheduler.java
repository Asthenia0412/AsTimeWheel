package io.github.asthenia0412.astimewheelcore;

import io.github.asthenia0412.astimewheelcore.TimeWheelScheduler;
import io.github.asthenia0412.astimewheelcore.core.HashedWheelTimer;
import io.github.asthenia0412.astimewheelcore.core.Timeout;
import io.github.asthenia0412.astimewheelcore.core.TimerTask;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 基于哈希时间轮的默认调度器实现。
 * <p>
 * 该类提供了定时任务调度的基础功能，包括：
 * <ul>
 *   <li>单次延迟任务调度</li>
 *   <li>固定速率重复任务调度</li>
 *   <li>任务取消</li>
 *   <li>调度器关闭</li>
 * </ul>
 * </p>
 *
 * <p><b>线程安全：</b></p>
 * 所有公共方法都是线程安全的，可以跨多个线程安全调用。
 */
public class DefaultTimeWheelScheduler implements TimeWheelScheduler {
    /**
     * 底层时间轮定时器实例
     */
    private final HashedWheelTimer timer;

    /**
     * 任务映射表，存储所有活跃任务的Timeout对象
     * <p>
     * Key: 任务ID (格式为"task-{sequence}")
     * Value: 对应的Timeout对象
     * </p>
     */
    private final Map<String, Timeout> taskMap = new ConcurrentHashMap<>();

    /**
     * 任务ID生成器，用于生成唯一任务标识符
     */
    private final AtomicLong taskIdGenerator = new AtomicLong(0);

    /**
     * 构造一个新的调度器实例
     *
     * @param timer 底层时间轮定时器实例（非null）
     * @throws NullPointerException 如果timer参数为null
     */
    public DefaultTimeWheelScheduler(HashedWheelTimer timer) {
        if (timer == null) {
            throw new NullPointerException("timer cannot be null");
        }
        this.timer = timer;
    }

    /**
     * 调度一个单次延迟任务
     *
     * @param task 要执行的任务（非null）
     * @param delay 延迟时间（必须大于0）
     * @param unit 时间单位（非null）
     * @return 分配给该任务的唯一标识符（格式为"task-{sequence}"）
     * @throws NullPointerException 如果task或unit为null
     * @throws IllegalArgumentException 如果delay <= 0
     */
    @Override
    public String schedule(Runnable task, long delay, TimeUnit unit) {
        if (task == null || unit == null) {
            throw new NullPointerException();
        }
        if (delay <= 0) {
            throw new IllegalArgumentException("delay must be positive");
        }

        String taskId = "task-" + taskIdGenerator.incrementAndGet();
        Timeout timeout = timer.newTimeout(new TimerTask() {
            @Override
            public void run(Timeout timeout) throws Exception {
                try {
                    task.run();
                } finally {
                    taskMap.remove(taskId);  // 确保任务完成后从映射中移除
                }
            }
        }, delay, unit);
        taskMap.put(taskId, timeout);
        return taskId;
    }

    /**
     * 调度一个固定速率的重复任务
     * <p>
     * 任务将在初始延迟后首次执行，之后每隔固定周期重复执行。
     * </p>
     *
     * @param task 要执行的任务（非null）
     * @param initialDelay 首次执行的延迟时间（必须大于0）
     * @param period 重复执行的周期（必须大于0）
     * @param unit 时间单位（非null）
     * @return 分配给该任务的唯一标识符
     * @throws NullPointerException 如果task或unit为null
     * @throws IllegalArgumentException 如果initialDelay或period <= 0
     */
    @Override
    public String scheduleAtFixedRate(Runnable task, long initialDelay, long period, TimeUnit unit) {
        if (task == null || unit == null) {
            throw new NullPointerException();
        }
        if (initialDelay <= 0 || period <= 0) {
            throw new IllegalArgumentException("initialDelay and period must be positive");
        }

        String taskId = "task-" + taskIdGenerator.incrementAndGet();
        TimerTask timerTask = new TimerTask() {
            @Override
            public void run(Timeout timeout) throws Exception {
                try {
                    task.run();
                } finally {
                    // 只有当任务未被取消时才重新调度
                    if (taskMap.containsKey(taskId)) {
                        timer.newTimeout(this, period, unit);
                    }
                }
            }
        };
        Timeout timeout = timer.newTimeout(timerTask, initialDelay, unit);
        taskMap.put(taskId, timeout);
        return taskId;
    }

    /**
     * 取消指定的任务
     *
     * @param taskId 要取消的任务ID
     * @return 如果任务存在且被成功取消返回true，否则返回false
     */
    @Override
    public boolean cancel(String taskId) {
        if (taskId == null) {
            return false;
        }

        Timeout timeout = taskMap.remove(taskId);
        if (timeout != null) {
            timeout.cancel();
            return true;
        }
        return false;
    }

    /**
     * 关闭调度器
     * <p>
     * 该方法会：
     * <ul>
     *   <li>停止底层时间轮定时器</li>
     *   <li>清除所有已注册的任务</li>
     *   <li>取消所有待处理的任务</li>
     * </ul>
     * 一旦关闭，调度器将不能再接受新任务。
     * </p>
     */
    @Override
    public void shutdown() {
        // 先停止定时器，防止新任务被添加
        timer.stop();

        // 取消所有已注册的任务
        for (Timeout timeout : taskMap.values()) {
            timeout.cancel();
        }

        // 清空任务映射
        taskMap.clear();
    }
}