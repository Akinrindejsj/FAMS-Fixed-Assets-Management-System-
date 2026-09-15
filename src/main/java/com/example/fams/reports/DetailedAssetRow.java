package com.example.fams.reports;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;


public record DetailedAssetRow(
        Long assetId,
        String assetCode,
        String assetName,
        String category,
        String description,
        String serialNumber,
        String manufacturer,
        String model,
        LocalDate acquisitionDate,
        BigDecimal acquisitionCost,
        String vendor,
        String branch,
        String department,
        String CostCenter,
        String custodian,
        BigDecimal accumulatedDepreciation,
        BigDecimal netBookValue,
        LocalDate warrantyExpiry,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
