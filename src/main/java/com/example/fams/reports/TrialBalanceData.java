package com.example.fams.reports;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public class TrialBalanceData {
    private LocalDate generatedDate = LocalDate.now();
    private List<TrialBalanceRow> rows;
    private BigDecimal totalDebit;
    private BigDecimal totalCredit;
    private boolean empty = true;

    public LocalDate getGeneratedDate() {
        return generatedDate;
    }

    public void setGeneratedDate(LocalDate generatedDate) {
        this.generatedDate = generatedDate;
    }

    public List<TrialBalanceRow> getRows() {
        return rows;
    }

    public void setRows(List<TrialBalanceRow> rows) {
        this.rows = rows;
        this.empty = rows == null || rows.isEmpty();
    }

    public BigDecimal getTotalDebit() {
        return totalDebit;
    }

    public void setTotalDebit(BigDecimal totalDebit) {
        this.totalDebit = totalDebit;
    }

    public BigDecimal getTotalCredit() {
        return totalCredit;
    }

    public void setTotalCredit(BigDecimal totalCredit) {
        this.totalCredit = totalCredit;
    }

    public boolean isEmpty() {
        return empty;
    }

    public void setEmpty(boolean empty) {
        this.empty = empty;
    }
}