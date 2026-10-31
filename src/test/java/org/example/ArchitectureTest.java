package org.example;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import org.example.device.ControlDispatcher;
import org.example.device.StaleRequestPolicy;
import org.example.monitoring.ResponsePolicy;
import org.example.monitoring.SensorProcessor;
import org.example.report.ReportService;
import org.example.simulation.ChamberSimulation;
import org.junit.jupiter.api.Test;

/**
 * DIP, checked mechanically: the services that do the work receive their collaborators only as
 * interfaces or immutable values, never as concrete service classes.
 */
class ArchitectureTest {
    /** Immutable values and configuration that are fine to depend on directly. */
    private static final Set<Class<?>> VALUES = Set.of(String.class, int.class, Duration.class, List.class,
            ResponsePolicy.class, StaleRequestPolicy.class);

    @Test
    void servicesDependOnAbstractionsOnly() {
        for (Class<?> service : List.of(SensorProcessor.class, ReportService.class, ChamberSimulation.class,
                ControlDispatcher.class)) {
            for (Constructor<?> constructor : service.getConstructors()) {
                for (Class<?> parameter : constructor.getParameterTypes()) {
                    assertTrue(parameter.isInterface() || VALUES.contains(parameter),
                            service.getSimpleName() + " depends on concrete " + parameter.getName());
                }
            }
        }
    }
}
