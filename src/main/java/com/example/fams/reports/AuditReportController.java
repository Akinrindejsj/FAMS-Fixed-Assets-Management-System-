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
import java.util.NoSuchElementException;

/**
 * Physical verification reports. Restricted to admin, asset manager and auditor roles;
 * these reports are not department-scoped.
 */
@RestController
@RequestMapping("/api/reports/audit")
public class AuditReportController {

    private final AuditReportService reportService;
    private final TabularExportService exportService;
    private final AuthenticationManager authenticationManager;

    public AuditReportController(AuditReportService reportService, TabularExportService exportService,
                                 AuthenticationManager authenticationManager) {
        this.reportService = reportService;
        this.exportService = exportService;
        this.authenticationManager = authenticationManager;
    }

    private boolean allowed() {
        return authenticationManager.isAdmin() || authenticationManager.isAssetManager() || authenticationManager.isAuditor();
    }

    private ResponseEntity<Map<String, Object>> failure(HttpStatus status, String message) {
        Map<String, Object> body = new HashMap<>();
        body.put("success", false);
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }

    /** Audit sessions for the report's session filter. */
    @GetMapping("/sessions")
    public ResponseEntity<Map<String, Object>> sessions() {
        if (!allowed()) return failure(HttpStatus.FORBIDDEN, "You do not have access to verification reports.");
        Map<String, Object> body = new HashMap<>();
        body.put("success", true);
        body.put("data", reportService.sessionOptions());
        return ResponseEntity.ok(body);
    }

    @GetMapping("/{type}")
    public ResponseEntity<Map<String, Object>> report(
            @PathVariable String type,
            @RequestParam(required = false) Long sessionId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        if (!allowed()) return failure(HttpStatus.FORBIDDEN, "You do not have access to verification reports.");
        try {
            TabularReport report = build(type, sessionId, startDate, endDate);
            if (report == null) return failure(HttpStatus.NOT_FOUND, "Unknown verification report: " + type);
            Map<String, Object> body = new HashMap<>();
            body.put("success", true);
            body.put("data", report);
            return ResponseEntity.ok(body);
        } catch (IllegalArgumentException | NoSuchElementException e) {
            return failure(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (Exception e) {
            return failure(HttpStatus.BAD_REQUEST, "Error generating verification report: " + e.getMessage());
        }
    }

    @GetMapping("/{type}/export")
    public ResponseEntity<byte[]> export(
            @PathVariable String type,
            @RequestParam(defaultValue = "excel") String format,
            @RequestParam(required = false) Long sessionId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        if (!allowed()) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        TabularReport report;
        try {
            report = build(type, sessionId, startDate, endDate);
        } catch (IllegalArgumentException | NoSuchElementException e) {
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

    private TabularReport build(String type, Long sessionId, LocalDate from, LocalDate to) {
        return switch (type) {
            case "verification-report" -> reportService.verificationReport(sessionId, from, to);
            case "asset-count" -> reportService.assetCount(sessionId, from, to);
            case "verified-assets" -> reportService.verifiedAssets(sessionId, from, to);
            case "unverified-assets" -> reportService.unverifiedAssets(sessionId);
            case "missing-assets" -> reportService.missingAssets(sessionId, from, to);
            case "wrong-location" -> reportService.wrongLocation(sessionId, from, to);
            case "custodian-mismatch" -> reportService.custodianMismatch(sessionId, from, to);
            case "unexpected-assets" -> reportService.unexpectedAssets(sessionId, from, to);
            case "damaged-assets" -> reportService.damagedAssets(sessionId, from, to);
            case "condition-report" -> reportService.conditionReport(sessionId, from, to);
            case "exception-report" -> reportService.exceptionReport(sessionId, from, to);
            case "reconciliation" -> reportService.reconciliation(sessionId, from, to);
            case "scan-log" -> reportService.scanLog(sessionId, from, to);
            case "last-verification" -> reportService.lastVerification();
            default -> null;
        };
    }
}
