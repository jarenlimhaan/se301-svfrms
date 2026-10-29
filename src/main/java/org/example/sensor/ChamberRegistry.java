package org.example.sensor;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Thread-safe lookup of chamber state; creates a chamber on first use. */
public final class ChamberRegistry {
    private final ConcurrentMap<String, ChamberState> chambers = new ConcurrentHashMap<>();

    public ChamberState state(String chamber) {
        return chambers.computeIfAbsent(chamber, c -> new ChamberState());
    }
}
