package com.example.fams.reports;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** One period's worth of acquisition activity in the trend report. */
public record TrendPoint(String period, int assetCount, BigDecimal totalCost) {
}
