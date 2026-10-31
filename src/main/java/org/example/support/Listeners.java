package org.example.support;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * An immutable set of independent listeners. Each is notified in turn; one that throws is logged
 * to stderr and counted, and the rest are still notified, so a faulty listener can never stop
 * the thread that published the event or starve the other listeners.
 */
public final class Listeners<L> {
    private final String channel;
    private final List<L> listeners;
    private final AtomicInteger failures = new AtomicInteger();

    public Listeners(String channel, List<L> listeners) {
        this.channel = channel;
        this.listeners = List.copyOf(listeners);
    }

    /** Calls {@code event} on every listener; {@code source} (e.g. the chamber) is used in failure logs. */
    public void notifyEach(String source, Consumer<L> event) {
        for (L listener : listeners) {
            try {
                event.accept(listener);
            } catch (RuntimeException e) {
                failures.incrementAndGet();
                System.err.println("[" + source + "][" + channel + "] listener "
                        + listener.getClass().getSimpleName() + " failed: " + e);
            }
        }
    }

    public int failures() {
        return failures.get();
    }
}
