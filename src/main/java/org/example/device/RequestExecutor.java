package org.example.device;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.example.sensor.ChamberControl;
import org.example.sensor.ChamberState;
import org.example.sensor.ChamberStates;
import org.example.support.Listeners;

/**
 * Executes one {@link DeviceRequest}: applies the device to its chamber, checking whether the
 * triggering reading is stale (see {@link StaleRequestPolicy}), notifies listeners, and counts outcomes.
 * The staleness check and each change happen in one atomic {@link ChamberState#adjustChecked} call,
 * so device code itself never runs under the chamber's lock.
 */
final class RequestExecutor {
    /** Thrown inside a device's {@code adjust} call to abandon a stale request under SKIP. Never escapes. */
    private static final class StaleRequest extends RuntimeException {
        private static final long serialVersionUID = 1L;

        StaleRequest() {
            super(null, null, false, false);
        }
    }

    private final ChamberStates chambers;
    private final Listeners<DeviceListener> listeners;
    private final StaleRequestPolicy stalePolicy;
    private final AtomicInteger executed = new AtomicInteger();
    private final AtomicInteger failed = new AtomicInteger();
    private final AtomicInteger stale = new AtomicInteger();
    private final AtomicInteger skipped = new AtomicInteger();

    RequestExecutor(ChamberStates chambers, List<DeviceListener> listeners, StaleRequestPolicy stalePolicy) {
        this.chambers = chambers;
        this.listeners = new Listeners<>("CONTROL", listeners);
        this.stalePolicy = stalePolicy;
    }

    void execute(DeviceRequest request) {
        ChamberState state = chambers.state(request.chamber());
        boolean skipIfStale = stalePolicy == StaleRequestPolicy.SKIP;
        boolean[] sawStale = {false};
        ChamberControl guarded = (sensor, delta, limit) -> {
            ChamberState.Adjustment result = state.adjustChecked(request.trigger(), skipIfStale, sensor, delta, limit);
            if (result.stale()) sawStale[0] = true;
            if (!result.applied()) throw new StaleRequest();
            return result.value();
        };
        String effect;
        try {
            effect = request.device().activate(guarded);
        } catch (StaleRequest e) {
            stale.incrementAndGet();
            skipped.incrementAndGet();
            return;
        } catch (RuntimeException e) {
            // A failing device must not kill the worker: log it, count it, carry on.
            failed.incrementAndGet();
            System.err.println("[" + request.chamber() + "][CONTROL] " + request.device().name() + " failed: " + e);
            return;
        }
        if (sawStale[0]) stale.incrementAndGet();
        executed.incrementAndGet();
        listeners.notifyEach(request.chamber(), l -> l.onActivated(request.chamber(), request.device(), effect));
    }

    DispatchStats stats() {
        return new DispatchStats(executed.get(), failed.get(), stale.get(), skipped.get(), listeners.failures());
    }
}
