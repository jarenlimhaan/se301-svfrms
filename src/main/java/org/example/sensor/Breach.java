package org.example.sensor;

/** A reading of {@code sensor} that fell outside its range on the {@code direction} side. */
public record Breach(SensorType sensor, Direction direction) {}
