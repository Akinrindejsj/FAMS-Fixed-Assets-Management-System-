package com.example.fams.reports;

/** Section 6, items 6 and 10 — current custodian assignment (or lack of one) per asset. */
public record AssignmentRow(
        String assetCode,
        String assetName,
        String category,
        String custodian,
        String department,
        String branch,
        String status
) {
}