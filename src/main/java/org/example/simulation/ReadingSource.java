package org.example.simulation;

import java.util.OptionalDouble;
import org.example.sensor.SensorType;

/**
 * Strategy for where readings come from: the random simulation, recorded data, or real hardware.
 * An empty result means the sensor did not respond.
 */
public interface ReadingSource {
    OptionalDouble read(String chamber, SensorType sensor);
}
