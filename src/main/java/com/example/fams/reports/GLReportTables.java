package com.example.fams.reports;

import com.example.fams.common.AppClock;
import com.example.fams.reports.TabularReport.Column;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Converts the ledger report data classes into the uniform {@link TabularReport} shape. */
final class GLReportTables {

    private GLReportTables() {
    }

    private static Column text(String key, String label) {
        return new Column(key, label, "text");
    }

    private static Column date(String key, String label) {
        return new Column(key, label, "date");
    }

    private static Column money(String key, String label) {
        return new Column(key, label, "money");
    }

    private static Column status(String key, String label) {
        return new Column(key, label, "status");
    }

    private static Map<String, Object> row(Object... keyValues) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            Object value = keyValues[i + 1];
            row.put((String) keyValues[i], value instanceof LocalDate d ? d.toString() : value);
        }
        return row;
    }

    private static Map<String, Object> ordered(Object... keyValues) {
        return row(keyValues);
    }

    private static TabularReport table(String title, String description, List<Column> columns,
                                       List<Map<String, Object>> rows, Map<String, Object> totals,
                                       Map<String, Object> summary, List<String> notes) {
        return new TabularReport(title, description, AppClock.today(), columns, rows,
                totals == null ? Map.<String, Object>of() : totals, summary == null ? Map.<String, Object>of() : summary,
                notes == null ? List.<String>of() : notes, rows.isEmpty());
    }

    private static final List<Column> LINE_COLUMNS = List.of(
            text("journalNumber", "Journal"), date("date", "Date"), text("sourceType", "Source"),
            status("status", "Status"), text("accountCode", "Account"), text("accountName", "Account Name"),
            text("assetCode", "Asset"), text("assetName", "Asset Name"),
            money("debit", "Debit"), money("credit", "Credit"), text("description", "Description"));

    private static List<Map<String, Object>> lineRows(List<JournalLineRow> lines) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (JournalLineRow l : lines) {
            rows.add(row("journalNumber", l.journalNumber(), "date", l.date(), "sourceType", l.sourceType(),
                    "status", l.status(), "accountCode", l.accountCode(), "accountName", l.accountName(),
                    "assetCode", l.assetCode(), "assetName", l.assetName(),
                    "debit", l.debit(), "credit", l.credit(), "description", l.description()));
        }
        return rows;
    }

    static TabularReport lineReport(String title, String description, JournalLineReportData data, List<String> notes) {
        List<Map<String, Object>> rows = data.getRows() == null ? List.of() : lineRows(data.getRows());
        return table(title, description, LINE_COLUMNS, rows,
                rows.isEmpty() ? null : ordered("description", "Totals", "debit", data.getTotalDebit(), "credit", data.getTotalCredit()),
                null, notes);
    }

    static TabularReport journals(String title, String description, JournalEntryReportData data) {
        List<Column> columns = List.of(text("journalNumber", "Journal"), date("date", "Date"), text("period", "Period"),
                text("sourceType", "Source"), status("status", "Status"), text("description", "Description"),
                money("totalDebit", "Total Debit"), money("totalCredit", "Total Credit"),
                text("postedBy", "Posted By"), text("rejectionReason", "Rejection Reason"));
        List<Map<String, Object>> rows = new ArrayList<>();
        BigDecimal debit = BigDecimal.ZERO;
        BigDecimal credit = BigDecimal.ZERO;
        if (data.getRows() != null) {
            for (JournalEntryRow e : data.getRows()) {
                rows.add(row("journalNumber", e.journalNumber(), "date", e.date(), "period", e.period(),
                        "sourceType", e.sourceType(), "status", e.status(), "description", e.description(),
                        "totalDebit", e.totalDebit(), "totalCredit", e.totalCredit(),
                        "postedBy", e.postedBy(), "rejectionReason", e.rejectionReason()));
                debit = debit.add(e.totalDebit());
                credit = credit.add(e.totalCredit());
            }
        }
        return table(title, description, columns, rows,
                rows.isEmpty() ? null : ordered("description", "Totals", "totalDebit", debit, "totalCredit", credit),
                null, null);
    }

    static TabularReport postingSummary(AccountSummaryReportData data) {
        List<Column> columns = List.of(text("accountCode", "Account"), text("accountName", "Account Name"),
                text("accountType", "Type"), money("totalDebit", "Total Debit"), money("totalCredit", "Total Credit"),
                money("netMovement", "Net Movement (Dr − Cr)"));
        List<Map<String, Object>> rows = new ArrayList<>();
        BigDecimal debit = BigDecimal.ZERO;
        BigDecimal credit = BigDecimal.ZERO;
        if (data.getRows() != null) {
            for (AccountSummaryRow r : data.getRows()) {
                rows.add(row("accountCode", r.accountCode(), "accountName", r.accountName(), "accountType", r.accountType(),
                        "totalDebit", r.totalDebit(), "totalCredit", r.totalCredit(), "netMovement", r.netMovement()));
                debit = debit.add(r.totalDebit());
                credit = credit.add(r.totalCredit());
            }
        }
        return table("GL Posting Summary", "Total debits and credits per GL account for the selected journals.", columns, rows,
                rows.isEmpty() ? null : ordered("accountName", "Totals", "totalDebit", debit, "totalCredit", credit,
                        "netMovement", debit.subtract(credit)),
                null, null);
    }

    static TabularReport accountActivity(AccountActivityData data) {
        List<Map<String, Object>> rows = data.getRows() == null ? List.of() : lineRows(data.getRows());
        Map<String, Object> summary = ordered("Account", data.getAccountCode() + " — " + data.getAccountName(),
                "Period", data.getFromDate() + " to " + data.getToDate(),
                "Opening balance", data.getOpeningBalance(),
                "Period debits", data.getPeriodDebit(), "Period credits", data.getPeriodCredit(),
                "Closing balance", data.getClosingBalance());
        return new TabularReport("Asset Account Activity",
                "Posted movement on one GL account over a period, with opening and closing balances.",
                AppClock.today(), LINE_COLUMNS, rows,
                rows.isEmpty() ? Map.<String, Object>of() : ordered("description", "Totals", "debit", data.getPeriodDebit(), "credit", data.getPeriodCredit()),
                summary, List.of("Balances are debit minus credit: a negative figure is a credit balance.",
                "Only posted journals are included."), rows.isEmpty() && data.getOpeningBalance().signum() == 0);
    }

    static TabularReport reconciliation(String title, String description, ReconciliationReportData data) {
        List<Column> columns = List.of(text("accountRole", "Account Role"), text("accountCode", "GL Account"),
                text("accountName", "Account Name"), money("subledgerBalance", "Subledger Balance"),
                money("glBalance", "GL Balance (Posted)"), money("unpostedBalance", "In Unposted Journals"),
                money("difference", "Difference"), money("unexplainedDifference", "Unexplained Difference"));
        List<Map<String, Object>> rows = new ArrayList<>();
        BigDecimal sub = BigDecimal.ZERO, gl = BigDecimal.ZERO, pending = BigDecimal.ZERO,
                diff = BigDecimal.ZERO, unexplained = BigDecimal.ZERO;
        if (data.getRows() != null) {
            for (ReconciliationRow r : data.getRows()) {
                rows.add(row("accountRole", r.accountRole(), "accountCode", r.accountCode(), "accountName", r.accountName(),
                        "subledgerBalance", r.subledgerBalance(), "glBalance", r.glBalance(),
                        "unpostedBalance", r.unpostedBalance(), "difference", r.difference(),
                        "unexplainedDifference", r.unexplainedDifference()));
                sub = sub.add(r.subledgerBalance());
                gl = gl.add(r.glBalance());
                pending = pending.add(r.unpostedBalance());
                diff = diff.add(r.difference());
                unexplained = unexplained.add(r.unexplainedDifference());
            }
        }
        return table(title, description, columns, rows,
                rows.isEmpty() ? null : ordered("accountName", "Totals", "subledgerBalance", sub, "glBalance", gl,
                        "unpostedBalance", pending, "difference", diff, "unexplainedDifference", unexplained),
                null,
                List.of("Difference = subledger − posted GL. Unexplained = difference after allowing for unposted journals.",
                        "Assets and transactions that predate journal generation have no journals and appear as unexplained differences."));
    }

    static TabularReport exceptions(JournalExceptionReportData data) {
        List<Column> columns = List.of(text("exceptionType", "Exception"), text("reference", "Reference"),
                date("date", "Date"), text("sourceType", "Source"), text("assetCode", "Asset"), text("reason", "Reason"));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (JournalExceptionRow e : data.rows()) {
            rows.add(row("exceptionType", e.exceptionType(), "reference", e.reference(), "date", e.date(),
                    "sourceType", e.sourceType(), "assetCode", e.assetCode(), "reason", e.reason()));
        }
        return table("Journal Exception Report",
                "Rejected or unbalanced journals, and transactions whose journal could not be generated.",
                columns, rows, null, null, null);
    }

    static TabularReport periodMovement(PeriodMovementData d) {
        List<Column> columns = List.of(text("component", "Component"), money("amount", "Amount"));
        List<Map<String, Object>> rows = List.of(
                row("component", "Opening net book value", "amount", d.getOpeningNetBookValue()),
                row("component", "Additions (acquisitions)", "amount", d.getAdditions()),
                row("component", "Capital additions", "amount", d.getCapitalAdditions()),
                row("component", "Depreciation", "amount", d.getDepreciation()),
                row("component", "Revaluation movement", "amount", d.getRevaluationMovement()),
                row("component", "Impairment movement", "amount", d.getImpairmentMovement()),
                row("component", "Disposals", "amount", d.getDisposals()),
                row("component", "Closing net book value", "amount", d.getClosingNetBookValue()));
        return table("Period Movement Report",
                "Opening net book value, movement by transaction type and closing net book value for " + d.getFromDate() + " to " + d.getToDate() + ".",
                columns, rows, null, null,
                List.of("Derived from posted journal lines only, so it is complete from when journal generation started.",
                        "Decreases (depreciation, impairment, disposals) are shown as negatives."));
    }

    static TabularReport trialBalance(TrialBalanceData data) {
        List<Column> columns = List.of(text("accountCode", "Account"), text("accountName", "Account Name"),
                text("accountType", "Type"), money("debitBalance", "Debit Balance"), money("creditBalance", "Credit Balance"));
        List<Map<String, Object>> rows = new ArrayList<>();
        if (data.getRows() != null) {
            for (TrialBalanceRow r : data.getRows()) {
                rows.add(row("accountCode", r.accountCode(), "accountName", r.accountName(), "accountType", r.accountType(),
                        "debitBalance", r.debitBalance(), "creditBalance", r.creditBalance()));
            }
        }
        List<String> notes = new ArrayList<>();
        if (!rows.isEmpty() && data.getTotalDebit().compareTo(data.getTotalCredit()) != 0) {
            notes.add("Debits and credits do not agree — the trial balance is out of balance by "
                    + data.getTotalDebit().subtract(data.getTotalCredit()).abs() + ".");
        }
        return table("Trial Balance by Asset Account", "Net balance of each GL account as at " + data.getGeneratedDate() + ".",
                columns, rows,
                rows.isEmpty() ? null : ordered("accountName", "Totals", "debitBalance", data.getTotalDebit(),
                        "creditBalance", data.getTotalCredit()),
                null, notes);
    }
}
