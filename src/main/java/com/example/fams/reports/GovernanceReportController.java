package com.example.fams.reports;

import com.example.fams.core.config.AuthenticationManager;
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
import java.util.HashMap;
import java.util.Map;

/**
 * Audit and governance reports. Restricted to admin, asset manager, financial officer and
 * auditor roles; these reports are not department-scoped.
 */
@RestController
@RequestMapping("/api/reports/governance")
public class GovernanceReportController {

    private final GovernanceReportService reportService;
    private final TabularExportService exportService;
    private final AuthenticationManager authenticationManager;

    public GovernanceReportController(GovernanceReportService reportService, TabularExportService exportService,
                                      AuthenticationManager authenticationManager) {
        this.reportService = reportService;
        this.exportService = exportService;
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

    @GetMapping("/{type}")
    public ResponseEntity<Map<String, Object>> report(
            @PathVariable String type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Integer threshold) {
        if (!allowed()) return failure(HttpStatus.FORBIDDEN, "You do not have access to audit and governance reports.");
        try {
            TabularReport report = build(type, startDate, endDate, threshold);
            if (report == null) return failure(HttpStatus.NOT_FOUND, "Unknown report: " + type);
            Map<String, Object> body = new HashMap<>();
            body.put("success", true);
            body.put("data", report);
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            return failure(HttpStatus.BAD_REQUEST, "Error generating report: " + e.getMessage());
        }
    }

    @GetMapping("/{type}/export")
    public ResponseEntity<byte[]> export(
            @PathVariable String type,
            @RequestParam(defaultValue = "excel") String format,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Integer threshold) {
        if (!allowed()) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        TabularReport report;
        try {
            report = build(type, startDate, endDate, threshold);
        } catch (Exception e) {
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

    private TabularReport build(String type, LocalDate from, LocalDate to, Integer threshold) {
        return switch (type) {
            case "transaction-trail" -> reportService.transactionTrail(from, to);
            case "master-changes" -> reportService.masterChanges(from, to);
            case "user-activity" -> reportService.userActivity(from, to);
            case "approvals" -> reportService.approvalHistory(from, to);
            case "depreciation-approvals" -> reportService.depreciationApprovals(from, to);
            case "disposal-approvals" -> reportService.disposalApprovals(from, to);
            case "transfer-approvals" -> reportService.transferApprovals(from, to);
            case "revaluation-approvals" -> reportService.revaluationApprovals(from, to);
            case "creation-modification" -> reportService.creationModification(from, to);
            case "deactivated-assets" -> reportService.deactivatedAssets(from, to);
            case "backdated" -> reportService.backdated(from, to, threshold);
            case "manual-adjustments" -> reportService.manualAdjustments(from, to);
            case "overrides" -> reportService.overrides(from, to);
            case "sod-exceptions" -> reportService.segregationOfDuties(from, to);
            case "period-close" -> reportService.periodClose(from, to);
            default -> null;
        };
    }
}
