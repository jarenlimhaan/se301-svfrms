package org.example.sensor;

import java.util.EnumMap;

/**
 * Latest known conditions of one chamber. Written by chamber threads (sensor readings)
 * and by the control thread (device effects), so every access is guarded by this object's lock.
 * No foreign code ever runs under the lock.
 */
public final class ChamberState implements ChamberControl {
    private final EnumMap<SensorType, Double> values = new EnumMap<>(SensorType.class);

    public ChamberState() {
        for (SensorType type : SensorType.values()) values.put(type, type.initialValue());
    }

    public synchronized double get(SensorType type) {
        return values.get(type);
    }

    /**
     * Records a sensor reading.
     *
     * @throws IllegalArgumentException if {@code value} is not a valid reading for {@code type}
     */
    public synchronized void set(SensorType type, double value) {
        if (!type.isValid(value)) {
            throw new IllegalArgumentException("invalid " + type.label() + " reading: " + value);
        }
        values.put(type, value);
    }

    @Override
    public synchronized double adjust(SensorType type, double delta, double limit) {
        if (!Double.isFinite(delta) || !Double.isFinite(limit)) {
            throw new IllegalArgumentException("delta and limit must be finite: " + delta + ", " + limit);
        }
        double next = values.get(type) + delta;
        next = delta < 0 ? Math.max(limit, next) : Math.min(limit, next);
        values.put(type, next);
        return next;
    }
}
