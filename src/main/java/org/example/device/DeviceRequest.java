package org.example.device;

import java.util.Objects;

/**
 * Command: a request to run {@code device} in {@code chamber}, created by a chamber thread and
 * executed later by the control thread ({@link Device#activate} is the action).
 */
public record DeviceRequest(String chamber, Device device) {

    public DeviceRequest {
        Objects.requireNonNull(chamber, "chamber");
        Objects.requireNonNull(device, "device");
    }
}
