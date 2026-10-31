package org.example.simulation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

/**
 * Runs chamber simulations in parallel (one thread each) and verifies the outcome of every one.
 * The run counts as successful only if every chamber completed all of its cycles.
 */
public final class SimulationRunner {
    private SimulationRunner() {}

    /**
     * Runs all simulations and waits for each to finish.
     *
     * @throws SimulationFailedException if any simulation threw, was interrupted, or completed fewer
     *                                   cycles than configured; every failure is listed, and the
     *                                   underlying exceptions are attached as suppressed
     * @throws InterruptedException      if the calling thread is interrupted while waiting
     */
    public static void runAll(List<ChamberSimulation> simulations) throws SimulationFailedException, InterruptedException {
        if (simulations.isEmpty()) return;
        Map<ChamberSimulation, Future<Integer>> futures = new LinkedHashMap<>();
        List<String> failures = new ArrayList<>();
        List<Throwable> causes = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(simulations.size())) {
            simulations.forEach(sim -> futures.put(sim, pool.submit(sim)));
            for (Map.Entry<ChamberSimulation, Future<Integer>> entry : futures.entrySet()) {
                ChamberSimulation sim = entry.getKey();
                try {
                    int completed = entry.getValue().get();
                    if (completed != sim.cycles()) {
                        failures.add(sim.chamber() + " completed " + completed + " of " + sim.cycles() + " cycles");
                    }
                } catch (ExecutionException e) {
                    failures.add(sim.chamber() + " failed: " + e.getCause());
                    causes.add(e.getCause());
                } catch (CancellationException e) {
                    failures.add(sim.chamber() + " was cancelled");
                    causes.add(e);
                }
            }
        } catch (InterruptedException e) {
            futures.values().forEach(f -> f.cancel(true));
            throw e;
        }
        if (!failures.isEmpty()) {
            SimulationFailedException failure = new SimulationFailedException(
                    "Simulation incomplete: " + String.join("; ", failures));
            causes.forEach(failure::addSuppressed);
            throw failure;
        }
    }
}
