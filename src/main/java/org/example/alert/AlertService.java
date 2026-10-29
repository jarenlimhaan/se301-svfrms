package org.example.alert;

import java.util.ArrayList;
import java.util.List;

/**
 * Owns active and archived alerts and the alert counters. All state is guarded by this object's
 * lock so the list and counters always change together; listeners are notified outside the lock.
 */
public final class AlertService {
    private final List<AlertListener> listeners;
    private final List<Alert> active = new ArrayList<>();
    private final List<Alert> archived = new ArrayList<>();
    private int generated;

    public AlertService(List<AlertListener> listeners) {
        this.listeners = List.copyOf(listeners);
    }

    public void raise(Alert alert) {
        synchronized (this) {
            active.add(alert);
            generated++;
        }
        listeners.forEach(l -> l.onAlert(alert));
    }

    /** Atomically moves all active alerts to the archive and returns them. */
    public synchronized List<Alert> archiveActive() {
        List<Alert> moved = List.copyOf(active);
        archived.addAll(moved);
        active.clear();
        return moved;
    }

    public synchronized int generatedCount() { return generated; }

    public synchronized int activeCount() { return active.size(); }

    public synchronized List<Alert> archived() { return List.copyOf(archived); }
}
