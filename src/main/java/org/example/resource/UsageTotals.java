package org.example.resource;

/** Read access to total resource usage. The role reporting code depends on. */
public interface UsageTotals {
    /** Total water used, in litres. */
    int water();

    /** Total energy used, in kWh. */
    int energy();
}
