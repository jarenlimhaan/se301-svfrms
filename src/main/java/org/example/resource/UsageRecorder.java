package org.example.resource;

/** Records resources consumed. The role monitoring and simulation code depend on. */
public interface UsageRecorder {
    void record(Usage usage);
}
