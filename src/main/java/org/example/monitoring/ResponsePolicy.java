package org.example.monitoring;

import static org.example.sensor.Direction.HIGH;
import static org.example.sensor.Direction.LOW;
import static org.example.sensor.SensorType.HUMIDITY;
import static org.example.sensor.SensorType.LIGHT;
import static org.example.sensor.SensorType.NUTRIENT;
import static org.example.sensor.SensorType.SOIL_MOISTURE;
import static org.example.sensor.SensorType.TEMPERATURE;

import java.util.Map;
import java.util.Optional;
import org.example.device.Devices;
import org.example.sensor.Breach;

/** Maps each kind of breach to its response. A breach with no response is ignored. */
public final class ResponsePolicy {
    private final Map<Breach, Response> responses;

    public ResponsePolicy(Map<Breach, Response> responses) {
        this.responses = Map.copyOf(responses);
    }

    public static ResponsePolicy standard() {
        return new ResponsePolicy(Map.of(
                new Breach(TEMPERATURE, HIGH), new Response("High Temp", true, Devices.COOLING_FAN),
                new Breach(TEMPERATURE, LOW), new Response("Low Temp", true, null),
                new Breach(HUMIDITY, HIGH), new Response("High Humidity", true, Devices.DEHUMIDIFIER),
                new Breach(HUMIDITY, LOW), new Response("Low Humidity", true, Devices.HUMIDIFIER),
                new Breach(SOIL_MOISTURE, LOW), new Response("Low Soil Moisture", true, Devices.WATER_PUMP),
                new Breach(LIGHT, LOW), new Response("Low Light Level", false, Devices.GROW_LIGHT),
                new Breach(NUTRIENT, LOW), new Response("Low Nutrient Level", false, Devices.NUTRIENT_INJECTOR)));
    }

    public Optional<Response> responseTo(Breach breach) {
        return Optional.ofNullable(responses.get(breach));
    }
}
