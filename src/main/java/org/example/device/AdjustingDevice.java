package org.example.device;

import org.example.sensor.ChamberState;
import org.example.sensor.SensorType;

/**
 * A device that moves one sensor value by a fixed step, stopping at a limit.
 * {@code effectFormat} receives the new value, e.g. "Temperature reduced to %sC".
 */
public record AdjustingDevice(String name, SensorType sensor, double delta, double limit, String effectFormat)
        implements Device {

    @Override
    public String activate(ChamberState state) {
        double value = state.adjust(sensor, delta, limit);
        return effectFormat.formatted(String.valueOf(value));
    }
}
