package org.example.sensor;

/**
 * Chamber state seen by role (ISP): monitoring records readings, devices adjust values. A chamber
 * is created on first use.
 */
public interface ChamberStates {
    /**
     * Records a sensor reading.
     *
     * @throws IllegalArgumentException if {@code value} is not a valid reading for {@code sensor}
     */
    void record(String chamber, SensorType sensor, double value);

    /** What a device may change in {@code chamber}. */
    ChamberControl control(String chamber);
}
