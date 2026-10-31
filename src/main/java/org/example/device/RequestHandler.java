package org.example.device;

/** Executes queued device commands and exposes their outcome counts. */
public interface RequestHandler {
    void execute(DeviceRequest request);

    DispatchStats stats();
}
