package org.example.device;

import org.example.sensor.Reading;

/** Accepts requests to run a device in a chamber. */
public interface DeviceController {
    /**
     * Queues the request, blocking while the controller is at capacity.
     *
     * @param trigger the reading that caused the request, so the controller can tell whether it is
     *                stale when it runs; {@code null} for a request not tied to a reading
     */
    void request(String chamber, Device device, Reading trigger) throws InterruptedException;

    /** Queues a request that is not tied to any reading. */
    default void request(String chamber, Device device) throws InterruptedException {
        request(chamber, device, null);
    }
}
