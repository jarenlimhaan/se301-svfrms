package org.example.crop;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.example.resource.Resource;
import org.example.resource.Usage;
import org.example.sensor.Breach;
import org.example.sensor.Direction;
import org.example.sensor.SensorType;

/**
 * Immutable growth requirements of one crop: an acceptable range per monitored sensor and the
 * resources consumed when a range is breached. A sensor with no range is not checked for the crop.
 * Built with {@link #builder(String)}.
 */
public final class CropProfile {
    /** Facility-wide minimums that apply to every crop unless overridden. */
    public static final Range DEFAULT_LIGHT = Range.atLeast(500);
    public static final Range DEFAULT_NUTRIENT = Range.atLeast(40);

    private final String name;
    private final Map<SensorType, Range> ranges;
    private final Map<Breach, Usage> costs;

    private CropProfile(Builder builder) {
        this.name = builder.name;
        this.ranges = Map.copyOf(builder.ranges);
        this.costs = Map.copyOf(builder.costs);
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

    public static final class Builder {
        private final String name;
        private final Map<SensorType, Range> ranges = new EnumMap<>(SensorType.class);
        private final Map<Breach, Usage> costs = new HashMap<>();

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

        public Builder range(SensorType sensor, Range range) {
            ranges.put(sensor, range);
            return this;
        }

        /** Resources consumed each time {@code sensor} breaches its range in {@code direction}. */
        public Builder cost(SensorType sensor, Direction direction, Resource resource, int amount) {
            costs.put(new Breach(sensor, direction), new Usage(resource, amount));
            return this;
        }

        public CropProfile build() {
            return new CropProfile(this);
        }
    }
}
