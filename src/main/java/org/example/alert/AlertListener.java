package org.example.alert;

/** Notified whenever a new alert is raised. */
public interface AlertListener {
    void onAlert(Alert alert);
}
