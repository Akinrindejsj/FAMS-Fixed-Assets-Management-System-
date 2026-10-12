package com.example.fams.gl;

/** What kind of asset transaction a journal entry was auto-generated from. */
public enum JournalSourceType {
    ACQUISITION,
    DEPRECIATION_RUN,
    CAPITAL_ADDITION,
    REVALUATION,
    IMPAIRMENT,
    DISPOSAL
}