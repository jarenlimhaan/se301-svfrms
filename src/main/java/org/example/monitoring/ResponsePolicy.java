package org.example.monitoring;

import static org.example.alert.Severity.CRITICAL;
import static org.example.alert.Severity.INFO;
import static org.example.alert.Severity.WARNING;
import static org.example.sensor.Direction.HIGH;
import static org.example.sensor.Direction.LOW;
import static org.example.sensor.SensorType.HUMIDITY;
import static org.example.sensor.SensorType.LIGHT;
import static org.example.sensor.SensorType.NUTRIENT;
import static org.example.sensor.SensorType.SOIL_MOISTURE;
import static org.example.sensor.SensorType.TEMPERATURE;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.example.device.Devices;
import org.example.sensor.Breach;
import org.example.sensor.Direction;
import org.example.sensor.SensorType;

/**
 * Maps each kind of breach to its response. The policy must be complete: every sensor and
 * direction has a response, so a breach of any configured crop range is never silently dropped.
 * Breaches a crop deliberately ignores are declared on the crop ({@code CropProfile#ignores}).
 */
public final class ResponsePolicy {
    private final Map<Breach, Response> responses;

    /** @throws IllegalArgumentException if any sensor/direction combination has no response */
    public ResponsePolicy(Map<Breach, Response> responses) {
        List<Breach> missing = new ArrayList<>();
        for (SensorType sensor : SensorType.values()) {
            for (Direction direction : Direction.values()) {
                Breach breach = new Breach(sensor, direction);
                if (!responses.containsKey(breach)) missing.add(breach);
            }
        }
        if (!missing.isEmpty()) throw new IllegalArgumentException("no response for " + missing);
        this.responses = Map.copyOf(responses);
    }

    /**
     * The facility's responses. Alert texts and devices are those of the legacy system; the
     * severities follow spec §3.4 (temperature breaches are CRITICAL, as in its example).
     */
    public static ResponsePolicy standard() {
        return new ResponsePolicy(Map.of(
                new Breach(TEMPERATURE, HIGH), new Response("High Temp", CRITICAL, true, Devices.COOLING_FAN),
                new Breach(TEMPERATURE, LOW), new Response("Low Temp", CRITICAL, true, null),
                new Breach(HUMIDITY, HIGH), new Response("High Humidity", WARNING, true, Devices.DEHUMIDIFIER),
                new Breach(HUMIDITY, LOW), new Response("Low Humidity", WARNING, true, Devices.HUMIDIFIER),
                new Breach(SOIL_MOISTURE, HIGH), new Response("High Soil Moisture", WARNING, true, null),
                new Breach(SOIL_MOISTURE, LOW), new Response("Low Soil Moisture", WARNING, true, Devices.WATER_PUMP),
                // Light and nutrient have no default maximum, so these fire only for crops that set one.
                new Breach(LIGHT, HIGH), new Response("High Light Level", INFO, false, null),
                new Breach(LIGHT, LOW), new Response("Low Light Level", INFO, false, Devices.GROW_LIGHT),
                new Breach(NUTRIENT, HIGH), new Response("High Nutrient Level", WARNING, false, null),
                new Breach(NUTRIENT, LOW), new Response("Low Nutrient Level", WARNING, false, Devices.NUTRIENT_INJECTOR)));
    }

    public Response responseTo(Breach breach) {
        return responses.get(breach);
    }
}
