package org.example.simulation;

import java.util.concurrent.ThreadLocalRandom;
import org.example.monitoring.SensorProcessor;
import org.example.resource.ResourceMeter;
import org.example.sensor.SensorType;

/** Simulates one chamber's sensors producing random readings. Runs on its own thread. */
public final class ChamberSimulation implements Runnable {
    private final String chamber;
    private final String crop;
    private final int cycles;
    private final SensorProcessor processor;
    private final ResourceMeter meter;

    public ChamberSimulation(String chamber, String crop, int cycles, SensorProcessor processor, ResourceMeter meter) {
        this.chamber = chamber;
        this.crop = crop;
        this.cycles = cycles;
        this.processor = processor;
        this.meter = meter;
    }

    @Override
    public void run() {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        try {
            for (int i = 0; i < cycles; i++) {
                processor.process(chamber, crop, SensorType.TEMPERATURE, 10 + random.nextInt(30));
                processor.process(chamber, crop, SensorType.HUMIDITY, 40 + random.nextInt(60));
                processor.process(chamber, crop, SensorType.SOIL_MOISTURE, 20 + random.nextInt(60));
                processor.process(chamber, crop, SensorType.LIGHT, 300 + random.nextInt(900));
                processor.process(chamber, crop, SensorType.NUTRIENT, 20 + random.nextInt(80));
                meter.addWater(2);
                meter.addEnergy(1);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
