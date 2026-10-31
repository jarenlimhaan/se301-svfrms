package org.example.report;

import java.util.List;

/** Per-chamber summary lines for the archive report, sorted by chamber name. */
public interface ChamberSummaries {
    List<String> lines();
}
