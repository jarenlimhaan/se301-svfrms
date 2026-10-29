package org.example.sensor;

import java.util.EnumMap;

/**
 * Latest known conditions of one chamber. Written by chamber threads (sensor readings)
 * and by the control thread (device effects), so every access is guarded by this object's lock.
 */
public final class ChamberState {
    private final EnumMap<SensorType, Double> values = new EnumMap<>(SensorType.class);

    public ChamberState() {
        for (SensorType type : SensorType.values()) {
            values.put(type, type.initialValue());
        }
    }

    public synchronized double get(SensorType type) {
        return values.get(type);
    }

    public synchronized void set(SensorType type, double value) {
        values.put(type, value);
    }

    /** Atomically adds {@code delta}, clamped at {@code limit} (a floor when lowering, a ceiling when raising). */
    public synchronized double adjust(SensorType type, double delta, double limit) {
        double next = values.get(type) + delta;
        next = delta < 0 ? Math.max(limit, next) : Math.min(limit, next);
        values.put(type, next);
        return next;
    }
}
