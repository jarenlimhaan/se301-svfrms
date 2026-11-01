package org.example.alert;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import org.example.support.Listeners;

/**
 * Owns active and archived alerts and the alert counters. All state is guarded by this object's
 * lock so the list and counters always change together; listeners are notified outside the lock,
 * and a listener that throws is isolated (see {@link Listeners}).
 */
public final class AlertService implements AlertSink, AlertLedger {
    private final Listeners<AlertListener> listeners;
    private final List<Alert> active = new ArrayList<>();
    private final List<Alert> archived = new ArrayList<>();
    private int generated;

    public AlertService(List<AlertListener> listeners, Consumer<String> errors) {
        this.listeners = new Listeners<>("ALERT", listeners, errors);
    }

    @Override
    public void raise(Alert alert) {
        synchronized (this) {
            active.add(alert);
            generated++;
        }
        listeners.notifyEach(alert.chamber(), l -> l.onAlert(alert));
    }

    @Override
    public synchronized List<Alert> archiveActive() {
        List<Alert> moved = List.copyOf(active);
        archived.addAll(moved);
        active.clear();
        return moved;
    }

    @Override
    public synchronized int generatedCount() { return generated; }

    @Override
    public synchronized int activeCount() { return active.size(); }

    @Override
    public synchronized List<Alert> archived() { return List.copyOf(archived); }

    /** Number of listener notifications that threw (each one was logged and skipped). */
    public int listenerFailures() { return listeners.failures(); }
}
