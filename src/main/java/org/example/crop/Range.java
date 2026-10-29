package org.example.crop;

import java.util.Optional;
import org.example.sensor.Direction;

/** An acceptable [min, max] operating range for one sensor. */
public record Range(double min, double max) {

    public Range {
        if (min > max) throw new IllegalArgumentException("min " + min + " > max " + max);
    }

    public static Range atLeast(double min) {
        return new Range(min, Double.POSITIVE_INFINITY);
    }

    /** Which side of the range {@code value} is on, or empty if it is within range. */
    public Optional<Direction> breach(double value) {
        if (value > max) return Optional.of(Direction.HIGH);
        if (value < min) return Optional.of(Direction.LOW);
        return Optional.empty();
    }
}
