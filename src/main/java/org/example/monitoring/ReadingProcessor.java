package org.example.monitoring;

import org.example.sensor.SensorType;

/** Accepts sensor readings from a chamber. The role a sensor feed (simulated or real) depends on. */
public interface ReadingProcessor {
    /** Handles one reading; may block while the device queue is full. */
    void process(String chamber, String crop, SensorType sensor, double value) throws InterruptedException;

    /** Handles a sensor that gave no reading this cycle (unresponsive). */
    void reportMissing(String chamber, String crop, SensorType sensor);
}
