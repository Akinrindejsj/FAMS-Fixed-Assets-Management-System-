package com.example.fams.reports;

import com.example.fams.core.config.SecurityScopeService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/** Depreciation and valuation reports in table form, scoped to the assets the caller may see. */
@RestController
@RequestMapping("/api/reports/depreciation-tables")
public class DepreciationReportController {

    private final DepreciationReportService reportService;
    private final TabularExportService exportService;
    private final SecurityScopeService scopeService;

    public DepreciationReportController(DepreciationReportService reportService, TabularExportService exportService,
                                        SecurityScopeService scopeService) {
        this.reportService = reportService;
        this.exportService = exportService;
        this.scopeService = scopeService;
    }

    @GetMapping("/{type}")
    public ResponseEntity<Map<String, Object>> report(@PathVariable String type,
                                                      @RequestParam(required = false) String period,
                                                      @RequestParam(required = false) String category,
                                                      @RequestParam(required = false) String department) {
        Map<String, Object> body = new HashMap<>();
        try {
            TabularReport report = build(type, period, category, department);
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
    public ResponseEntity<byte[]> export(@PathVariable String type,
                                         @RequestParam(defaultValue = "excel") String format,
                                         @RequestParam(required = false) String period,
                                         @RequestParam(required = false) String category,
                                         @RequestParam(required = false) String department) {
        TabularReport report;
        try {
            report = build(type, period, category, department);
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

    private TabularReport build(String type, String period, String category, String department) {
        SecurityScopeService.ReportScope scope = scopeService.getScope();
        return switch (type) {
            case "by-location" -> reportService.byLocation(scope, period);
            case "by-gl-account" -> reportService.byGlAccount(scope, period);
            case "cost-vs-accumulated-depreciation" -> reportService.costVsAccumulated(scope, category, department);
            default -> null;
        };
    }
}
