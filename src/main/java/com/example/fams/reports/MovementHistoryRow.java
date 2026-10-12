package com.example.fams.reports;

import java.time.LocalDateTime;

/** Section 6, item 12 — one chronological lifecycle event for an asset. */
public record MovementHistoryRow(
        String assetCode,
        String assetName,
        String eventType,
        LocalDateTime eventAt,
        String actor,
        String title,
        String fromValue,
        String toValue
) {
}