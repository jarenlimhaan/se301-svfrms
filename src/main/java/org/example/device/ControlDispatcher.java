package org.example.device;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import org.example.sensor.ChamberStates;
import org.example.sensor.Reading;

/**
 * Producer-consumer: chamber threads queue {@link DeviceRequest}s (commands) into a bounded queue;
 * one worker thread executes them in order through a {@link RequestExecutor}. This class only owns
 * the queue and the worker's lifecycle: {@code NEW → RUNNING → SHUTTING_DOWN → TERMINATED}.
 *
 * <ul>
 *   <li>Requests are accepted only while running; afterwards they are rejected, never lost silently.</li>
 *   <li>If the worker dies, every producer blocked on the full queue is woken at once and gets an
 *       {@link IllegalStateException} (no hang, no polling).</li>
 *   <li>{@link #close()} lets the worker drain what is queued, waits at most the shutdown timeout,
 *       and throws if the worker stopped abnormally or requests were left unexecuted.</li>
 * </ul>
 */
public final class ControlDispatcher implements DeviceController, AutoCloseable {
    private enum State { NEW, RUNNING, SHUTTING_DOWN, TERMINATED }

    private static final Duration DEFAULT_SHUTDOWN_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration INTERRUPT_GRACE = Duration.ofMillis(500);

    private final ClosableBoundedQueue<DeviceRequest> queue;
    private final RequestExecutor executor;
    private final Duration shutdownTimeout;
    private final Thread worker = new Thread(this::runWorker, "control-dispatcher");

    private volatile State state = State.NEW;
    /** Why the worker stopped before the queue was closed and drained, or {@code null}. */
    private volatile Throwable workerFailure;

    public ControlDispatcher(ChamberStates chambers, int capacity, List<DeviceListener> listeners) {
        this(chambers, capacity, listeners, DEFAULT_SHUTDOWN_TIMEOUT, StaleRequestPolicy.EXECUTE);
    }

    public ControlDispatcher(ChamberStates chambers, int capacity, List<DeviceListener> listeners,
                             Duration shutdownTimeout) {
        this(chambers, capacity, listeners, shutdownTimeout, StaleRequestPolicy.EXECUTE);
    }

    public ControlDispatcher(ChamberStates chambers, int capacity, List<DeviceListener> listeners,
                             Duration shutdownTimeout, StaleRequestPolicy stalePolicy) {
        this.queue = new ClosableBoundedQueue<>(capacity);
        this.executor = new RequestExecutor(chambers, listeners, Objects.requireNonNull(stalePolicy, "stalePolicy"));
        Objects.requireNonNull(shutdownTimeout, "shutdownTimeout");
        // Thread.join(0) would wait forever, so a zero or negative timeout is a configuration error.
        if (shutdownTimeout.isZero() || shutdownTimeout.isNegative()) {
            throw new IllegalArgumentException("shutdown timeout must be positive: " + shutdownTimeout);
        }
        this.shutdownTimeout = shutdownTimeout;
    }

    public synchronized void start() {
        if (state != State.NEW) throw new IllegalStateException("dispatcher already " + state);
        state = State.RUNNING;
        worker.start();
    }

    /**
     * Queues the request, blocking while the queue is full (back-pressure).
     *
     * @throws IllegalStateException if the dispatcher was never started, is shutting down, or its worker died
     */
    @Override
    public void request(String chamber, Device device, Reading trigger) throws InterruptedException {
        if (state == State.NEW) throw new IllegalStateException("dispatcher not started; request rejected");
        queue.put(new DeviceRequest(chamber, device, trigger));
    }

    /** Outcome counts so far: executed, failed, stale, skipped and listener failures. */
    public DispatchStats stats() {
        return executor.stats();
    }

    private void runWorker() {
        try {
            for (DeviceRequest request = queue.take(); request != null; request = queue.take()) {
                executor.execute(request);
            }
        } catch (InterruptedException e) {
            fail(e);
            Thread.currentThread().interrupt();
        } catch (Throwable t) { // an Error escaping a device: record it rather than dying silently
            fail(t);
        }
    }

    private void fail(Throwable cause) {
        workerFailure = cause;
        queue.abort(cause); // wakes and rejects every waiting producer
    }

    /**
     * Stops accepting requests, lets the worker drain what is already queued, and waits at most the
     * shutdown timeout for it to finish.
     *
     * @throws IllegalStateException if the worker stopped abnormally (queued requests were not all
     *                               executed), did not finish within the shutdown timeout, or the
     *                               closing thread was interrupted (its interrupt flag is restored)
     */
    @Override
    public void close() {
        synchronized (this) {
            if (state == State.NEW) {
                state = State.TERMINATED;
                return;
            }
            if (state != State.RUNNING) return; // already closed
            state = State.SHUTTING_DOWN;
        }
        try {
            queue.close();
            awaitWorker(shutdownTimeout);
            if (worker.isAlive()) {
                worker.interrupt();
                awaitWorker(INTERRUPT_GRACE);
                throw new IllegalStateException("control worker did not finish within " + shutdownTimeout
                        + "; it was interrupted and pending device requests may not have run");
            }
            int notExecuted = queue.drain().size();
            if (workerFailure != null) {
                throw new IllegalStateException("control worker stopped abnormally; " + notExecuted
                        + " queued device request(s) were not executed", workerFailure);
            }
        } finally {
            state = State.TERMINATED;
        }
    }

    /**
     * Waits up to {@code timeout} for the worker. Uses the Duration overload, which never treats a
     * short timeout as "wait forever" the way {@code join(0)} does. AutoCloseable.close should not
     * throw InterruptedException, so the interrupt flag is restored instead.
     */
    private void awaitWorker(Duration timeout) {
        try {
            worker.join(timeout);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            worker.interrupt();
            throw new IllegalStateException("interrupted while waiting for the control worker to finish", e);
        }
    }
}
