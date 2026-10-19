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
 * Insurance and compliance reports. Restricted to admin, asset manager, financial officer
 * and auditor roles; these reports are not department-scoped.
 */
@RestController
@RequestMapping("/api/reports/insurance")
public class InsuranceReportController {

    private final InsuranceReportService reportService;
    private final TabularExportService exportService;
    private final AuthenticationManager authenticationManager;

    public InsuranceReportController(InsuranceReportService reportService, TabularExportService exportService,
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
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        if (!allowed()) return failure(HttpStatus.FORBIDDEN, "You do not have access to insurance reports.");
        try {
            TabularReport report = build(type, startDate, endDate);
            if (report == null) return failure(HttpStatus.NOT_FOUND, "Unknown insurance report: " + type);
            Map<String, Object> body = new HashMap<>();
            body.put("success", true);
            body.put("data", report);
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            return failure(HttpStatus.BAD_REQUEST, "Error generating insurance report: " + e.getMessage());
        }
    }

    @GetMapping("/{type}/export")
    public ResponseEntity<byte[]> export(
            @PathVariable String type,
            @RequestParam(defaultValue = "excel") String format,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        if (!allowed()) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        TabularReport report;
        try {
            report = build(type, startDate, endDate);
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

    private TabularReport build(String type, LocalDate from, LocalDate to) {
        return switch (type) {
            case "insured-assets" -> reportService.insuredAssets();
            case "uninsured-assets" -> reportService.uninsuredAssets();
            case "policy-register" -> reportService.policyRegister();
            case "coverage-by-asset" -> reportService.coverageByAsset();
            case "insurance-expiry" -> reportService.insuranceExpiry(from, to);
            case "value-vs-coverage" -> reportService.valueVsCoverage();
            case "underinsured-assets" -> reportService.underinsured();
            case "compliance" -> reportService.certificationCompliance();
            case "certification-expiry" -> reportService.certificationExpiry(from, to);
            case "claim-history" -> reportService.claimHistory(from, to);
            default -> null;
        };
    }
}
