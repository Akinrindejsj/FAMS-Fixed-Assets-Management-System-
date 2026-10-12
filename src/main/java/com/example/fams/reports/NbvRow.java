package com.example.fams.reports;

import java.math.BigDecimal;

/**
 * Net Book Value Report: historical cost, accumulated depreciation,
 * cumulative net impairment, and current net book value.
 *
 * <p><b>Scope note:</b> netBookValue here = cost - accumulatedDepreciation - impairment.
 * It does not fold in revaluation — an asset that has been revalued has its
 * own gain/loss trail in the revaluation reports. Folding both into one number risked conflating two distinct
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