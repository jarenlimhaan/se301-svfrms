package org.example.device;

import java.util.ArrayDeque;
import java.util.List;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * A bounded blocking queue that can be closed. {@code ArrayBlockingQueue} has no closed state, so a
 * producer blocked in {@code put} on a full queue waits forever if the consumer dies. Here,
 * {@link #close()} and {@link #abort(Throwable)} wake every waiting thread at once (signalling, not
 * polling), and every {@code put} after that is rejected, so no request can slip in after shutdown.
 * One lock guards all state; no other lock is ever taken while it is held.
 */
final class ClosableBoundedQueue<T> {
    private final ReentrantLock lock = new ReentrantLock();
    private final Condition notEmpty = lock.newCondition();
    private final Condition notFull = lock.newCondition();
    private final ArrayDeque<T> items;
    private final int capacity;
    private boolean closed;
    private Throwable abortCause;

    ClosableBoundedQueue(int capacity) {
        if (capacity < 1) throw new IllegalArgumentException("capacity must be at least 1: " + capacity);
        this.capacity = capacity;
        this.items = new ArrayDeque<>(capacity);
    }

    /**
     * Adds {@code item}, waiting while the queue is full.
     *
     * @throws IllegalStateException if the queue is closed or aborted, before or while waiting
     */
    void put(T item) throws InterruptedException {
        lock.lockInterruptibly();
        try {
            while (items.size() == capacity && !closed) notFull.await();
            if (closed) {
                throw abortCause == null
                        ? new IllegalStateException("queue closed; request rejected")
                        : new IllegalStateException("consumer stopped abnormally; request rejected", abortCause);
            }
            items.addLast(item);
            notEmpty.signal();
        } finally {
            lock.unlock();
        }
    }

    /** Removes the next item, waiting while the queue is empty and open; {@code null} once closed and drained. */
    T take() throws InterruptedException {
        lock.lockInterruptibly();
        try {
            while (items.isEmpty() && !closed) notEmpty.await();
            T item = items.pollFirst();
            if (item != null) notFull.signal();
            return item;
        } finally {
            lock.unlock();
        }
    }

    /** Graceful close: rejects new items; the consumer still drains what is queued. */
    void close() {
        lock.lock();
        try {
            closed = true;
            notEmpty.signalAll();
            notFull.signalAll();
        } finally {
            lock.unlock();
        }
    }

    /** The consumer has died: rejects new items and wakes blocked producers with {@code cause}. */
    void abort(Throwable cause) {
        lock.lock();
        try {
            closed = true;
            abortCause = cause;
            notEmpty.signalAll();
            notFull.signalAll();
        } finally {
            lock.unlock();
        }
    }

    /** Removes and returns everything still queued (items that were never consumed). */
    List<T> drain() {
        lock.lock();
        try {
            List<T> rest = List.copyOf(items);
            items.clear();
            notFull.signalAll();
            return rest;
        } finally {
            lock.unlock();
        }
    }
}
