package org.example.device;

import org.example.sensor.ChamberState;

/** An automated device that changes a chamber's conditions. */
public interface Device {
    String name();

    /** Applies the device to {@code state} and returns a description of the effect. */
    String activate(ChamberState state);
}
