package org.example.device;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.example.sensor.ChamberControls;
import org.example.support.Listeners;

/**
 * Invoker for {@link DeviceRequest} commands: executes one, notifies listeners, and
 * counts outcomes. Device code runs outside every lock; each change it makes is one atomic
 * {@code adjust} call.
 */
final class RequestExecutor {
    private final ChamberControls chambers;
    private final Listeners<DeviceListener> listeners;
    private final AtomicInteger executed = new AtomicInteger();
    private final AtomicInteger failed = new AtomicInteger();

    RequestExecutor(ChamberControls chambers, List<DeviceListener> listeners) {
        this.chambers = chambers;
        this.listeners = new Listeners<>("CONTROL", listeners);
    }

    void execute(DeviceRequest request) {
        String effect;
        try {
            effect = request.execute(chambers);
        } catch (RuntimeException e) {
            // A failing device must not kill the worker: log it, count it, carry on.
            failed.incrementAndGet();
            System.err.println("[" + request.chamber() + "][CONTROL] " + request.device().name() + " failed: " + e);
            return;
        }
        executed.incrementAndGet();
        listeners.notifyEach(request.chamber(), l -> l.onActivated(request.chamber(), request.device(), effect));
    }

    DispatchStats stats() {
        return new DispatchStats(executed.get(), failed.get(), listeners.failures());
    }
}
