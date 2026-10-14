package com.example.fams.reports;

import com.example.fams.core.config.AuthenticationManager;
import com.example.fams.gl.GLAccount;
import com.example.fams.gl.GLAccountRepository;
import com.example.fams.gl.JournalStatus;
import com.example.fams.reports.GLReportService.ReconRole;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Ledger-based reports. Restricted to admin, asset manager, financial officer and auditor
 * roles; these reports are not department-scoped.
 */
@RestController
@RequestMapping("/api/reports/gl")
public class GLReportController {

    private final GLReportService reportService;
    private final TabularExportService exportService;
    private final GLAccountRepository accountRepository;
    private final AuthenticationManager authenticationManager;

    public GLReportController(GLReportService reportService, TabularExportService exportService,
                              GLAccountRepository accountRepository, AuthenticationManager authenticationManager) {
        this.reportService = reportService;
        this.exportService = exportService;
        this.accountRepository = accountRepository;
        this.authenticationManager = authenticationManager;
    }

    private boolean allowed() {
        return authenticationManager.isAdmin() || authenticationManager.isAuditor()
                || authenticationManager.isAssetManager() || authenticationManager.isFinancialOfficer();
    }

    private ResponseEntity<Map<String, Object>> failure(HttpStatus status, String message) {
        Map<String, Object> body = new HashMap<>();
        body.put("success", false);
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }

    /** GL accounts for the report account filter. */
    @GetMapping("/accounts")
    public ResponseEntity<Map<String, Object>> accounts() {
        if (!allowed()) return failure(HttpStatus.FORBIDDEN, "You do not have access to ledger reports.");
        List<Map<String, Object>> accounts = accountRepository.findAllByOrderByAccountCodeAsc().stream()
                .map((GLAccount a) -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("id", a.getId());
                    m.put("label", a.getAccountCode() + " — " + a.getAccountName());
                    return m;
                }).toList();
        Map<String, Object> body = new HashMap<>();
        body.put("success", true);
        body.put("data", accounts);
        return ResponseEntity.ok(body);
    }

    @GetMapping("/{type}")
    public ResponseEntity<Map<String, Object>> report(
            @PathVariable String type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long accountId,
            @RequestParam(required = false) String period) {
        if (!allowed()) return failure(HttpStatus.FORBIDDEN, "You do not have access to ledger reports.");
        try {
            TabularReport report = build(type, startDate, endDate, status, accountId, period);
            if (report == null) return failure(HttpStatus.NOT_FOUND, "Unknown ledger report: " + type);
            Map<String, Object> body = new HashMap<>();
            body.put("success", true);
            body.put("data", report);
            return ResponseEntity.ok(body);
        } catch (IllegalArgumentException e) {
            return failure(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            return failure(HttpStatus.BAD_REQUEST, "Error generating ledger report: " + e.getMessage());
        }
    }

    @GetMapping("/{type}/export")
    public ResponseEntity<byte[]> export(
            @PathVariable String type,
            @RequestParam(defaultValue = "excel") String format,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long accountId,
            @RequestParam(required = false) String period) {
        if (!allowed()) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        TabularReport report;
        try {
            report = build(type, startDate, endDate, status, accountId, period);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
        if (report == null) return ResponseEntity.notFound().build();

        boolean pdf = "pdf".equalsIgnoreCase(format);
        byte[] bytes = pdf ? exportService.toPdf(report) : exportService.toExcel(report);
        String filename = type + "-" + report.generatedDate() + (pdf ? ".pdf" : ".xlsx");
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(pdf ? MediaType.APPLICATION_PDF
                        : MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(bytes);
    }

    private TabularReport build(String type, LocalDate from, LocalDate to, String status, Long accountId, String period) {
        List<JournalStatus> all = List.of(JournalStatus.values());
        return switch (type) {
            case "subledger" -> GLReportTables.lineReport("Fixed Asset Subledger",
                    "Every journal line booked against an asset, grouped by asset (unposted and posted).",
                    reportService.subledger(from, to, accountId),
                    List.of("Rejected journals are excluded."));
            case "journals" -> GLReportTables.journals("Fixed Asset Journal Report",
                    "All journal entries generated from asset transactions.",
                    reportService.journals(from, to, reportService.parseStatuses(status, all)));
            case "unposted-journals" -> GLReportTables.journals("Unposted Journal Report",
                    "Journal entries waiting to be posted or rejected.",
                    reportService.journals(from, to, List.of(JournalStatus.UNPOSTED)));
            case "posted-journals" -> GLReportTables.journals("Posted Journal Report",
                    "Journal entries that have been posted to the ledger.",
                    reportService.journals(from, to, List.of(JournalStatus.POSTED)));
            case "account-activity" -> GLReportTables.accountActivity(reportService.accountActivity(accountId, from, to));
            case "cost-reconciliation" -> GLReportTables.reconciliation("Asset Cost Reconciliation",
                    "Asset cost per the asset register (including capital additions and revaluations) against the GL cost account.",
                    reportService.reconcile(EnumSet.of(ReconRole.COST), null));
            case "accumulated-depreciation-reconciliation" -> GLReportTables.reconciliation("Accumulated Depreciation Reconciliation",
                    "Latest accumulated depreciation per the depreciation postings against the GL accumulated depreciation account.",
                    reportService.reconcile(EnumSet.of(ReconRole.ACCUMULATED_DEPRECIATION), null));
            case "depreciation-expense-reconciliation" -> GLReportTables.reconciliation("Depreciation Expense Reconciliation",
                    "Depreciation charged per the depreciation postings against the GL depreciation expense account.",
                    reportService.reconcile(EnumSet.of(ReconRole.DEPRECIATION_EXPENSE), period));
            case "fixed-asset-gl-reconciliation" -> GLReportTables.reconciliation("Fixed Asset to GL Reconciliation",
                    "Cost, accumulated depreciation, depreciation expense and accumulated impairment against the ledger.",
                    reportService.reconcile(EnumSet.allOf(ReconRole.class), period));
            case "posting-summary" -> GLReportTables.postingSummary(
                    reportService.postingSummary(from, to, reportService.parseStatuses(status, List.of(JournalStatus.POSTED))));
            case "posting-detail" -> GLReportTables.lineReport("GL Posting Detail",
                    "Individual journal lines generated from asset activity.",
                    reportService.postingDetail(from, to, reportService.parseStatuses(status, List.of(JournalStatus.POSTED)), accountId),
                    null);
            case "journal-exceptions" -> GLReportTables.exceptions(reportService.journalExceptions(from, to));
            case "period-movement" -> GLReportTables.periodMovement(reportService.periodMovement(from, to));
            case "trial-balance" -> GLReportTables.trialBalance(
                    reportService.trialBalance(to, reportService.parseStatuses(status, List.of(JournalStatus.POSTED))));
            default -> null;
        };
    }
}
