package org.example;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.TreeSet;
import org.example.sensor.SensorType;
import org.example.simulation.ReadingSource;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Pins the console and archive formats (the legacy output contract) for a scripted Tomato chamber
 * that breaches every bound with a device. Numbers are masked as {@code N}, as in
 * {@code tools/compare_legacy.sh}, because device effects depend on how the threads interleave.
 */
class GoldenOutputTest {
    @TempDir
    Path dir;

    private static final Map<SensorType, Double> READINGS = Map.of(SensorType.TEMPERATURE, 30.0,
            SensorType.HUMIDITY, 50.0, SensorType.SOIL_MOISTURE, 40.0, SensorType.LIGHT, 400.0, SensorType.NUTRIENT, 30.0);

    private static String mask(String line) {
        return line.replaceAll("[0-9]+(\\.[0-9]+)?", "N");
    }

    @Test
    void consoleAndArchiveFormatsMatchTheLegacyContract() throws Exception {
        ReadingSource scripted = (chamber, sensor) -> OptionalDouble.of(READINGS.get(sensor));
        Path report = dir.resolve("daily_report.txt");
        ByteArrayOutputStream captured = new ByteArrayOutputStream();
        PrintStream original = System.out;
        System.setOut(new PrintStream(captured, true, StandardCharsets.UTF_8));
        int status;
        try {
            status = SVFRMS.run(new String[] {"CH1=Tomato"}, report, scripted);
        } finally {
            System.setOut(original);
        }
        assertEquals(SVFRMS.EXIT_OK, status);

        TreeSet<String> console = new TreeSet<>();
        captured.toString(StandardCharsets.UTF_8).lines().map(GoldenOutputTest::mask)
                .map(l -> l.replaceAll("saved to: .*", "saved to: PATH")).forEach(console::add);
        assertEquals(new TreeSet<>(CONSOLE), console);

        List<String> archive = Files.readAllLines(report, StandardCharsets.UTF_8).stream()
                .map(l -> l.startsWith("Timestamp: ") ? "Timestamp: T" : mask(l)).distinct().toList();
        assertEquals(ARCHIVE, archive);
    }

    private static final List<String> CONSOLE = List.of(
            "[CHN][Tomato][ALERT] CHN Tomato High Temp",
            "[CHN][Tomato][ALERT] CHN Tomato Low Humidity",
            "[CHN][Tomato][ALERT] CHN Tomato Low Soil Moisture",
            "[CHN][Tomato][ALERT] CHN Low Light Level",
            "[CHN][Tomato][ALERT] CHN Low Nutrient Level",
            "\t\u001B[Nm[CHN][CONTROL] Cooling Fan Activated\u001B[Nm",
            "\t\u001B[Nm[CHN][CONTROL] Temperature reduced to NC\u001B[Nm",
            "\t\u001B[Nm[CHN][CONTROL] Humidifier Activated\u001B[Nm",
            "\t\u001B[Nm[CHN][CONTROL] Humidity increased to N%\u001B[Nm",
            "\t\u001B[Nm[CHN][CONTROL] Water Pump Activated\u001B[Nm",
            "\t\u001B[Nm[CHN][CONTROL] Soil moisture increased to N%\u001B[Nm",
            "\t\u001B[Nm[CHN][CONTROL] Grow Light Activated\u001B[Nm",
            "\t\u001B[Nm[CHN][CONTROL] Light level increased to N lux\u001B[Nm",
            "\t\u001B[Nm[CHN][CONTROL] Nutrient Injector Activated\u001B[Nm",
            "\t\u001B[Nm[CHN][CONTROL] Nutrient level increased to N%\u001B[Nm",
            "Archiving alerts... N moved to archive.",
            "===== DAILY REPORT =====",
            "Generated Alerts: N",
            "Active Alerts: N",
            "Water Usage: N L",
            "Energy Usage: N kWh",
            "System finished sensor processing.",
            "Archive report saved to: PATH");
    private static final List<String> ARCHIVE = List.of(
            "===== DAILY REPORT =====",
            "Timestamp: T",
            "Generated Alerts: N",
            "Active Alerts: N",
            "Water Usage: N L",
            "Energy Usage: N kWh",
            "Archived alerts: N",
            "---",
            "Archived N alerts",
            "Daily Report",
            "--- chamber wise summary ---",
            "CHN -> Alerts=N, Actions=N, Last Action=Nutrient Injector Activated, Last Alert=CHN Low Nutrient Level",
            "--- archived alerts ---",
            "CHN Tomato High Temp",
            "CHN Tomato Low Humidity",
            "CHN Tomato Low Soil Moisture",
            "CHN Low Light Level",
            "CHN Low Nutrient Level");
}
