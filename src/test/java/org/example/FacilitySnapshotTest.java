package org.example;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.example.report.ArchiveReport;
import org.example.report.FacilitySnapshot;
import org.example.resource.Resource;
import org.junit.jupiter.api.Test;

/** Finding 6: the snapshot is deeply immutable and the archive timestamp format is unchanged. */
class FacilitySnapshotTest {

    @Test
    void listsAreCopiedAndUnmodifiable() {
        List<String> activity = new ArrayList<>(List.of("Daily Report"));
        FacilitySnapshot snapshot = new FacilitySnapshot(Instant.now(), 1, 0, Map.of(Resource.WATER, 2, Resource.ENERGY, 3), activity,
                new ArrayList<>(List.of("CH1 -> ...")), new ArrayList<>(List.of("CH1 Tomato High Temp")));
        activity.add("changed later");
        assertEquals(List.of("Daily Report"), snapshot.activity(), "caller's list is not shared");
        assertThrows(UnsupportedOperationException.class, () -> snapshot.activity().add("x"));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.chamberSummaries().clear());
        assertThrows(UnsupportedOperationException.class, () -> snapshot.archivedAlerts().remove(0));
    }

    @Test
    void nullsAreRejected() {
        assertThrows(NullPointerException.class, () -> new FacilitySnapshot(null, 0, 0, Map.of(Resource.WATER, 0, Resource.ENERGY, 0), List.of(), List.of(), List.of()));
        assertThrows(NullPointerException.class, () -> new FacilitySnapshot(Instant.now(), 0, 0, Map.of(Resource.WATER, 0, Resource.ENERGY, 0), null, List.of(), List.of()));
    }

    @Test
    void archiveTimestampKeepsTheLegacyDateFormat() {
        Instant now = Instant.now();
        List<String> lines = new ArchiveReport().render(new FacilitySnapshot(now, 0, 0, Map.of(Resource.WATER, 0, Resource.ENERGY, 0), List.of(), List.of(), List.of()));
        assertEquals("Timestamp: " + new Date(now.toEpochMilli()), lines.get(1));
    }
}
