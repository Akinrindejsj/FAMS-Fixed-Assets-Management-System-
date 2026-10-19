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

/** Lifecycle and data-quality exception reports, scoped to the assets the caller may see. */
@RestController
@RequestMapping("/api/reports/exceptions")
public class AssetExceptionReportController {

    private final AssetExceptionReportService reportService;
    private final TabularExportService exportService;
    private final SecurityScopeService scopeService;

    public AssetExceptionReportController(AssetExceptionReportService reportService, TabularExportService exportService,
                                          SecurityScopeService scopeService) {
        this.reportService = reportService;
        this.exportService = exportService;
        this.scopeService = scopeService;
    }

    @GetMapping("/{type}")
    public ResponseEntity<Map<String, Object>> report(@PathVariable String type,
                                                      @RequestParam(required = false) String category,
                                                      @RequestParam(required = false) String department,
                                                      @RequestParam(required = false) Integer threshold) {
        Map<String, Object> body = new HashMap<>();
        try {
            TabularReport report = build(type, category, department, threshold);
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
            body.put("message", "Error generating report: " + e.getMessage());
            return ResponseEntity.badRequest().body(body);
        }
    }

    @GetMapping("/{type}/export")
    public ResponseEntity<byte[]> export(@PathVariable String type,
                                         @RequestParam(defaultValue = "excel") String format,
                                         @RequestParam(required = false) String category,
                                         @RequestParam(required = false) String department,
                                         @RequestParam(required = false) Integer threshold) {
        TabularReport report;
        try {
            report = build(type, category, department, threshold);
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

    private TabularReport build(String type, String category, String department, Integer threshold) {
        SecurityScopeService.ReportScope scope = scopeService.getScope();
        return switch (type) {
            case "no-custodian" -> reportService.noCustodian(scope, category, department);
            case "no-location" -> reportService.noLocation(scope, category, department);
            case "no-serial" -> reportService.noSerial(scope, category, department);
            case "no-depreciation-policy" -> reportService.noDepreciationPolicy(scope, category, department);
            case "no-gl-mapping" -> reportService.noGlMapping(scope, category, department);
            case "missing-cost" -> reportService.missingCost(scope, category, department);
            case "invalid-useful-life" -> reportService.invalidUsefulLife(scope, category, department);
            case "beyond-useful-life" -> reportService.beyondUsefulLife(scope, category, department);
            case "fully-depreciated-active" -> reportService.fullyDepreciatedActive(scope, category, department);
            case "idle" -> reportService.idle(scope, category, department, threshold);
            case "dormant" -> reportService.dormant(scope, category, department, threshold);
            case "replacement" -> reportService.dueForReplacement(scope, category, department, threshold);
            case "excessive-maintenance" -> reportService.excessiveMaintenance(scope, category, department, threshold);
            case "not-verified" -> reportService.notVerified(scope, category, department, threshold);
            case "duplicates" -> reportService.duplicates(scope, category, department);
            case "incomplete-records" -> reportService.incompleteRecords(scope, category, department);
            case "data-quality" -> reportService.dataQuality(scope, category, department);
            default -> null;
        };
    }
}
