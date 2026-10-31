package org.example;

import org.example.sensor.ChamberControl;
import org.example.sensor.SensorType;

/** Test helper: reads a chamber value through the device-facing role, without any test-only API. */
final class Chambers {
    private Chambers() {}

    /** A zero step changes nothing and returns the current value. */
    static double read(ChamberControl chamber, SensorType sensor) {
        return chamber.adjust(sensor, 0, Double.MAX_VALUE);
    }
}
