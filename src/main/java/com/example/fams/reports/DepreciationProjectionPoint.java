package com.example.fams.reports;

import java.math.BigDecimal;

/** Section 4, item 15 — one future year's projected depreciation position. */
public record DepreciationProjectionPoint(
        String year,
        BigDecimal projectedCharge,
        BigDecimal projectedAccumulatedDepreciation,
        BigDecimal projectedBookValue
) {
}