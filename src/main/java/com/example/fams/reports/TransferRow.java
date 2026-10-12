package com.example.fams.reports;

import java.time.LocalDate;

/**
 * Section 6, items 1–5, 8, 9, 11 — one transfer workflow entry. Reused across Asset
 * Transfer Report, Transfer-In/Out Register, Inter-Branch/Department Transfer Report,
 * Assignment History, Location History, and Pending Transfer Approval — they differ only
 * in which workflows are selected, not in row shape.
 */
public record TransferRow(
        LocalDate requestedDate,
        String assetCode,
        String assetName,
        String category,
        String status,
        String fromDepartment,
        String toDepartment,
        String fromBranch,
        String toBranch,
        String fromLocation,
        String toLocation,
        String fromEmployee,
        String toEmployee,
        String requestedBy,
        String reason
) {
}