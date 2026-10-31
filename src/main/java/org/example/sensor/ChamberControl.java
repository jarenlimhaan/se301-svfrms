package org.example.sensor;

/** What a device may do to a chamber: move one sensor value by a step, stopping at a limit. */
public interface ChamberControl {
    /**
     * Atomically adds {@code delta} to the sensor's value, clamped at {@code limit} (a floor when
     * lowering, a ceiling when raising), and returns the new value.
     */
    double adjust(SensorType sensor, double delta, double limit);
}
