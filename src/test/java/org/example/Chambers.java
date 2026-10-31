package org.example;

import java.time.Duration;
import java.util.List;
import org.example.alert.AlertListener;
import org.example.alert.AlertService;
import org.example.device.ControlDispatcher;
import org.example.device.DeviceListener;
import org.example.device.RequestExecutor;
import org.example.sensor.ChamberControl;
import org.example.sensor.ChamberControls;
import org.example.sensor.SensorType;

/** Test helper: reads a chamber value through the device-facing role, without any test-only API. */
final class Chambers {
    private Chambers() {}

    /** A zero step changes nothing and returns the current value. */
    static double read(ChamberControl chamber, SensorType sensor) {
        return chamber.adjust(sensor, 0, Double.MAX_VALUE);
    }

    static AlertService alerts(List<AlertListener> listeners) {
        return new AlertService(listeners, message -> {});
    }

    static ControlDispatcher dispatcher(ChamberControls chambers, int capacity, List<DeviceListener> listeners,
                                        Duration timeout) {
        return new ControlDispatcher(new RequestExecutor(chambers, listeners, message -> {}), capacity, timeout);
    }
}
