package com.example.fams.reports;

import java.math.BigDecimal;

/**
 * Section 4, item 1 — Depreciation Schedule: cost, residual value, useful life, method,
 * current depreciation charge and carrying amount, per asset. Distinct from the plain
 * Asset Register in that it surfaces the depreciation *policy* (method/useful life/
 * residual), not just the current balance.
 */
public record DepreciationScheduleRow(
        String assetCode,
        String assetName,
        String category,
        BigDecimal cost,
        BigDecimal residualValue,
        Integer usefulLifeYears,
        String method,
        BigDecimal depreciationCharge,
        BigDecimal accumulatedDepreciation,
        BigDecimal carryingAmount,
        String status
) {
}