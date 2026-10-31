package org.example;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;
import org.example.alert.Alert;
import org.example.alert.AlertService;
import org.example.alert.Severity;
import org.example.alert.SeverityAlertPrinter;
import org.example.crop.CropCatalog;
import org.example.monitoring.ResponsePolicy;
import org.example.monitoring.SensorProcessor;
import org.example.resource.ResourceMeter;
import org.example.sensor.Breach;
import org.example.sensor.ChamberRegistry;
import org.example.sensor.Direction;
import org.example.sensor.SensorLog;
import org.example.sensor.SensorType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Alert severity levels (spec §3.4): assigned by the policy, shown only when asked for. */
class SeverityTest {
    @TempDir
    Path dir;

    @Test
    void policyAssignsSeverities() {
        ResponsePolicy policy = ResponsePolicy.standard();
        assertEquals(Severity.CRITICAL, policy.responseTo(new Breach(SensorType.TEMPERATURE, Direction.HIGH)).severity());
        assertEquals(Severity.CRITICAL, policy.responseTo(new Breach(SensorType.TEMPERATURE, Direction.LOW)).severity());
        assertEquals(Severity.WARNING, policy.responseTo(new Breach(SensorType.SOIL_MOISTURE, Direction.LOW)).severity());
        assertEquals(Severity.INFO, policy.responseTo(new Breach(SensorType.LIGHT, Direction.LOW)).severity());
    }

    @Test
    void alertsCarryThePolicySeverity() throws Exception {
        AlertService alerts = new AlertService(List.of());
        new SensorProcessor(CropCatalog.defaultRegistry(), new ChamberRegistry(), new SensorLog(), alerts, new ResourceMeter(),
                ResponsePolicy.standard(), (c, d, t) -> {}).process("CH3", "Lettuce", SensorType.TEMPERATURE, 30);
        alerts.archiveActive();
        assertEquals(List.of(new Alert("CH3", "Lettuce", "CH3 Lettuce High Temp", Severity.CRITICAL)), alerts.archived());
    }

    @Test
    void severityPrinterUsesTheSpecFormat() {
        String out = capture(() -> new SeverityAlertPrinter().onAlert(
                new Alert("CH3", "Lettuce", "CH3 Lettuce High Temp", Severity.CRITICAL)));
        assertEquals("CRITICAL: CH3 Lettuce High Temp", out.strip());
    }

    @Test
    void defaultConsoleFormatIsUnchangedAndSeverityFormatIsOptIn() {
        String legacy = capture(() -> runQuietly(new String[] {"CH1=Tomato"}));
        assertTrue(legacy.contains("[CH1][Tomato][ALERT] CH1 "), "legacy format by default");
        assertTrue(!legacy.contains("CRITICAL:") && !legacy.contains("WARNING:") && !legacy.contains("INFO:"));

        String severity = capture(() -> runQuietly(new String[] {"--alert-format=severity", "CH1=Tomato"}));
        assertTrue(severity.matches("(?s).*(CRITICAL|WARNING|INFO): CH1 .*"), severity);
        assertTrue(!severity.contains("[ALERT]"));
    }

    @Test
    void unknownAlertFormatIsRejected() throws Exception {
        assertEquals(SVFRMS.EXIT_BAD_ARGUMENTS, SVFRMS.run(new String[] {"--alert-format=loud"}, dir.resolve("r.txt")));
    }

    private void runQuietly(String[] args) {
        try {
            assertEquals(SVFRMS.EXIT_OK, SVFRMS.run(args, dir.resolve("report.txt")));
        } catch (InterruptedException e) {
            throw new AssertionError(e);
        }
    }

    private static String capture(Runnable action) {
        PrintStream original = System.out;
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        System.setOut(new PrintStream(buffer, true, StandardCharsets.UTF_8));
        try {
            action.run();
        } finally {
            System.setOut(original);
        }
        return buffer.toString(StandardCharsets.UTF_8);
    }
}
