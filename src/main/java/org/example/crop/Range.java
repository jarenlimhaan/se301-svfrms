package org.example.crop;

import java.util.Optional;
import org.example.sensor.Direction;

/**
 * An acceptable [min, max] operating range for one sensor. Bounds must be numbers; an unbounded
 * side is written as -Infinity (min) or +Infinity (max).
 */
public record Range(double min, double max) {

    public Range {
        if (Double.isNaN(min) || Double.isNaN(max)) throw new IllegalArgumentException("range bound is NaN");
        if (min == Double.POSITIVE_INFINITY) throw new IllegalArgumentException("min cannot be +Infinity");
        if (max == Double.NEGATIVE_INFINITY) throw new IllegalArgumentException("max cannot be -Infinity");
        if (min > max) throw new IllegalArgumentException("min " + min + " > max " + max);
    }

    public static Range atLeast(double min) {
        return new Range(min, Double.POSITIVE_INFINITY);
    }

    /**
     * Which side of the range {@code value} is on, or empty if it is within range.
     *
     * @throws IllegalArgumentException if {@code value} is NaN (it is neither in nor out of range)
     */
    public Optional<Direction> breach(double value) {
        if (Double.isNaN(value)) throw new IllegalArgumentException("cannot compare NaN with a range");
        if (value > max) return Optional.of(Direction.HIGH);
        if (value < min) return Optional.of(Direction.LOW);
        return Optional.empty();
    }
}
