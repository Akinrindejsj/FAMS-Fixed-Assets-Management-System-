package com.example.fams.reports;

import java.math.BigDecimal;

/** One future year's projected depreciation position. */
public record DepreciationProjectionPoint(
        String year,
        BigDecimal projectedCharge,
        BigDecimal projectedAccumulatedDepreciation,
        BigDecimal projectedBookValue
) {
}