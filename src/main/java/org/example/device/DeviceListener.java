package org.example.device;

/** Notified after a device has been activated. */
public interface DeviceListener {
    void onActivated(String chamber, Device device, String effect);
}
