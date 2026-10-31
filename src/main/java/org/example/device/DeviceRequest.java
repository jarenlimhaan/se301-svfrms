package org.example.device;

import java.util.Objects;
import org.example.sensor.ChamberControls;

/**
 * Command: a request to run {@code device} in {@code chamber}, created by a chamber thread and
 * executed later by the control thread ({@link RequestExecutor} is the invoker).
 */
public record DeviceRequest(String chamber, Device device) {

    public DeviceRequest {
        Objects.requireNonNull(chamber, "chamber");
        Objects.requireNonNull(device, "device");
    }

    /** Runs the device on its chamber and returns the effect it reports. */
    String execute(ChamberControls chambers) {
        return device.activate(chambers.control(chamber));
    }
}
