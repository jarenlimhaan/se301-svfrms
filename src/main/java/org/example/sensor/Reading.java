package org.example.sensor;

/**
 * Identifies one accepted sensor reading in a chamber: {@code version} counts the readings of
 * {@code sensor} recorded so far, so a later reading of the same sensor always has a higher version.
 */
public record Reading(SensorType sensor, long version) {}
