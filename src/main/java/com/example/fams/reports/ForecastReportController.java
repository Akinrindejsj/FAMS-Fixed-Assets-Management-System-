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

/** Forecasting and analytical reports, scoped to the assets the caller may see. */
@RestController
@RequestMapping("/api/reports/forecast")
public class ForecastReportController {

    private final ForecastReportService reportService;
    private final TabularExportService exportService;
    private final SecurityScopeService scopeService;

    public ForecastReportController(ForecastReportService reportService, TabularExportService exportService,
                                    SecurityScopeService scopeService) {
        this.reportService = reportService;
        this.exportService = exportService;
        this.scopeService = scopeService;
    }

    @GetMapping("/{type}")
    public ResponseEntity<Map<String, Object>> report(@PathVariable String type,
                                                      @RequestParam(required = false) String category,
                                                      @RequestParam(required = false) String department,
                                                      @RequestParam(required = false) Integer threshold,
                                                      @RequestParam(required = false) Double rate) {
        Map<String, Object> body = new HashMap<>();
        try {
            TabularReport report = build(type, category, department, threshold, rate);
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
            body.put("message", "Error generating forecast: " + e.getMessage());
            return ResponseEntity.badRequest().body(body);
        }
    }

    @GetMapping("/{type}/export")
    public ResponseEntity<byte[]> export(@PathVariable String type,
                                         @RequestParam(defaultValue = "excel") String format,
                                         @RequestParam(required = false) String category,
                                         @RequestParam(required = false) String department,
                                         @RequestParam(required = false) Integer threshold,
                                         @RequestParam(required = false) Double rate) {
        TabularReport report;
        try {
            report = build(type, category, department, threshold, rate);
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

    private TabularReport build(String type, String category, String department, Integer threshold, Double rate) {
        SecurityScopeService.ReportScope scope = scopeService.getScope();
        return switch (type) {
            case "depreciation-12m" -> reportService.depreciationForecast(scope, category, department, 12);
            case "depreciation-3y" -> reportService.depreciationForecast(scope, category, department, 36);
            case "depreciation-5y" -> reportService.depreciationForecast(scope, category, department, 60);
            case "capex" -> reportService.capexForecast(scope, category, department, threshold, rate);
            case "end-of-life" -> reportService.endOfLife(scope, category, department, threshold);
            case "future-nbv" -> reportService.futureNbv(scope, category, department, threshold);
            case "maintenance" -> reportService.maintenanceForecast(scope, category, department);
            case "replacement-exposure" -> reportService.replacementExposure(scope, category, department, threshold, rate);
            case "age-distribution" -> reportService.ageDistribution(scope, category, department);
            case "cost-benefit" -> reportService.costBenefit(scope, category, department);
            default -> null;
        };
    }
}
