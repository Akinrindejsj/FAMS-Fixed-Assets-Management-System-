package com.example.fams.reports;

import java.math.BigDecimal;

/**
 * Section 5, item 1 — Net Book Value Report: historical cost, accumulated depreciation,
 * cumulative net impairment, and current net book value.
 *
 * <p><b>Scope note:</b> netBookValue here = cost - accumulatedDepreciation - impairment.
 * It does not fold in revaluation (items 9–11) — an asset that has been revalued has its
 * own gain/loss trail there. Folding both into one number risked conflating two distinct
 * concepts; this report answers "what would NBV be under cost-basis depreciation and
 * impairment alone."
 */
public record NbvRow(
        String assetCode,
        String assetName,
        String category,
        BigDecimal historicalCost,
        BigDecimal accumulatedDepreciation,
        BigDecimal impairment,
        BigDecimal netBookValue
) {
}