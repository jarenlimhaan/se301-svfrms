package org.example.device;

/**
 * Outcome counts for the device requests a dispatcher has run.
 *
 * @param executed         requests whose device ran
 * @param failed           requests whose device threw (logged; the worker carried on)
 * @param stale            requests whose triggering reading had been superseded when they ran
 * @param skipped          stale requests not executed (only under {@link StaleRequestPolicy#SKIP})
 * @param listenerFailures listener notifications that threw (logged; other listeners still ran)
 */
public record DispatchStats(int executed, int failed, int stale, int skipped, int listenerFailures) {

    /** Whether anything went wrong that the operator should be told about. */
    public boolean hasProblems() {
        return failed > 0 || listenerFailures > 0;
    }
}
