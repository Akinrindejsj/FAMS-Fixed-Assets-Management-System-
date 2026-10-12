package com.example.fams.reports;

import java.math.BigDecimal;
import java.time.LocalDate;

/** A component asset alongside its parent asset's identity. */
public record ComponentRow(
        String componentCode,
        String componentName,
        String category,
        LocalDate acquisitionDate,
        BigDecimal acquisitionCost,
        String status,
        String parentAssetCode,
        String parentAssetName
) {
}
