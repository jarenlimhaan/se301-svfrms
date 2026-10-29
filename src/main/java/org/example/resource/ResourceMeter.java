package org.example.resource;

import java.util.concurrent.atomic.AtomicInteger;

/** Facility-wide water (L) and energy (kWh) counters, safe to update from any thread. */
public final class ResourceMeter {
    private final AtomicInteger water = new AtomicInteger();
    private final AtomicInteger energy = new AtomicInteger();

    public void record(Usage usage) {
        (usage.resource() == Resource.WATER ? water : energy).addAndGet(usage.amount());
    }

    public void addWater(int litres) { water.addAndGet(litres); }

    public void addEnergy(int kwh) { energy.addAndGet(kwh); }

    public int water() { return water.get(); }

    public int energy() { return energy.get(); }
}
