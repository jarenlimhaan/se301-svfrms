package org.example.simulation;

import java.util.OptionalDouble;
import java.util.concurrent.Callable;
import org.example.monitoring.ReadingProcessor;
import org.example.resource.Resource;
import org.example.resource.Usage;
import org.example.resource.UsageRecorder;
import org.example.sensor.SensorType;

/**
 * Runs one chamber's monitoring cycles on its own thread: each cycle reads every sensor (in
 * {@link SensorType} order) and records the chamber's base water and energy use. Returns the
 * number of cycles completed; any failure or interruption is thrown to the caller, never hidden.
 */
public final class ChamberSimulation implements Callable<Integer> {
    private static final Usage WATER_PER_CYCLE = new Usage(Resource.WATER, 2);
    private static final Usage ENERGY_PER_CYCLE = new Usage(Resource.ENERGY, 1);

    private final String chamber;
    private final String crop;
    private final int cycles;
    private final ReadingProcessor processor;
    private final ReadingSource source;
    private final UsageRecorder usage;

    public ChamberSimulation(String chamber, String crop, int cycles, ReadingProcessor processor,
                             ReadingSource source, UsageRecorder usage) {
        this.chamber = chamber;
        this.crop = crop;
        this.cycles = cycles;
        this.processor = processor;
        this.source = source;
        this.usage = usage;
    }

    public String chamber() { return chamber; }

    public int cycles() { return cycles; }

    @Override
    public Integer call() throws InterruptedException {
        int completed = 0;
        for (int i = 0; i < cycles; i++) {
            if (Thread.currentThread().isInterrupted()) throw new InterruptedException(chamber + " interrupted");
            for (SensorType sensor : SensorType.values()) {
                OptionalDouble reading = source.read(chamber, sensor);
                if (reading.isPresent()) {
                    processor.process(chamber, crop, sensor, reading.getAsDouble());
                } else {
                    processor.reportMissing(chamber, crop, sensor);
                }
            }
            usage.record(WATER_PER_CYCLE);
            usage.record(ENERGY_PER_CYCLE);
            completed++;
        }
        return completed;
    }
}
