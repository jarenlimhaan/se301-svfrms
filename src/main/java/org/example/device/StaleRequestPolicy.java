package org.example.device;

/**
 * What the dispatcher does with a device request whose triggering reading has been superseded by a
 * newer reading of the same sensor before the request runs (e.g. a cooling fan queued at 35 C that
 * runs after a 20 C reading).
 */
public enum StaleRequestPolicy {
    /** Run it anyway. This is the legacy behaviour and the default; stale runs are counted. */
    EXECUTE,
    /** Skip it: the device runs only if its triggering reading is still the latest one. */
    SKIP
}
