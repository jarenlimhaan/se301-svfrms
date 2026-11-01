package org.example.simulation;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Runs chamber simulations in parallel (one thread each) and verifies the outcome of every one.
 * The run counts as successful only if every chamber completed all of its cycles within the deadline.
 */
public final class SimulationRunner {
    /** Generous upper bound for a whole run; a reading source that blocks forever must not hang the program. */
    public static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(5);

    private SimulationRunner() {}

    /** Runs all simulations with {@link #DEFAULT_TIMEOUT}; see {@link #runAll(List, Duration)}. */
    public static void runAll(List<ChamberSimulation> simulations) throws SimulationFailedException, InterruptedException {
        runAll(simulations, DEFAULT_TIMEOUT);
    }

    /**
     * Runs all simulations and waits for each to finish, at most {@code timeout} in total.
     *
     * @throws SimulationFailedException if any simulation threw, was interrupted, completed fewer
     *                                   cycles than configured, or did not finish within the timeout; every failure is listed, and the
     *                                   underlying exceptions are attached as suppressed
     * @throws InterruptedException      if the calling thread is interrupted while waiting
     */
    public static void runAll(List<ChamberSimulation> simulations, Duration timeout)
            throws SimulationFailedException, InterruptedException {
        if (simulations.isEmpty()) return;
        long deadline = System.nanoTime() + timeout.toNanos();
        Map<ChamberSimulation, Future<Integer>> futures = new LinkedHashMap<>();
        List<String> failures = new ArrayList<>();
        List<Throwable> causes = new ArrayList<>();
        // Daemon threads: a chamber stuck in an uninterruptible read cannot keep the JVM alive after a timeout.
        ExecutorService pool = Executors.newFixedThreadPool(simulations.size(), task -> {
            Thread thread = new Thread(task);
            thread.setDaemon(true);
            return thread;
        });
        try {
            simulations.forEach(sim -> futures.put(sim, pool.submit(sim)));
            collect(futures, failures, causes, deadline);
        } finally {
            pool.shutdownNow(); // every task has finished here, unless it timed out or we were interrupted
        }
        if (!failures.isEmpty()) {
            SimulationFailedException failure = new SimulationFailedException(
                    "Simulation incomplete: " + String.join("; ", failures));
            causes.forEach(failure::addSuppressed);
            throw failure;
        }
    }

    private static void collect(Map<ChamberSimulation, Future<Integer>> futures, List<String> failures,
                                List<Throwable> causes, long deadline) throws InterruptedException {
        for (Map.Entry<ChamberSimulation, Future<Integer>> entry : futures.entrySet()) {
            ChamberSimulation sim = entry.getKey();
            try {
                long remaining = Math.max(0, deadline - System.nanoTime());
                int completed = entry.getValue().get(remaining, TimeUnit.NANOSECONDS);
                if (completed != sim.cycles()) {
                    failures.add(sim.chamber() + " completed " + completed + " of " + sim.cycles() + " cycles");
                }
            } catch (ExecutionException e) {
                failures.add(sim.chamber() + " failed: " + e.getCause());
                causes.add(e.getCause());
            } catch (TimeoutException e) {
                entry.getValue().cancel(true);
                failures.add(sim.chamber() + " did not finish in time");
                causes.add(e);
            } catch (CancellationException e) {
                failures.add(sim.chamber() + " was cancelled");
                causes.add(e);
            }
        }
    }
}
