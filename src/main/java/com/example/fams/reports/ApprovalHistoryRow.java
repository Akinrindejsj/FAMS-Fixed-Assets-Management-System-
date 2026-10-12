package com.example.fams.reports;

import java.time.LocalDateTime;

/** Section 7, item 11 — one approval action taken on a disposal workflow. */
public record ApprovalHistoryRow(
        String assetCode,
        String assetName,
        String taskName,
        String actor,
        String decision,
        String comment,
        LocalDateTime actionAt
) {
}