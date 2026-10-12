package com.example.fams.reports;

/** An asset excluded from/failing depreciation, with why. */
public record ExceptionRow(
        String assetCode,
        String assetName,
        String category,
        String department,
        String status,
        String reason
) {
}