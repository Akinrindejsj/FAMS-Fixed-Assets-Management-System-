package com.example.fams.reports;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * A report in a uniform table shape, so the reports page and the Excel/PDF exports can
 * render any of them with one generic view.
 *
 * <p>Column {@code type} is one of {@code text}, {@code date}, {@code money} or
 * {@code status}; dates are ISO strings and money values are numbers. {@code totals} is
 * keyed by column key and shown as a final row; {@code summary} is an ordered label-to-value
 * map shown as headline figures above the table.
 */
public record TabularReport(
        String title,
        String description,
        LocalDate generatedDate,
        List<Column> columns,
        List<Map<String, Object>> rows,
        Map<String, Object> totals,
        Map<String, Object> summary,
        List<String> notes,
        boolean empty
) {
    public record Column(String key, String label, String type) {
    }
}
