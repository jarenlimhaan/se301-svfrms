package org.example.simulation;

/** One or more chamber simulations failed, were interrupted, or did not complete every cycle. */
public final class SimulationFailedException extends Exception {
    private static final long serialVersionUID = 1L;

    public SimulationFailedException(String message) {
        super(message);
    }
}
