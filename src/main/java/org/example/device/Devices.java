package org.example.device;

import static org.example.sensor.SensorType.HUMIDITY;
import static org.example.sensor.SensorType.LIGHT;
import static org.example.sensor.SensorType.NUTRIENT;
import static org.example.sensor.SensorType.SOIL_MOISTURE;
import static org.example.sensor.SensorType.TEMPERATURE;

/** The devices installed in every chamber. */
public final class Devices {
    private Devices() {}

    public static final Device COOLING_FAN =
            new AdjustingDevice("Cooling Fan", TEMPERATURE, -5, 18, "Temperature reduced to %sC");
    public static final Device WATER_PUMP =
            new AdjustingDevice("Water Pump", SOIL_MOISTURE, 20, 100, "Soil moisture increased to %s%%");
    public static final Device NUTRIENT_INJECTOR =
            new AdjustingDevice("Nutrient Injector", NUTRIENT, 15, 100, "Nutrient level increased to %s%%");
    public static final Device HUMIDIFIER =
            new AdjustingDevice("Humidifier", HUMIDITY, 10, 100, "Humidity increased to %s%%");
    public static final Device DEHUMIDIFIER =
            new AdjustingDevice("Dehumidifier", HUMIDITY, -10, 30, "Humidity reduced to %s%%");
    public static final Device GROW_LIGHT =
            new AdjustingDevice("Grow Light", LIGHT, 150, 1000, "Light level increased to %s lux");
}
