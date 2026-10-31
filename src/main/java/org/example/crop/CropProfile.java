package org.example.crop;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.example.resource.Resource;
import org.example.resource.Usage;
import org.example.sensor.Breach;
import org.example.sensor.Direction;
import org.example.sensor.SensorType;

/**
 * Immutable growth requirements of one crop: an acceptable range per monitored sensor and the
 * resources consumed when a range is breached. A sensor with no range is not checked for the crop.
 * Every breach of a configured range gets a response, except the ones explicitly listed with
 * {@link Builder#ignoreBreach(SensorType, Direction)} (used only to preserve legacy behaviour).
 * Built with {@link #builder(String)}.
 */
public final class CropProfile {
    /** Facility-wide minimums that apply to every crop unless overridden. */
    public static final Range DEFAULT_LIGHT = Range.atLeast(500);
    public static final Range DEFAULT_NUTRIENT = Range.atLeast(40);

    private final String name;
    private final Map<SensorType, Range> ranges;
    private final Map<Breach, Usage> costs;
    private final Set<Breach> ignored;

    private CropProfile(Builder builder) {
        this.name = builder.name;
        this.ranges = Map.copyOf(builder.ranges);
        this.costs = Map.copyOf(builder.costs);
        this.ignored = Set.copyOf(builder.ignored);
    }

    public static Builder builder(String name) {
        return new Builder(name);
    }

    public String name() { return name; }

    public Optional<Range> range(SensorType sensor) {
        return Optional.ofNullable(ranges.get(sensor));
    }

    public Optional<Usage> cost(Breach breach) {
        return Optional.ofNullable(costs.get(breach));
    }

    /** Whether this crop deliberately takes no action on {@code breach} (legacy compatibility). */
    public boolean ignores(Breach breach) {
        return ignored.contains(breach);
    }

    public static final class Builder {
        private final String name;
        private final Map<SensorType, Range> ranges = new EnumMap<>(SensorType.class);
        private final Map<Breach, Usage> costs = new HashMap<>();
        private final Set<Breach> ignored = new HashSet<>();

        private Builder(String name) {
            if (name == null || name.isBlank()) throw new IllegalArgumentException("crop name required");
            this.name = name;
            ranges.put(SensorType.LIGHT, DEFAULT_LIGHT);
            ranges.put(SensorType.NUTRIENT, DEFAULT_NUTRIENT);
        }

        public Builder temperature(double min, double max) { return range(SensorType.TEMPERATURE, new Range(min, max)); }

        public Builder humidity(double min, double max) { return range(SensorType.HUMIDITY, new Range(min, max)); }

        public Builder soilMoisture(double min, double max) { return range(SensorType.SOIL_MOISTURE, new Range(min, max)); }

        public Builder light(double min, double max) { return range(SensorType.LIGHT, new Range(min, max)); }

        /**
         * @throws IllegalArgumentException if a finite bound of {@code range} lies outside what the
         *                                  sensor can measure (e.g. a negative humidity percentage)
         */
        public Builder range(SensorType sensor, Range range) {
            checkBound(sensor, range.min());
            checkBound(sensor, range.max());
            ranges.put(sensor, range);
            return this;
        }

        private static void checkBound(SensorType sensor, double bound) {
            if (Double.isInfinite(bound)) return; // an unbounded side; Range already checked its direction
            if (bound < sensor.validMin() || bound > sensor.validMax()) {
                throw new IllegalArgumentException(sensor.label() + " bound " + bound + " is outside the measurable range ["
                        + sensor.validMin() + ", " + sensor.validMax() + "]");
            }
        }

        /** Resources consumed each time {@code sensor} breaches its range in {@code direction}. */
        public Builder cost(SensorType sensor, Direction direction, Resource resource, int amount) {
            costs.put(new Breach(sensor, direction), new Usage(resource, amount));
            return this;
        }

        /**
         * Declares that a breach of {@code sensor}'s range in {@code direction} is deliberately not
         * acted on. Only for preserving legacy behaviour; new crops should not need it.
         */
        public Builder ignoreBreach(SensorType sensor, Direction direction) {
            ignored.add(new Breach(sensor, direction));
            return this;
        }

        public CropProfile build() {
            return new CropProfile(this);
        }
    }
}
