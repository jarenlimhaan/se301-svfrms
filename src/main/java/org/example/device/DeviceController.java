package org.example.device;

/** Accepts requests to run a device in a chamber. */
public interface DeviceController {
    /** Queues the request, blocking while the controller is at capacity. */
    void request(String chamber, Device device) throws InterruptedException;
}
