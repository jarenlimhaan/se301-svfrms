package org.example;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.example.alert.AlertService;
import org.example.crop.CropCatalog;
import org.example.monitoring.ResponsePolicy;
import org.example.monitoring.SensorProcessor;
import org.example.resource.ResourceMeter;
import org.example.sensor.ChamberRegistry;
import org.example.sensor.SensorLog;
import org.example.simulation.ChamberSimulation;
import org.example.simulation.SimulationFailedException;
import org.example.simulation.RandomReadingSource;
import org.example.simulation.SimulationRunner;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Finding 3: a chamber that fails or is cut short makes the whole run fail visibly. */
class SimulationRunnerTest {
    private final ResourceMeter meter = new ResourceMeter();

    private SensorProcessor processor(org.example.device.DeviceController controller) {
        return new SensorProcessor(CropCatalog.defaultRegistry(), new ChamberRegistry(), new SensorLog(),
                new AlertService(List.of()), meter, ResponsePolicy.standard(), controller);
    }

    @Test
    void allChambersCompletingEveryCycleSucceeds() throws Exception {
        SensorProcessor p = processor((chamber, device, trigger) -> {});
        SimulationRunner.runAll(List.of(
                new ChamberSimulation("CH1", "Tomato", 20, p, RandomReadingSource.legacy(), meter),
                new ChamberSimulation("CH2", "Kale", 20, p, RandomReadingSource.legacy(), meter)));
        // Base usage is 1 kWh per cycle; Tomato temperature breaches can only add to it.
        assertTrue(meter.energy() >= 2 * 20, "every cycle of both chambers ran: " + meter.energy());
    }

    @Test
    void chamberThatThrowsFailsTheRun() {
        SensorProcessor p = processor((chamber, device, trigger) -> {
            if (chamber.equals("CH2")) throw new IllegalStateException("control worker has stopped");
        });
        SimulationFailedException e = assertThrows(SimulationFailedException.class, () -> SimulationRunner.runAll(List.of(
                new ChamberSimulation("CH1", "Tomato", 20, p, RandomReadingSource.legacy(), meter),
                new ChamberSimulation("CH2", "Tomato", 20, p, RandomReadingSource.legacy(), meter))));
        assertTrue(e.getMessage().contains("CH2 failed"), e.getMessage());
        assertTrue(!e.getMessage().contains("CH1"), "CH1 completed normally: " + e.getMessage());
        assertEquals(1, e.getSuppressed().length);
    }

    @Test
    void interruptedChamberFailsTheRun() {
        SensorProcessor p = processor((chamber, device, trigger) -> {
            throw new InterruptedException("interrupted while queueing");
        });
        SimulationFailedException e = assertThrows(SimulationFailedException.class, () -> SimulationRunner.runAll(List.of(
                new ChamberSimulation("CH3", "Tomato", 20, p, RandomReadingSource.legacy(), meter))));
        assertTrue(e.getMessage().contains("CH3 failed"), e.getMessage());
        assertTrue(e.getSuppressed()[0] instanceof InterruptedException);
    }

    @TempDir
    Path dir;

    /** End to end: a failure inside a chamber makes SVFRMS.run return exit status 1 and write no report. */
    @Test
    void failedRunExitsWithStatus1AndWritesNoReport() throws Exception {
        Path report = dir.resolve("daily_report.txt");
        int status = SVFRMS.run(new String[] {"CH1=Tomato", "CH2=Kale"}, report, (chamber, sensor) -> {
            throw new IllegalStateException("sensor bus down");
        });
        assertEquals(SVFRMS.EXIT_RUN_FAILED, status);
        assertTrue(Files.notExists(report), "no report after a failed run");
    }
}
