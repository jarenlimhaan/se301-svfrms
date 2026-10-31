package org.example.sensor;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Thread-safe lookup of chamber state; creates a chamber on first use. Monitoring sees it only as a
 * {@link ReadingRecorder}, devices only as {@link ChamberControls} (ISP).
 */
public final class ChamberRegistry implements ReadingRecorder, ChamberControls {
    private final ConcurrentMap<String, ChamberState> chambers = new ConcurrentHashMap<>();

    /** The chamber's live state (atomic get-or-create). */
    private ChamberState state(String chamber) {
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
