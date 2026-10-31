package org.example.alert;

import java.util.Objects;

/** One alert raised for a chamber. */
public record Alert(String chamber, String crop, String message, Severity severity) {

    public Alert {
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(severity, "severity");
    }
}
