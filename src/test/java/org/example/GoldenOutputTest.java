package org.example;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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
 * that breaches every bound with a device. Only device-effect readings are masked because their
 * final values depend on how sensor and control threads interleave; all totals are exact.
 */
class GoldenOutputTest {
    @TempDir
    Path dir;

    private static final Map<SensorType, Double> READINGS = Map.of(SensorType.TEMPERATURE, 30.0,
            SensorType.HUMIDITY, 50.0, SensorType.SOIL_MOISTURE, 40.0, SensorType.LIGHT, 400.0, SensorType.NUTRIENT, 30.0);

    private static String normalizeConsole(String line) {
        if (line.contains("reduced to ") || line.contains("increased to ")) {
            return line.replaceAll("(?<=to )-?[0-9]+(\\.[0-9]+)?", "N");
        }
        return line.replaceAll("saved to: .*", "saved to: PATH");
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
        captured.toString(StandardCharsets.UTF_8).lines().map(GoldenOutputTest::normalizeConsole).forEach(console::add);
        assertEquals(new TreeSet<>(CONSOLE), console);

        List<String> archive = Files.readAllLines(report, StandardCharsets.UTF_8).stream()
                .map(l -> l.startsWith("Timestamp: ") ? "Timestamp: T" : l).toList();
        assertEquals(expectedArchive(), archive);
    }

    private static final List<String> CONSOLE = List.of(
            "[CH1][Tomato][ALERT] CH1 Tomato High Temp",
            "[CH1][Tomato][ALERT] CH1 Tomato Low Humidity",
            "[CH1][Tomato][ALERT] CH1 Tomato Low Soil Moisture",
            "[CH1][Tomato][ALERT] CH1 Low Light Level",
            "[CH1][Tomato][ALERT] CH1 Low Nutrient Level",
            "\t\u001B[32m[CH1][CONTROL] Cooling Fan Activated\u001B[0m",
            "\t\u001B[32m[CH1][CONTROL] Temperature reduced to NC\u001B[0m",
            "\t\u001B[32m[CH1][CONTROL] Humidifier Activated\u001B[0m",
            "\t\u001B[32m[CH1][CONTROL] Humidity increased to N%\u001B[0m",
            "\t\u001B[32m[CH1][CONTROL] Water Pump Activated\u001B[0m",
            "\t\u001B[32m[CH1][CONTROL] Soil moisture increased to N%\u001B[0m",
            "\t\u001B[32m[CH1][CONTROL] Grow Light Activated\u001B[0m",
            "\t\u001B[32m[CH1][CONTROL] Light level increased to N lux\u001B[0m",
            "\t\u001B[32m[CH1][CONTROL] Nutrient Injector Activated\u001B[0m",
            "\t\u001B[32m[CH1][CONTROL] Nutrient level increased to N%\u001B[0m",
            "Archiving alerts... 100 moved to archive.",
            "===== DAILY REPORT =====",
            "Generated Alerts: 100",
            "Active Alerts: 0",
            "Water Usage: 1440 L",
            "Energy Usage: 220 kWh",
            "System finished sensor processing.",
            "Archive report saved to: PATH");
    private static List<String> expectedArchive() {
        List<String> lines = new ArrayList<>(List.of(
                "===== DAILY REPORT =====",
                "Timestamp: T",
                "Generated Alerts: 100",
                "Active Alerts: 0",
                "Water Usage: 1440 L",
                "Energy Usage: 220 kWh",
                "Archived alerts: 100",
                "---",
                "Archived 100 alerts",
                "Daily Report",
                "--- chamber wise summary ---",
                "CH1 -> Alerts=100, Actions=100, Last Action=Nutrient Injector Activated, Last Alert=CH1 Low Nutrient Level",
                "--- archived alerts ---"));
        for (int cycle = 0; cycle < 20; cycle++) {
            lines.add("CH1 Tomato High Temp");
            lines.add("CH1 Tomato Low Humidity");
            lines.add("CH1 Tomato Low Soil Moisture");
            lines.add("CH1 Low Light Level");
            lines.add("CH1 Low Nutrient Level");
        }
        return lines;
    }
}
