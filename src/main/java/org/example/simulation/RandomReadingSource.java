package org.example.simulation;

import java.util.EnumMap;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.concurrent.ThreadLocalRandom;
import org.example.sensor.SensorType;

/** Uniformly random integer readings per sensor, as in the legacy simulation. Always responds. */
public final class RandomReadingSource implements ReadingSource {
    private record Spread(int base, int span) {}

    private final Map<SensorType, Spread> spreads;

    private RandomReadingSource(Map<SensorType, Spread> spreads) {
        for (SensorType sensor : SensorType.values()) {
            if (!spreads.containsKey(sensor)) throw new IllegalArgumentException("no simulated range for " + sensor);
        }
        this.spreads = Map.copyOf(spreads);
    }

    /** The legacy ranges: e.g. temperature {@code 10 + nextInt(30)}, i.e. 10–39 C. */
    public static RandomReadingSource legacy() {
        Map<SensorType, Spread> spreads = new EnumMap<>(SensorType.class);
        spreads.put(SensorType.TEMPERATURE, new Spread(10, 30));
        spreads.put(SensorType.HUMIDITY, new Spread(40, 60));
        spreads.put(SensorType.SOIL_MOISTURE, new Spread(20, 60));
        spreads.put(SensorType.LIGHT, new Spread(300, 900));
        spreads.put(SensorType.NUTRIENT, new Spread(20, 80));
        return new RandomReadingSource(spreads);
    }

    @Override
    public OptionalDouble read(String chamber, SensorType sensor) {
        Spread spread = spreads.get(sensor);
        return OptionalDouble.of(spread.base() + ThreadLocalRandom.current().nextInt(spread.span()));
    }
}
