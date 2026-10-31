package org.example.sensor;

/**
 * The environmental quantities measured in every growing chamber, with the physically possible
 * range of each. A reading outside that range (or NaN/infinite) means the sensor has failed.
 * Code that needs "every sensor" iterates {@link #values()}, so a new sensor is added here once.
 */
public enum SensorType {
    TEMPERATURE("Temperature", "temperature", 24, -40, 125),   // degrees C (typical digital sensor range)
    HUMIDITY("Humidity", "humidity", 70, 0, 100),              // %
    SOIL_MOISTURE("Soil", "soil", 60, 0, 100),                 // %
    LIGHT("Light", "light", 600, 0, 200_000),                  // lux
    NUTRIENT("Nutrient", "nutrient", 70, 0, 100);              // %

    private final String label;
    private final String key;
    private final double initialValue;
    private final double validMin;
    private final double validMax;

    SensorType(String label, String key, double initialValue, double validMin, double validMax) {
        this.label = label;
        this.key = key;
        this.initialValue = initialValue;
        this.validMin = validMin;
        this.validMax = validMax;
    }

    /** Name used in console output and logs, e.g. "Soil". */
    public String label() { return label; }

    /** Name used in crop definitions on the command line, e.g. "soil". */
    public String key() { return key; }

    public double initialValue() { return initialValue; }

    public double validMin() { return validMin; }

    public double validMax() { return validMax; }

    /** Whether {@code value} is a reading this sensor can physically produce. */
    public boolean isValid(double value) {
        return Double.isFinite(value) && value >= validMin && value <= validMax;
    }
}
