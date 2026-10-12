package com.example.fams.reports;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * One disposal workflow entry. Reused across Asset
 * Disposal Register, Disposal Detail, Disposed by Period/Category, Asset Sales,
 * Scrapped Assets, Disposal Proceeds, Gain/Loss on Disposal, and Asset Retirement — they
 * differ only in which workflows are selected (status, disposalMethod, date range), not
 * in row shape.
 */
public record DisposalRow(
        LocalDate date,
        String assetCode,
        String assetName,
        String category,
        String department,
        String status,
        String disposalMethod,
        BigDecimal disposalProceeds,
        BigDecimal accumulatedDepreciation,
        BigDecimal netBookValue,
        BigDecimal financialImpact,
        String reason,
        String requestedBy
) {
}