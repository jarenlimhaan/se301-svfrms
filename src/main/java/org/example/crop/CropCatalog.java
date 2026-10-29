package org.example.crop;

import static org.example.resource.Resource.ENERGY;
import static org.example.resource.Resource.WATER;
import static org.example.sensor.Direction.HIGH;
import static org.example.sensor.Direction.LOW;
import static org.example.sensor.SensorType.HUMIDITY;
import static org.example.sensor.SensorType.SOIL_MOISTURE;
import static org.example.sensor.SensorType.TEMPERATURE;

import java.util.List;

/**
 * The facility's crop profiles. Adding a crop is one new entry here; removing a crop is deleting
 * its entry. No monitoring, alerting or control code changes.
 */
public final class CropCatalog {
    private CropCatalog() {}

    public static List<CropProfile> defaults() {
        return List.of(
                // Legacy crops: thresholds and resource costs identical to the original SVFRMS.
                CropProfile.builder("Tomato")
                        .temperature(22, 28).humidity(60, 80).soilMoisture(45, 60)
                        .cost(TEMPERATURE, HIGH, ENERGY, 10).cost(TEMPERATURE, LOW, ENERGY, 15)
                        .cost(HUMIDITY, LOW, WATER, 20).cost(SOIL_MOISTURE, LOW, WATER, 50)
                        .build(),
                CropProfile.builder("Strawberry")
                        .temperature(18, 24).humidity(70, 85)
                        .cost(TEMPERATURE, HIGH, ENERGY, 10).cost(TEMPERATURE, LOW, ENERGY, 15)
                        .cost(HUMIDITY, LOW, WATER, 25)
                        .build(),
                CropProfile.builder("Lettuce").temperature(15, 21).build(),
                CropProfile.builder("Spinach").temperature(10, 20).build(),
                CropProfile.builder("Chili").temperature(24, 32).build(),
                CropProfile.builder("Cucumber").temperature(18, 27).build(),

                // New crops (feature extension, spec 4.5).
                CropProfile.builder("Kale").temperature(15, 24).humidity(60, 80).soilMoisture(50, 70).build(),
                CropProfile.builder("Bell Pepper").temperature(21, 29).humidity(60, 75).soilMoisture(50, 70).build(),
                CropProfile.builder("Bok Choy").temperature(15, 22).humidity(60, 80).soilMoisture(55, 75).build(),
                CropProfile.builder("Eggplant").temperature(22, 30).humidity(60, 75).soilMoisture(50, 70).build());
    }

    public static CropRegistry defaultRegistry() {
        CropRegistry registry = new CropRegistry();
        defaults().forEach(registry::register);
        return registry;
    }
}
