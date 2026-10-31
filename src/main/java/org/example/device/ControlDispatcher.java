package org.example.device;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import org.example.sensor.ChamberControls;

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

    private static final Duration INTERRUPT_GRACE = Duration.ofMillis(500);

    private final ClosableBoundedQueue<DeviceRequest> queue;
    private final RequestExecutor executor;
    private final Duration shutdownTimeout;
    private final Thread worker = new Thread(this::runWorker, "control-dispatcher");

    private volatile State state = State.NEW;
    /** Why the worker stopped before the queue was closed and drained, or {@code null}. */
    private volatile Throwable workerFailure;
    /** 1 if a device Error killed the worker before its request completed. */
    private volatile int lostInFlight;
    private final CountDownLatch closeFinished = new CountDownLatch(1);
    /** The first close outcome is retained so every later close reports the same failure. */
    private volatile IllegalStateException closeFailure;

    public ControlDispatcher(ChamberControls chambers, int capacity, List<DeviceListener> listeners,
                             Duration shutdownTimeout) {
        this.queue = new ClosableBoundedQueue<>(capacity);
        this.executor = new RequestExecutor(chambers, listeners);
        Objects.requireNonNull(shutdownTimeout, "shutdownTimeout");
        // Thread.join(0) would wait forever, so a zero or negative timeout is a configuration error.
        if (shutdownTimeout.isZero() || shutdownTimeout.isNegative()) {
            throw new IllegalArgumentException("shutdown timeout must be positive: " + shutdownTimeout);
        }
        this.shutdownTimeout = shutdownTimeout;
        // A device that ignores interruption must not keep the command-line application alive forever.
        worker.setDaemon(true);
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
    public void request(String chamber, Device device) throws InterruptedException {
        if (state != State.RUNNING) throw new IllegalStateException("dispatcher " + state + "; request rejected");
        queue.put(new DeviceRequest(chamber, device));
    }

    /** Outcome counts so far: executed, failed and listener failures. */
    public DispatchStats stats() {
        return executor.stats();
    }

    private void runWorker() {
        try {
            for (DeviceRequest request = queue.take(); request != null; request = queue.take()) {
                int completedBefore = executor.stats().executed();
                try {
                    executor.execute(request);
                } catch (Throwable t) {
                    // A listener Error escapes after the device completed; only a device Error loses the request.
                    lostInFlight = executor.stats().executed() == completedBefore ? 1 : 0;
                    throw t;
                }
            }
        } catch (InterruptedException e) {
            fail(e);
            Thread.currentThread().interrupt();
        } catch (Throwable t) { // record an escaping Error rather than dying silently
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
        State previous;
        synchronized (this) {
            previous = state;
            if (previous == State.RUNNING) state = State.SHUTTING_DOWN;
            else if (previous == State.NEW) state = State.TERMINATED;
        }
        if (previous == State.NEW) {
            queue.close(); // never started: later requests are rejected, not queued for nobody
            closeFinished.countDown();
            return;
        }
        if (previous != State.RUNNING) { // another thread is closing (or has closed): wait for its drain
            awaitClose();
            if (closeFailure != null) throw closeFailure;
            return;
        }
        try {
            queue.close();
            awaitWorker(shutdownTimeout);
            if (worker.isAlive()) {
                int dropped = queue.drain().size(); // queued work cannot start after close() gives up
                worker.interrupt();
                awaitWorker(INTERRUPT_GRACE);
                throw new IllegalStateException("control worker did not finish within " + shutdownTimeout
                        + "; it was interrupted and " + dropped + " queued device request(s) were dropped");
            }
            int notExecuted = queue.drain().size() + lostInFlight;
            if (workerFailure != null) {
                throw new IllegalStateException("control worker stopped abnormally; " + notExecuted
                        + " queued device request(s) were not executed", workerFailure);
            }
        } catch (IllegalStateException e) {
            closeFailure = e;
            throw e;
        } finally {
            state = State.TERMINATED;
            closeFinished.countDown();
        }
    }

    private void awaitClose() {
        try {
            closeFinished.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted while waiting for dispatcher shutdown", e);
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
