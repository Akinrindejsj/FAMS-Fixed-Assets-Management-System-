package com.example.fams.reports;

import java.time.LocalDate;
import java.util.List;

public class AssignmentReportData {
    private LocalDate generatedDate = LocalDate.now();
    private List<AssignmentRow> rows;
    private int totalRecords;
    private boolean empty = true;

    public LocalDate getGeneratedDate() {
        return generatedDate;
    }

    public void setGeneratedDate(LocalDate generatedDate) {
        this.generatedDate = generatedDate;
    }

    public List<AssignmentRow> getRows() {
        return rows;
    }

    public void setRows(List<AssignmentRow> rows) {
        this.rows = rows;
        this.empty = rows == null || rows.isEmpty();
        this.totalRecords = rows == null ? 0 : rows.size();
    }

    public int getTotalRecords() {
        return totalRecords;
    }

    public void setTotalRecords(int totalRecords) {
        this.totalRecords = totalRecords;
    }

    public boolean isEmpty() {
        return empty;
    }

    public void setEmpty(boolean empty) {
        this.empty = empty;
    }
}