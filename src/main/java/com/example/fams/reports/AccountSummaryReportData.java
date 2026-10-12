package com.example.fams.reports;

import java.time.LocalDate;
import java.util.List;

public class AccountSummaryReportData {
    private LocalDate generatedDate = LocalDate.now();
    private List<AccountSummaryRow> rows;
    private boolean empty = true;

    public LocalDate getGeneratedDate() {
        return generatedDate;
    }

    public void setGeneratedDate(LocalDate generatedDate) {
        this.generatedDate = generatedDate;
    }

    public List<AccountSummaryRow> getRows() {
        return rows;
    }

    public void setRows(List<AccountSummaryRow> rows) {
        this.rows = rows;
        this.empty = rows == null || rows.isEmpty();
    }

    public boolean isEmpty() {
        return empty;
    }

    public void setEmpty(boolean empty) {
        this.empty = empty;
    }
}