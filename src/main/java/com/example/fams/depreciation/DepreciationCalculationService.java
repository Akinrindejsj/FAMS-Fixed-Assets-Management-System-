package com.example.fams.depreciation;

import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Service for calculating depreciation using different methods.
 * Supports: Straight-Line, Reducing Balance, and Double Declining Balance
 */
@Service
public class DepreciationCalculationService {

    private static final int SCALE = 2;
    private static final RoundingMode ROUNDING_MODE = RoundingMode.HALF_UP;

    /**
     * Calculate annual depreciation based on the method
     */
    public BigDecimal calculateAnnualDepreciation(
            BigDecimal assetCost,
            BigDecimal residualValue,
            Integer usefulLifeYears,
            DepreciationMethod method,
            Integer yearNumber,
            BigDecimal accumulatedDepreciation) {

        if (assetCost == null || usefulLifeYears == null || usefulLifeYears <= 0) {
            return BigDecimal.ZERO;
        }

        if (residualValue == null) {
            residualValue = BigDecimal.ZERO;
        }

        // Check if asset is fully depreciated
        if (isFullyDepreciated(assetCost, residualValue, accumulatedDepreciation)) {
            return BigDecimal.ZERO;
        }

        return switch (method) {
            case STRAIGHT_LINE -> calculateStraightLine(assetCost, residualValue, usefulLifeYears);
            case REDUCING_BALANCE -> calculateReducingBalance(assetCost, residualValue, usefulLifeYears, accumulatedDepreciation, yearNumber);
            case DOUBLE_DECLINING_BALANCE -> calculateDoubleDecliningBalance(assetCost, residualValue, usefulLifeYears, accumulatedDepreciation, yearNumber);
        };
    }

    /**
     * Straight-Line Depreciation: (Cost - Residual Value) / Useful Life
     * Same amount each year
     */
    private BigDecimal calculateStraightLine(BigDecimal assetCost, BigDecimal residualValue, Integer usefulLifeYears) {
        BigDecimal depreciableAmount = assetCost.subtract(residualValue);
        BigDecimal yearsDecimal = new BigDecimal(usefulLifeYears);
        return depreciableAmount.divide(yearsDecimal, SCALE, ROUNDING_MODE);
    }

    /**
     * Reducing Balance / Declining Balance:
     * Uses a fixed rate applied to the book value (original cost - accumulated depreciation)
     * Rate = 1 - (Residual / Cost) ^ (1/Years)
     */
    private BigDecimal calculateReducingBalance(
            BigDecimal assetCost,
            BigDecimal residualValue,
            Integer usefulLifeYears,
            BigDecimal accumulatedDepreciation,
            Integer yearNumber) {

        // Calculate the rate: typically 1/usefulLifeYears for standard reducing balance
        BigDecimal rate = new BigDecimal(1).divide(new BigDecimal(usefulLifeYears), SCALE + 2, ROUNDING_MODE);

        // Current book value
        BigDecimal bookValue = assetCost.subtract(accumulatedDepreciation);

        // Calculate depreciation for this year
        BigDecimal depreciation = bookValue.multiply(rate).setScale(SCALE, ROUNDING_MODE);

        // Ensure we don't depreciate below residual value
        BigDecimal projectedAccumulated = accumulatedDepreciation.add(depreciation);
        if (projectedAccumulated.add(depreciation).compareTo(assetCost.subtract(residualValue)) > 0) {
            depreciation = assetCost.subtract(residualValue).subtract(accumulatedDepreciation);
        }

        return depreciation.max(BigDecimal.ZERO);
    }

    /**
     * Double Declining Balance (DDB):
     * Uses double the straight-line rate applied to the declining book value
     * Rate = 2 / Useful Life
     */
    private BigDecimal calculateDoubleDecliningBalance(
            BigDecimal assetCost,
            BigDecimal residualValue,
            Integer usefulLifeYears,
            BigDecimal accumulatedDepreciation,
            Integer yearNumber) {

        // Double declining rate: 2 / useful life
        BigDecimal rate = new BigDecimal(2).divide(new BigDecimal(usefulLifeYears), SCALE + 2, ROUNDING_MODE);

        // Current book value
        BigDecimal bookValue = assetCost.subtract(accumulatedDepreciation);

        // Calculate depreciation for this year using DDB rate
        BigDecimal depreciation = bookValue.multiply(rate).setScale(SCALE, ROUNDING_MODE);

        // Ensure we don't depreciate below residual value
        BigDecimal projectedAccumulated = accumulatedDepreciation.add(depreciation);
        if (projectedAccumulated.compareTo(assetCost.subtract(residualValue)) > 0) {
            depreciation = assetCost.subtract(residualValue).subtract(accumulatedDepreciation);
        }

        return depreciation.max(BigDecimal.ZERO);
    }

    /**
     * Check if an asset is fully depreciated
     */
    public boolean isFullyDepreciated(BigDecimal assetCost, BigDecimal residualValue, BigDecimal accumulatedDepreciation) {
        if (residualValue == null) {
            residualValue = BigDecimal.ZERO;
        }
        BigDecimal depreciableAmount = assetCost.subtract(residualValue);
        return accumulatedDepreciation.compareTo(depreciableAmount) >= 0;
    }

    /**
     * Calculate monthly depreciation (annual / 12)
     */
    public BigDecimal calculateMonthlyDepreciation(BigDecimal annualDepreciation) {
        if (annualDepreciation == null) {
            return BigDecimal.ZERO;
        }
        return annualDepreciation.divide(new BigDecimal(12), SCALE, ROUNDING_MODE);
    }

    /**
     * Calculate depreciation for partial year
     */
    public BigDecimal calculatePartialYearDepreciation(BigDecimal annualDepreciation, Integer months) {
        if (annualDepreciation == null || months == null || months <= 0) {
            return BigDecimal.ZERO;
        }
        BigDecimal monthlyRate = annualDepreciation.divide(new BigDecimal(12), SCALE + 2, ROUNDING_MODE);
        return monthlyRate.multiply(new BigDecimal(months)).setScale(SCALE, ROUNDING_MODE);
    }

    /**
     * Calculate book value
     */
    public BigDecimal calculateBookValue(BigDecimal assetCost, BigDecimal accumulatedDepreciation) {
        if (assetCost == null) {
            return BigDecimal.ZERO;
        }
        if (accumulatedDepreciation == null) {
            accumulatedDepreciation = BigDecimal.ZERO;
        }
        return assetCost.subtract(accumulatedDepreciation).max(BigDecimal.ZERO);
    }
}

