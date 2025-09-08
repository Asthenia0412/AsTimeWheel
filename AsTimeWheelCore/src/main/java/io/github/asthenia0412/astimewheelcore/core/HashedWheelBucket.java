package io.github.asthenia0412.astimewheelcore.core;

final class HashedWheelBucket {
    private Timeout head;
    private Timeout tail;

    void addTimeout(Timeout timeout) {
        assert timeout.bucket == null;
        timeout.bucket = this;
        if (head == null) {
            head = tail = timeout;
        } else {
            tail.next = timeout;
            timeout.prev = tail;
            tail = timeout;
        }
    }

    void expireTimeouts(long deadline) {
        Timeout timeout = head;
        while (timeout != null) {
            Timeout next = timeout.next;
            if (timeout.getRemainingRounds() <= 0) {
                next = remove(timeout);
                if (timeout.deadline() <= deadline) {
                    timeout.expire();
                }
            } else {
                timeout.setRemainingRounds(timeout.getRemainingRounds()-1);
            }
            timeout = next;
        }
    }

    Timeout remove(Timeout timeout) {
        Timeout next = timeout.next;
        if (timeout.prev != null) {
            timeout.prev.next = next;
        }
        if (timeout.next != null) {
            timeout.next.prev = timeout.prev;
        }
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
        timeout.prev = null;
        timeout.next = null;
        timeout.bucket = null;
        return next;
    }
}