package org.example.sensor;

import java.util.EnumMap;

/**
 * Latest known conditions of one chamber. Written by chamber threads (sensor readings)
 * and by the control thread (device effects), so every access is guarded by this object's lock.
 * Each accepted reading gets a version number so a queued device action can tell whether the
 * reading that triggered it has since been superseded. No foreign code ever runs under the lock.
 */
public final class ChamberState implements ChamberControl {

    /** Result of {@link #adjustChecked}: whether the trigger was stale, whether the change was made, the value after. */
    public record Adjustment(boolean stale, boolean applied, double value) {}

    private final EnumMap<SensorType, Double> values = new EnumMap<>(SensorType.class);
    private final EnumMap<SensorType, Long> versions = new EnumMap<>(SensorType.class);

    public ChamberState() {
        for (SensorType type : SensorType.values()) {
            values.put(type, type.initialValue());
            versions.put(type, 0L);
        }
    }

    public synchronized double get(SensorType type) {
        return values.get(type);
    }

    /**
     * Records a sensor reading and returns its identity.
     *
     * @throws IllegalArgumentException if {@code value} is not a valid reading for {@code type}
     */
    public synchronized Reading set(SensorType type, double value) {
        if (!type.isValid(value)) {
            throw new IllegalArgumentException("invalid " + type.label() + " reading: " + value);
        }
        values.put(type, value);
        long version = versions.merge(type, 1L, Long::sum);
        return new Reading(type, version);
    }

    /** Whether {@code reading} is still the latest reading of its sensor. */
    public synchronized boolean isCurrent(Reading reading) {
        return versions.get(reading.sensor()) == reading.version();
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

    /**
     * Like {@link #adjust}, but first checks, in the same atomic step, whether {@code trigger} is
     * still the latest reading. If it is stale and {@code skipIfStale}, nothing changes.
     *
     * @param trigger the reading that caused the adjustment, or {@code null} if none (never stale)
     */
    public synchronized Adjustment adjustChecked(Reading trigger, boolean skipIfStale,
                                                 SensorType type, double delta, double limit) {
        boolean stale = trigger != null && !isCurrent(trigger);
        if (stale && skipIfStale) return new Adjustment(true, false, values.get(type));
        return new Adjustment(stale, true, adjust(type, delta, limit));
    }
}
