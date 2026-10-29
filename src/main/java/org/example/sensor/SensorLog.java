package org.example.sensor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/** Timestamped record of every processed sensor reading. */
public final class SensorLog {
    private final List<String> entries = Collections.synchronizedList(new ArrayList<>());

    public void add(String message) {
        entries.add(new Date() + " : " + message);
    }

    public List<String> entries() {
        synchronized (entries) {
            return List.copyOf(entries);
        }
    }
}
