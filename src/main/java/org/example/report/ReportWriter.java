package org.example.report;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

/** Persists rendered report lines to a destination. */
public interface ReportWriter {
    void write(Path file, List<String> lines) throws IOException;
}
