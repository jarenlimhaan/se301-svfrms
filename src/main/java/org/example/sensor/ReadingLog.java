package org.example.sensor;

/** Records every processed reading. */
public interface ReadingLog {
    void add(String entry);
}
