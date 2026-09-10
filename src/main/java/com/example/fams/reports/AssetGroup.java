package com.example.fams.reports;

import java.math.BigDecimal;
import java.util.List;


public record AssetGroup(
        String groupKey,
        int assetCount,
        BigDecimal totalCost,
        BigDecimal totalAccumulatedDepreciation,
        BigDecimal totalNetBookValue,
        List<ReportRow> rows
) {
}
