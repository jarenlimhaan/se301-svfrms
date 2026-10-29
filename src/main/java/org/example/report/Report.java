package org.example.report;

import java.util.List;

/** Renders a snapshot as lines of text. New report types implement this. */
public interface Report {
    List<String> render(FacilitySnapshot snapshot);
}
