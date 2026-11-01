package org.example.sensor;

/** Hands devices the one thing they may change: a chamber's {@link ChamberControl}. */
public interface ChamberControls {
    ChamberControl control(String chamber);
}
