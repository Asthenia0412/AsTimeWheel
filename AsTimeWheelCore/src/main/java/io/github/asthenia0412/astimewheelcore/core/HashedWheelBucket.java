package io.github.asthenia0412.astimewheelcore.core;

/**
 * 哈希时间轮的桶实现，用于管理同一时间槽(slot)中的多个Timeout任务。
 * <p>
 * 这个类维护一个双向链表结构来组织Timeout实例，提供添加、移除和过期处理等核心操作。
 * 每个Timeout实例只能属于一个HashedWheelBucket，这是通过维护timeout.bucket引用实现的。
 * </p>
 *
 * <p><b>线程安全说明：</b></p>
 * 这个类不是线程安全的，调用方需要确保外部同步。
 *
 * <p><b>内存一致性：</b></p>
 * 所有对Timeout的修改（包括prev/next/bucket字段）都应由同一个线程完成，
 * 以确保内存可见性。
 */
final class HashedWheelBucket {
    /**
     * 桶中第一个Timeout节点
     */
    private Timeout head;

    /**
     * 桶中最后一个Timeout节点
     */
    private Timeout tail;

    /**
     * 将Timeout添加到桶中。
     * <p>
     * 该方法会将Timeout插入到链表尾部，并维护双向链接关系。
     * </p>
     *
     * @param timeout 要添加的Timeout实例（非null）
     * @throws IllegalStateException 如果timeout已经属于某个桶（timeout.bucket != null）
     *
     * <p><b>边界条件：</b></p>
     * 1. 如果timeout为null，会抛出NullPointerException（由assert检查）
     * 2. 如果timeout已经属于其他桶（timeout.bucket != null），assert会失败
     * 3. 在多线程环境下不加锁可能导致链表损坏
     */
    void addTimeout(Timeout timeout) {
        assert timeout.bucket == null;  // 前置条件检查
        timeout.bucket = this;
        if (head == null) {
            head = tail = timeout;
        } else {
            tail.next = timeout;
            timeout.prev = tail;
            tail = timeout;
        }
    }

    /**
     * 处理过期的Timeout任务。
     * <p>
     * 遍历桶中所有Timeout，检查是否达到触发条件：
     * 1. 如果remainingRounds <= 0且deadline <= 当前时间，执行expire()
     * 2. 否则减少remainingRounds计数
     * </p>
     *
     * @param deadline 过期截止时间（单位：纳秒）
     *
     * <p><b>边界条件：</b></p>
     * 1. 如果链表被并发修改可能导致遍历异常
     * 2. 如果expire()方法抛出异常，会中断处理流程
     * 3. 当系统时间回拨时可能导致错误触发（deadline判断失效）
     */
    void expireTimeouts(long deadline) {
        Timeout timeout = head;
        while (timeout != null) {
            Timeout next = timeout.next;
            if (timeout.getRemainingRounds() <= 0) {
                next = remove(timeout);
                if (timeout.deadline() <= deadline) {
                    timeout.expire();  // 实际触发任务执行
                }
            } else {
                timeout.setRemainingRounds(timeout.getRemainingRounds()-1);
            }
            timeout = next;
        }
    }

    /**
     * 从桶中移除指定的Timeout节点。
     * <p>
     * 该方法会维护链表的前后关系，并清理被移除节点的引用关系。
     * </p>
     *
     * @param timeout 要移除的Timeout实例（非null）
     * @return 下一个待处理的Timeout节点（可能为null）
     *
     * <p><b>边界条件：</b></p>
     * 1. 如果timeout为null，会抛出NullPointerException
     * 2. 如果timeout不属于当前桶（timeout.bucket != this），可能导致状态不一致
     * 3. 移除头节点或尾节点时需要特殊处理
     * 4. 如果timeout既不是头也不是尾节点，但prev/next为null，说明链表已损坏
     */
    Timeout remove(Timeout timeout) {
        Timeout next = timeout.next;

        // 处理前驱节点链接
        if (timeout.prev != null) {
            timeout.prev.next = next;
        }

        // 处理后继节点链接
        if (timeout.next != null) {
            timeout.next.prev = timeout.prev;
        }

        // 处理头尾指针
        if (timeout == head) {
            if (timeout == tail) {
                tail = null;
                head = null;
            } else {
                head = next;
            }
        } else if (timeout == tail) {
            tail = timeout.prev;
        }

        // 清理被移除节点的引用
        timeout.prev = null;
        timeout.next = null;
        timeout.bucket = null;

        return next;
    }
}