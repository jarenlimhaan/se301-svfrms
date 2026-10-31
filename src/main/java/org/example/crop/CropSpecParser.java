package org.example.crop;

import java.util.Arrays;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.example.sensor.SensorType;

/**
 * Parses a crop definition written as text, so crops can be added at run time without editing or
 * rebuilding any Java code. Format (one crop):
 *
 * <pre>Name:sensor=min..max,sensor=min..max,...</pre>
 *
 * Sensors are named by {@link SensorType#key()}: {@code temperature}, {@code humidity}, {@code soil},
 * {@code light} and {@code nutrient}; leaving out max ({@code light=600..}) means no upper limit. Light and nutrient
 * keep the facility-wide minimums unless given. Example: {@code Okra:temperature=24..30,soil=50..70}.
 * Values are validated exactly like the built-in catalog (see {@link CropProfile.Builder#range}).
 */
public final class CropSpecParser {
    /** Sensor keys come from {@link SensorType#key()}, so a new sensor needs no change here. */
    private static final Map<String, SensorType> SENSORS = Arrays.stream(SensorType.values())
            .collect(Collectors.toUnmodifiableMap(SensorType::key, Function.identity()));

    private CropSpecParser() {}

    /** @throws IllegalArgumentException with a message naming the problem if {@code spec} is malformed */
    public static CropProfile parse(String spec) {
        int colon = spec.indexOf(':');
        if (colon < 0) throw invalid(spec, "expected Name:sensor=min..max,...");
        String name = spec.substring(0, colon).trim();
        if (name.isEmpty()) throw invalid(spec, "crop name is empty");
        String body = spec.substring(colon + 1).trim();
        if (body.isEmpty()) throw invalid(spec, "no ranges given");

        CropProfile.Builder builder = CropProfile.builder(name);
        for (String entry : body.split(",")) {
            String[] keyValue = entry.split("=", 2);
            if (keyValue.length != 2) throw invalid(spec, "expected sensor=min..max, got '" + entry.trim() + "'");
            SensorType sensor = SENSORS.get(keyValue[0].trim().toLowerCase(Locale.ROOT));
            if (sensor == null) throw invalid(spec, "unknown sensor '" + keyValue[0].trim() + "'; known: " + new TreeSet<>(SENSORS.keySet()));
            try {
                builder.range(sensor, parseRange(keyValue[1].trim()));
            } catch (IllegalArgumentException e) {
                throw invalid(spec, keyValue[0].trim() + ": " + e.getMessage());
            }
        }
        return builder.build();
    }

    private static Range parseRange(String text) {
        int dots = text.indexOf("..");
        if (dots < 0) throw new IllegalArgumentException("expected min..max, got '" + text + "'");
        String min = text.substring(0, dots).trim();
        String max = text.substring(dots + 2).trim();
        if (min.isEmpty()) throw new IllegalArgumentException("min is required in '" + text + "'");
        return new Range(number(min), max.isEmpty() ? Double.POSITIVE_INFINITY : number(max));
    }

    private static double number(String text) {
        try {
            double value = Double.parseDouble(text);
            if (!Double.isFinite(value)) throw new NumberFormatException();
            return value;
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("'" + text + "' is not a number");
        }
    }

    private static IllegalArgumentException invalid(String spec, String reason) {
        return new IllegalArgumentException("invalid crop definition '" + spec + "': " + reason);
    }
}
