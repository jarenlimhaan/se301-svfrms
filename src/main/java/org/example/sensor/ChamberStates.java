package org.example.sensor;

/** Looks up a chamber's live state, creating it on first use. */
public interface ChamberStates {
    ChamberState state(String chamber);
}
