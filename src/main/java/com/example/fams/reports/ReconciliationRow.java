package com.example.fams.reports;

import java.math.BigDecimal;

/**
 * One line of the asset-subledger-vs-GL reconciliation, per GL account.
 *
 * <p>{@code subledgerBalance} comes from the asset, depreciation and valuation data
 * directly; {@code glBalance} from POSTED journal lines; {@code unpostedBalance} is the
 * same measure sitting in UNPOSTED journals. {@code difference} is subledger minus posted
 * GL, and {@code unexplainedDifference} is what's left after allowing for unposted
 * journals — anything non-zero there is a real discrepancy (missing journals, assets that
 * predate GL generation, or manual changes).
 */
public record ReconciliationRow(
        String accountRole,
        String accountCode,
        String accountName,
        BigDecimal subledgerBalance,
        BigDecimal glBalance,
        BigDecimal unpostedBalance,
        BigDecimal difference,
        BigDecimal unexplainedDifference
) {
}
