package org.example.alert;

/** Where new alerts are sent. The role monitoring code depends on. */
public interface AlertSink {
    void raise(Alert alert);
}
