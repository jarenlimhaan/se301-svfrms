package org.example.device;

import java.util.Objects;
import org.example.sensor.Reading;

/**
 * Command: a request to run {@code device} in {@code chamber}, created by the monitoring thread
 * and executed later by the control thread.
 *
 * @param trigger the reading that caused the request, or {@code null} if it is not tied to one
 */
public record DeviceRequest(String chamber, Device device, Reading trigger) {

    public DeviceRequest {
        Objects.requireNonNull(chamber, "chamber");
        Objects.requireNonNull(device, "device");
    }
}
