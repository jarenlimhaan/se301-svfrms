package org.example.sensor;

/** What monitoring may do to a chamber: record a sensor reading. A chamber is created on first use. */
public interface ReadingRecorder {
    /** @throws IllegalArgumentException if {@code value} is not a valid reading for {@code sensor} */
    void record(String chamber, SensorType sensor, double value);
}
