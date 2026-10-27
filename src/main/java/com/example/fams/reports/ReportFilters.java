package com.example.fams.reports;

import com.example.fams.assets.Asset;

/**
 * Per-request asset filters (cost centre, custodian, status) shared by the asset-based
 * reports. A request interceptor sets them from the query string and clears them when the
 * request ends, so each report service only has to ask {@link #matches(Asset)}.
 */
public final class ReportFilters {

    /** Filter values; blank means "no filter". */
    public record Values(String costCentre, String custodian, String status) {
        boolean isEmpty() {
            return blank(costCentre) && blank(custodian) && blank(status);
        }
    }

    private static final ThreadLocal<Values> CURRENT = new ThreadLocal<>();

    private ReportFilters() {
    }

    static void set(Values values) {
        if (values == null || values.isEmpty()) {
            CURRENT.remove();
        } else {
            CURRENT.set(values);
        }
    }

    static void clear() {
        CURRENT.remove();
    }

    /** True when no filters are set for this request, or the asset satisfies all of them. */
    public static boolean matches(Asset asset) {
        Values values = CURRENT.get();
        if (values == null) {
            return true;
        }
        if (asset == null) {
            return false;
        }
        return containsIgnoreCase(asset.getCostCentre(), values.costCentre())
                && containsIgnoreCase(asset.getCustodian(), values.custodian())
                && (blank(values.status()) || values.status().trim().equalsIgnoreCase(asset.getStatus() == null ? "" : asset.getStatus().trim()));
    }

    private static boolean containsIgnoreCase(String actual, String wanted) {
        if (blank(wanted)) {
            return true;
        }
        return actual != null && actual.toLowerCase().contains(wanted.trim().toLowerCase());
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
