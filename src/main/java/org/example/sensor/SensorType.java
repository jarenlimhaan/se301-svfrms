package org.example.sensor;

/** The environmental quantities measured in every growing chamber. */
public enum SensorType {
    TEMPERATURE("Temperature", 24),
    HUMIDITY("Humidity", 70),
    SOIL_MOISTURE("Soil", 60),
    LIGHT("Light", 600),
    NUTRIENT("Nutrient", 70);

    private final String label;
    private final double initialValue;

    SensorType(String label, double initialValue) {
        this.label = label;
        this.initialValue = initialValue;
    }

    public String label() { return label; }

    public double initialValue() { return initialValue; }
}
