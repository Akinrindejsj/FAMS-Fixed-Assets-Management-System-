package com.example.fams.reports;

/** Current custodian assignment (or lack of one) per asset. */
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