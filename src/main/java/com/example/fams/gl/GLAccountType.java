package com.example.fams.gl;

/**
 * The role a GL account plays in fixed-asset journal generation. One
 * {@link GLAccountMapping} assigns a real account to each of these roles per category
 * (or globally, as the fallback).
 */
public enum GLAccountType {
    ASSET_COST,
    ACCUMULATED_DEPRECIATION,
    DEPRECIATION_EXPENSE,
    ACCUMULATED_IMPAIRMENT,
    IMPAIRMENT_LOSS,
    REVALUATION_SURPLUS,
    REVALUATION_LOSS,
    DISPOSAL_GAIN,
    DISPOSAL_LOSS,
    DISPOSAL_PROCEEDS_RECEIVABLE,
    CASH_OR_PAYABLES_CLEARING
}