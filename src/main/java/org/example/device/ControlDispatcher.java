package org.example.device;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import org.example.sensor.ChamberRegistry;

/**
 * Producer-consumer: chamber threads queue device requests into a bounded queue; one worker
 * thread executes them in order. {@link #close()} drains outstanding requests, then stops the worker.
 */
public final class ControlDispatcher implements DeviceController, AutoCloseable {
    private record Request(String chamber, Device device) {}

    /** Poison pill: tells the worker to stop once everything queued before it is done. */
    private static final Request STOP = new Request("", null);

    private final BlockingQueue<Request> queue;
    private final ChamberRegistry chambers;
    private final List<DeviceListener> listeners;
    private final Thread worker = new Thread(this::runWorker, "control-dispatcher");

    public ControlDispatcher(ChamberRegistry chambers, int capacity, List<DeviceListener> listeners) {
        this.chambers = chambers;
        this.queue = new ArrayBlockingQueue<>(capacity);
        this.listeners = List.copyOf(listeners);
    }

    public void start() {
        worker.start();
    }

    @Override
    public void request(String chamber, Device device) throws InterruptedException {
        queue.put(new Request(chamber, Objects.requireNonNull(device, "device")));
    }

    private void runWorker() {
        try {
            for (Request request = queue.take(); request != STOP; request = queue.take()) {
                execute(request);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void execute(Request request) {
        // A failing device must not kill the worker, or producers would block on a full queue forever.
        try {
            String effect = request.device().activate(chambers.state(request.chamber()));
            listeners.forEach(l -> l.onActivated(request.chamber(), request.device(), effect));
        } catch (RuntimeException e) {
            System.err.println("[" + request.chamber() + "][CONTROL] " + request.device().name() + " failed: " + e);
        }
    }

    @Override
    public void close() throws InterruptedException {
        queue.put(STOP);
        worker.join();
    }
}
