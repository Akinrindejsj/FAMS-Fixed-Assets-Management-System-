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

/** Management and executive reports, scoped to the assets the caller may see. */
@RestController
@RequestMapping("/api/reports/management")
public class ManagementReportController {

    private final ManagementReportService reportService;
    private final TabularExportService exportService;
    private final SecurityScopeService scopeService;

    public ManagementReportController(ManagementReportService reportService, TabularExportService exportService,
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
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) Integer threshold) {
        Map<String, Object> body = new HashMap<>();
        try {
            TabularReport report = build(type, startDate, endDate, category, department, threshold);
            if (report == null) {
                body.put("success", false);
                body.put("message", "Unknown report: " + type);
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(body);
            }
            body.put("success", true);
            body.put("data", report);
            return ResponseEntity.ok(body);
        } catch (Exception e) {
            body.put("success", false);
            body.put("message", e.getMessage());
            return ResponseEntity.badRequest().body(body);
        }
    }

    @GetMapping("/{type}/export")
    public ResponseEntity<byte[]> export(
            @PathVariable String type,
            @RequestParam(defaultValue = "excel") String format,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) Integer threshold) {
        TabularReport report;
        try {
            report = build(type, startDate, endDate, category, department, threshold);
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

    private TabularReport build(String type, LocalDate from, LocalDate to, String category, String department, Integer threshold) {
        SecurityScopeService.ReportScope scope = scopeService.getScope();
        return switch (type) {
            case "executive-summary" -> reportService.executiveSummary(scope, category, department, from, to);
            case "capex" -> reportService.capex(scope, category, department, from, to);
            case "monthly-additions" -> reportService.monthlyAdditions(scope, category, department, from, to);
            case "monthly-disposals" -> reportService.monthlyDisposals(scope, category, department, from, to);
            case "monthly-depreciation" -> reportService.monthlyDepreciation(scope, category, department, from, to);
            case "maintenance-trend" -> reportService.maintenanceTrend(scope, category, department, from, to);
            case "growth-trend" -> reportService.growthTrend(scope, category, department, from, to);
            case "utilisation" -> reportService.utilisation(scope, category, department, from, to);
            case "condition-summary" -> reportService.conditionSummary(scope, category, department);
            case "replacement-forecast" -> reportService.replacementForecast(scope, category, department, threshold);
            case "top-cost" -> reportService.topAssets(scope, category, department, false);
            case "top-nbv" -> reportService.topAssets(scope, category, department, true);
            default -> null;
        };
    }
}
