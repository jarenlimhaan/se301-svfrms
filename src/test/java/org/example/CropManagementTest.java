package org.example;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.example.crop.CropProfile;
import org.example.crop.CropRegistry;
import org.example.crop.CropSpecParser;
import org.example.crop.Range;
import org.example.sensor.SensorType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Finding 8: crops are added and removed at run time, from the command line, without code changes. */
class CropManagementTest {
    @TempDir
    Path dir;

    @Test
    void cropSpecIsParsedIntoAValidatedProfile() {
        CropProfile okra = CropSpecParser.parse("Okra: temperature=24..30, soil=50..70, light=600..");
        assertEquals("Okra", okra.name());
        assertEquals(new Range(24, 30), okra.range(SensorType.TEMPERATURE).orElseThrow());
        assertEquals(new Range(50, 70), okra.range(SensorType.SOIL_MOISTURE).orElseThrow());
        assertEquals(Range.atLeast(600), okra.range(SensorType.LIGHT).orElseThrow());
        assertEquals(CropProfile.DEFAULT_NUTRIENT, okra.range(SensorType.NUTRIENT).orElseThrow(), "facility default kept");
        assertTrue(okra.range(SensorType.HUMIDITY).isEmpty());
    }

    @Test
    void malformedSpecsAreRejectedWithAReason() {
        for (String bad : List.of("Okra", ":temperature=1..2", "Okra:", "Okra:temp=24..30", "Okra:temperature=24-30",
                "Okra:temperature=abc..30", "Okra:temperature=30..24", "Okra:humidity=-5..80", "Okra:temperature=..30")) {
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> CropSpecParser.parse(bad));
            assertTrue(e.getMessage().contains("invalid crop definition"), bad + " -> " + e.getMessage());
        }
    }

    @Test
    void commandLineAddsReplacesAndRemovesCrops() throws Exception {
        CommandLine cli = CommandLine.parse("--add-crop=Okra:temperature=24..30",
                "--add-crop=Basil:temperature=18..27,humidity=40..60", "--add-crop=Lettuce:temperature=12..18",
                "--remove-crop=Spinach", "CH5=Okra", "CH6=Basil");

        CropRegistry crops = cli.cropRegistry();
        assertTrue(crops.names().containsAll(List.of("Okra", "Basil")));
        assertTrue(crops.find("Spinach").isEmpty());
        assertEquals(new Range(12, 18), crops.find("Lettuce").orElseThrow().range(SensorType.TEMPERATURE).orElseThrow());
        assertEquals(Map.of("CH5", "Okra", "CH6", "Basil"), cli.chambers());
    }

    @Test
    void noChamberArgumentsMeansTheLegacyFour() throws Exception {
        assertEquals(List.of("CH1", "CH2", "CH3", "CH4"), List.copyOf(CommandLine.parse("--remove-crop=Chili").chambers().keySet()));
    }

    @Test
    void badArgumentsAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> CommandLine.parse("--bogus"));
        assertThrows(IllegalArgumentException.class, () -> CommandLine.parse("CH1"));
        assertThrows(IllegalArgumentException.class, () -> CommandLine.parse("--add-crop="));
        assertThrows(IllegalArgumentException.class, () -> CommandLine.parse("--remove-crop=Durian").cropRegistry());
    }

    @Test
    void runtimeCropIsMonitoredEndToEnd() throws Exception {
        Path report = dir.resolve("daily_report.txt");
        int status = SVFRMS.run(new String[] {"--add-crop=Okra:temperature=24..30,soil=50..70", "CH5=Okra"}, report);
        assertEquals(SVFRMS.EXIT_OK, status);
        String archive = Files.readString(report);
        assertTrue(archive.contains("CH5 Okra High Temp") || archive.contains("CH5 Okra Low Temp"), archive);
    }

    @Test
    void removedCropFallsBackToFacilityWideChecksOnly() throws Exception {
        Path report = dir.resolve("daily_report.txt");
        assertEquals(SVFRMS.EXIT_OK, SVFRMS.run(new String[] {"--remove-crop=Tomato", "CH1=Tomato"}, report));
        String archive = Files.readString(report);
        assertTrue(!archive.contains("Tomato High Temp") && !archive.contains("Tomato Low Temp"), archive);
    }

    @Test
    void invalidArgumentsGiveExitStatus3() throws Exception {
        assertEquals(SVFRMS.EXIT_BAD_ARGUMENTS, SVFRMS.run(new String[] {"--add-crop=Okra:temp=1..2"}, dir.resolve("r.txt")));
        assertTrue(Files.notExists(dir.resolve("r.txt")));
    }
}
