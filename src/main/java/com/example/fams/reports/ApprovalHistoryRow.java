package com.example.fams.reports;

import java.time.LocalDateTime;

/** One approval action taken on a disposal workflow. */
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