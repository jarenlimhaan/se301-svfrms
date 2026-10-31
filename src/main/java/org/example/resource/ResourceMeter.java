package org.example.resource;

import java.util.concurrent.atomic.AtomicInteger;

/** Facility-wide water (L) and energy (kWh) counters, safe to update from any thread. Usage only ever grows. */
public final class ResourceMeter implements UsageRecorder, UsageTotals {
    private final AtomicInteger water = new AtomicInteger();
    private final AtomicInteger energy = new AtomicInteger();

    @Override
    public void record(Usage usage) {
        (usage.resource() == Resource.WATER ? water : energy).addAndGet(usage.amount());
    }

    public void addWater(int litres) { record(new Usage(Resource.WATER, litres)); }

    public void addEnergy(int kwh) { record(new Usage(Resource.ENERGY, kwh)); }

    @Override
    public int water() { return water.get(); }

    @Override
    public int energy() { return energy.get(); }
}
