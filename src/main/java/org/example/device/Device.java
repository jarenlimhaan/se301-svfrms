package org.example.device;

import org.example.sensor.ChamberControl;

/** An automated device that changes a chamber's conditions. */
public interface Device {
    String name();

    /**
     * Applies the device through {@code chamber} and returns a description of the effect. Called on
     * the control thread with no lock held; each {@link ChamberControl#adjust} is atomic.
     */
    String activate(ChamberControl chamber);
}
