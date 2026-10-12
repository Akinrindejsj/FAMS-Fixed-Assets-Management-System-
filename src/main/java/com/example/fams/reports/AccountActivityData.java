package com.example.fams.reports;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Section 8, item 5 — Asset Account Activity: movement on one GL account over a period.
 * Unlike Section 5's Carrying Amount Movement (blocked — no asset-level snapshots exist),
 * this IS computable correctly: the opening balance is the cumulative net of every
 * journal line posted to this account before the period start, which the ledger has
 * always recorded. Accurate from whenever journal generation started; activity from
 * before that point (if any assets/transactions predate GL wiring) won't appear.
 */
public class AccountActivityData {
    private String accountCode;
    private String accountName;
    private LocalDate fromDate;
    private LocalDate toDate;
    private BigDecimal openingBalance;
    private List<JournalLineRow> rows;
    private BigDecimal periodDebit;
    private BigDecimal periodCredit;
    private BigDecimal closingBalance;
    private boolean empty = true;

    public String getAccountCode() {
        return accountCode;
    }

    public void setAccountCode(String accountCode) {
        this.accountCode = accountCode;
    }

    public String getAccountName() {
        return accountName;
    }

    public void setAccountName(String accountName) {
        this.accountName = accountName;
    }

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

    public BigDecimal getOpeningBalance() {
        return openingBalance;
    }

    public void setOpeningBalance(BigDecimal openingBalance) {
        this.openingBalance = openingBalance;
    }

    public List<JournalLineRow> getRows() {
        return rows;
    }

    public void setRows(List<JournalLineRow> rows) {
        this.rows = rows;
        this.empty = rows == null || rows.isEmpty();
    }

    public BigDecimal getPeriodDebit() {
        return periodDebit;
    }

    public void setPeriodDebit(BigDecimal periodDebit) {
        this.periodDebit = periodDebit;
    }

    public BigDecimal getPeriodCredit() {
        return periodCredit;
    }

    public void setPeriodCredit(BigDecimal periodCredit) {
        this.periodCredit = periodCredit;
    }

    public BigDecimal getClosingBalance() {
        return closingBalance;
    }

    public void setClosingBalance(BigDecimal closingBalance) {
        this.closingBalance = closingBalance;
    }

    public boolean isEmpty() {
        return empty;
    }

    public void setEmpty(boolean empty) {
        this.empty = empty;
    }
}