package com.example.fams.reports;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Section 8, item 13 — Period Movement Report: opening net book value, movement by
 * type, and closing net book value for a period. Computed entirely from journal line
 * history (see class javadoc on {@link AccountActivityData} for why this is reliable in
 * a way Section 5's Carrying Amount Movement Report was not) — accurate from whenever
 * journal generation started, not before.
 */
public class PeriodMovementData {
    private LocalDate fromDate;
    private LocalDate toDate;
    private BigDecimal openingNetBookValue;
    private BigDecimal additions;
    private BigDecimal capitalAdditions;
    private BigDecimal depreciation;
    private BigDecimal revaluationMovement;
    private BigDecimal impairmentMovement;
    private BigDecimal disposals;
    private BigDecimal closingNetBookValue;

    public LocalDate getFromDate() {
        return fromDate;
    }

    public void setFromDate(LocalDate fromDate) {
        this.fromDate = fromDate;
    }

    public LocalDate getToDate() {
        return toDate;
    }

    public void setToDate(LocalDate toDate) {
        this.toDate = toDate;
    }

    public BigDecimal getOpeningNetBookValue() {
        return openingNetBookValue;
    }

    public void setOpeningNetBookValue(BigDecimal openingNetBookValue) {
        this.openingNetBookValue = openingNetBookValue;
    }

    public BigDecimal getAdditions() {
        return additions;
    }

    public void setAdditions(BigDecimal additions) {
        this.additions = additions;
    }

    public BigDecimal getCapitalAdditions() {
        return capitalAdditions;
    }

    public void setCapitalAdditions(BigDecimal capitalAdditions) {
        this.capitalAdditions = capitalAdditions;
    }

    public BigDecimal getDepreciation() {
        return depreciation;
    }

    public void setDepreciation(BigDecimal depreciation) {
        this.depreciation = depreciation;
    }

    public BigDecimal getRevaluationMovement() {
        return revaluationMovement;
    }

    public void setRevaluationMovement(BigDecimal revaluationMovement) {
        this.revaluationMovement = revaluationMovement;
    }

    public BigDecimal getImpairmentMovement() {
        return impairmentMovement;
    }

    public void setImpairmentMovement(BigDecimal impairmentMovement) {
        this.impairmentMovement = impairmentMovement;
    }

    public BigDecimal getDisposals() {
        return disposals;
    }

    public void setDisposals(BigDecimal disposals) {
        this.disposals = disposals;
    }

    public BigDecimal getClosingNetBookValue() {
        return closingNetBookValue;
    }

    public void setClosingNetBookValue(BigDecimal closingNetBookValue) {
        this.closingNetBookValue = closingNetBookValue;
    }
}