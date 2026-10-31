package org.example.device;

/** Accepts requests to run a device in a chamber. */
public interface DeviceController {
    /**
     * Queues the request, waiting up to the controller's configured timeout while at capacity.
     *
     * @throws IllegalStateException if the controller is not running, has failed, or remains full
     */
    void request(String chamber, Device device) throws InterruptedException;
}
