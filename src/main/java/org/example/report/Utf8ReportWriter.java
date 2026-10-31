package org.example.report;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** Writes a report as UTF-8 text, creating its parent directory when needed. */
public final class Utf8ReportWriter implements ReportWriter {
    @Override
    public void write(Path file, List<String> lines) throws IOException {
        Path directory = file.getParent();
        if (directory != null) Files.createDirectories(directory);
        Files.write(file, lines, StandardCharsets.UTF_8);
    }
}
