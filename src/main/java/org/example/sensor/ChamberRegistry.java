package org.example.sensor;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Thread-safe lookup of chamber state; creates a chamber on first use. */
public final class ChamberRegistry implements ChamberStates {
    private final ConcurrentMap<String, ChamberState> chambers = new ConcurrentHashMap<>();

    /** The chamber's live state (atomic get-or-create). */
    public ChamberState state(String chamber) {
        return chambers.computeIfAbsent(chamber, c -> new ChamberState());
    }

    @Override
    public void record(String chamber, SensorType sensor, double value) {
        state(chamber).set(sensor, value);
    }

    @Override
    public ChamberControl control(String chamber) {
        return state(chamber);
    }
}
