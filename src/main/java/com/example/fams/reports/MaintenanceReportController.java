package com.example.fams.reports;

import com.example.fams.core.config.SecurityScopeService;
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

/** Maintenance reports, scoped to the assets the caller is entitled to see. */
@RestController
@RequestMapping("/api/reports/maintenance")
public class MaintenanceReportController {

    private static final double DEFAULT_HIGH_COST_MULTIPLE = 1.5;

    private final MaintenanceReportService reportService;
    private final TabularExportService exportService;
    private final SecurityScopeService scopeService;

    public MaintenanceReportController(MaintenanceReportService reportService, TabularExportService exportService,
                                       SecurityScopeService scopeService) {
        this.reportService = reportService;
        this.exportService = exportService;
        this.scopeService = scopeService;
    }

    @GetMapping("/{type}")
    public ResponseEntity<Map<String, Object>> report(
            @PathVariable String type,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Double multiple) {
        Map<String, Object> body = new HashMap<>();
        try {
            TabularReport report = build(type, startDate, endDate, multiple);
            if (report == null) {
                body.put("success", false);
                body.put("message", "Unknown maintenance report: " + type);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
            }
            body.put("success", true);
            body.put("data", report);
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            body.put("success", false);
            body.put("message", "Error generating maintenance report: " + e.getMessage());
            return ResponseEntity.badRequest().body(body);
        }
    }

    @GetMapping("/{type}/export")
    public ResponseEntity<byte[]> export(
            @PathVariable String type,
            @RequestParam(defaultValue = "excel") String format,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) Double multiple) {
        TabularReport report;
        try {
            report = build(type, startDate, endDate, multiple);
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

    private TabularReport build(String type, LocalDate from, LocalDate to, Double multiple) {
        SecurityScopeService.ReportScope scope = scopeService.getScope();
        return switch (type) {
            case "history" -> reportService.history(scope, from, to);
            case "scheduled" -> reportService.scheduled(scope);
            case "due" -> reportService.due(scope, from, to);
            case "overdue" -> reportService.overdue(scope);
            case "cost-by-asset" -> reportService.costByAsset(scope, from, to);
            case "cost-by-category" -> reportService.costByCategory(scope, from, to);
            case "cost-by-location" -> reportService.costByLocation(scope, from, to);
            case "breakdown-history" -> reportService.breakdownHistory(scope, from, to);
            case "downtime" -> reportService.downtime(scope, from, to);
            case "contract-expiry" -> reportService.contractExpiry(scope, from, to);
            case "high-cost-assets" -> reportService.highCostAssets(scope, from, to,
                    multiple != null && multiple > 0 ? multiple : DEFAULT_HIGH_COST_MULTIPLE);
            default -> null;
        };
    }
}
