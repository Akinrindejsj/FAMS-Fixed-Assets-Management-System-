package com.example.fams.reports;

import com.example.fams.core.config.SecurityScopeService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService reportService;
    private final SecurityScopeService scopeService;

    public ReportController(ReportService reportService, SecurityScopeService scopeService) {
        this.reportService = reportService;
        this.scopeService = scopeService;
    }

    @GetMapping("/asset-register")
    public ResponseEntity<Map<String, Object>> assetRegisterReport(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String status) {

        try {
            ReportData report = reportService.generateAssetRegisterReport(scopeService.getScope(), category, department, status);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", report);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error generating asset register report: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping("/capitalisation")
    public ResponseEntity<Map<String, Object>> capitalisationReport(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate startDate,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate endDate) {
        try {
            ReportData report = reportService.generateCapitalisationReport(scopeService.getScope(), startDate, endDate, category, department);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", report);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error generating capitalisation report: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping("/awaiting-capitalisation")
    public ResponseEntity<Map<String, Object>> awaitingCapitalisationReport(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String department) {
        try {
            ReportData report = reportService.generateAwaitingCapitalisationReport(scopeService.getScope(), category, department);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", report);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error generating assets awaiting capitalisation report: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping("/asset-acquisition")
    public ResponseEntity<Map<String, Object>> assetAcquisitionReport(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate startDate,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate endDate) {
        try {
            ReportData report = reportService.generateAssetAcquisitionReport(scopeService.getScope(), startDate, endDate, category, department);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", report);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error generating asset acquisition report: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping("/asset-additions")
    public ResponseEntity<Map<String, Object>> assetAdditionsReport(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate startDate,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate endDate) {
        try {
            ReportData report = reportService.generateAssetAdditionsReport(scopeService.getScope(), startDate, endDate, category, department);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", report);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error generating asset additions report: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping("/acquisition-cost-analysis")
    public ResponseEntity<Map<String, Object>> acquisitionCostAnalysis(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate startDate,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate endDate) {
        try {
            AcquisitionCostAnalysisData report = reportService.generateAcquisitionCostAnalysis(scopeService.getScope(), startDate, endDate, category, department);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", report);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error generating acquisition cost analysis: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping("/acquisition-trend")
    public ResponseEntity<Map<String, Object>> acquisitionTrend(
            @RequestParam(defaultValue = "MONTH") TrendGranularity granularity,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate startDate,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate endDate) {
        try {
            AcquisitionTrendData report = reportService.generateAcquisitionTrendReport(scopeService.getScope(), granularity, startDate, endDate, category, department);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", report);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error generating acquisition trend report: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping("/asset-register/fully-depreciated")
    public ResponseEntity<Map<String, Object>> fullyDepreciatedAssetRegisterReport(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String department) {

        try {
            ReportData report = reportService.generateFullyDepreciatedAssetRegisterReport(scopeService.getScope(), category, department);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", report);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error generating fully depreciated asset register: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping("/asset-register/non-depreciable")
    public ResponseEntity<Map<String, Object>> nonDepreciableAssetRegisterReport(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String department) {

        try {
            ReportData report = reportService.generateNonDepreciableAssetRegisterReport(scopeService.getScope(), category, department);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", report);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error generating non-depreciable asset register: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Asset register grouped by a dimension (category, location, department, cost
     * centre, custodian, status, acquisition month or supplier — see {@link AssetGroupBy}).
     * e.g. GET /api/reports/asset-register/grouped?groupBy=CATEGORY
     */
    @GetMapping("/asset-register/grouped")
    public ResponseEntity<Map<String, Object>> groupedAssetRegisterReport(
            @RequestParam AssetGroupBy groupBy,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate startDate,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate endDate) {

        try {
            GroupedReportData report = reportService.generateGroupedAssetRegisterReport(
                    scopeService.getScope(), groupBy, category, department, status, startDate, endDate);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", report);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error generating grouped asset register report: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping("/capitalisation-adjustments")
    public ResponseEntity<Map<String, Object>> capitalisationAdjustmentReport(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate startDate,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate endDate) {
        try {
            CapitalAdjustmentReportData report = reportService.generateCapitalisationAdjustmentReport(scopeService.getScope(), startDate, endDate, category, department);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", report);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error generating capitalisation adjustment report: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping("/asset-components")
    public ResponseEntity<Map<String, Object>> assetComponentRegisterReport(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String department) {
        try {
            ComponentReportData report = reportService.generateAssetComponentRegisterReport(scopeService.getScope(), category, department);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", report);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error generating asset component register: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping("/asset-register/detailed")
    public ResponseEntity<Map<String, Object>> detailedAssetRegisterReport(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) String status) {

        try {
            DetailedReportData report = reportService.generateDetailedAssetRegisterReport(scopeService.getScope(), category, department, status);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", report);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error generating detailed asset register report: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping("/asset-register/summary")
    public ResponseEntity<Map<String, Object>> assetSummaryReport(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String department) {

        try {
            AssetSummaryData report = reportService.generateAssetSummaryReport(scopeService.getScope(), category, department);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", report);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error generating asset summary report: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping("/inventory-summary")
    public ResponseEntity<Map<String, Object>> inventorySummaryReport(
            @RequestParam(required = false) String department) {

        try {
            ReportData report = reportService.generateInventorySummaryReport(scopeService.getScope(), department);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", report);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error generating inventory summary: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping("/location-audit")
    public ResponseEntity<Map<String, Object>> locationAuditReport(
            @RequestParam(required = false) String department) {

        try {
            ReportData report = reportService.generateLocationAuditReport(scopeService.getScope(), department);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", report);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error generating location audit report: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping("/valuation")
    public ResponseEntity<Map<String, Object>> valuationReport(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String department) {

        try {
            ReportData report = reportService.generateValuationReport(scopeService.getScope(), category, department);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", report);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error generating valuation report: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping("/depreciation-schedule")
    public ResponseEntity<Map<String, Object>> depreciationScheduleReport(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String department) {

        try {
            DepreciationScheduleData report = reportService.generateDepreciationScheduleReport(scopeService.getScope(), category, department);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", report);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error generating depreciation schedule: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Serves the disposal register, detail, by-period/category, sales, scrapped, proceeds,
     * gain/loss and retirement reports from one endpoint. {@code methods} (comma-separated)
     * restricts to those disposalMethod values. The system has no distinct "retirement"
     * concept, so the Asset Retirement view maps to methods=Write-off,Scrap (assets removed
     * from service without a sale) — an interpretation to confirm with the business.
     */
    @GetMapping("/asset-disposals")
    public ResponseEntity<Map<String, Object>> disposalReport(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) List<String> methods,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate startDate,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate endDate) {
        try {
            DisposalReportData report = reportService.generateDisposalReport(scopeService.getScope(), startDate, endDate, category, department, methods);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", report);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error generating disposal report: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping("/pending-disposal")
    public ResponseEntity<Map<String, Object>> pendingDisposalReport(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String department) {
        try {
            DisposalReportData report = reportService.generatePendingDisposalReport(scopeService.getScope(), category, department);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", report);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error generating pending disposal report: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping("/disposal-approval-history")
    public ResponseEntity<Map<String, Object>> disposalApprovalHistoryReport(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String department) {
        try {
            ApprovalHistoryReportData report = reportService.generateDisposalApprovalHistoryReport(scopeService.getScope(), category, department);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", report);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error generating disposal approval history: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping("/asset-transfers")
    public ResponseEntity<Map<String, Object>> assetTransferReport(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate startDate,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate endDate) {
        try {
            TransferReportData report = reportService.generateAssetTransferReport(scopeService.getScope(), startDate, endDate, category, department);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", report);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error generating asset transfer report: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Transfers received into a unit. "unit" filters against
     * toDepartment/toBranch/toLocation (see ReportService javadoc).
     */
    @GetMapping("/transfer-in")
    public ResponseEntity<Map<String, Object>> transferInRegister(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String unit,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate startDate,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate endDate) {
        try {
            TransferReportData report = reportService.generateTransferInRegister(scopeService.getScope(), startDate, endDate, category, unit);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", report);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error generating transfer-in register: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping("/transfer-out")
    public ResponseEntity<Map<String, Object>> transferOutRegister(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String unit,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate startDate,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate endDate) {
        try {
            TransferReportData report = reportService.generateTransferOutRegister(scopeService.getScope(), startDate, endDate, category, unit);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", report);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error generating transfer-out register: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping("/inter-branch-transfers")
    public ResponseEntity<Map<String, Object>> interBranchTransferReport(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate startDate,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate endDate) {
        try {
            TransferReportData report = reportService.generateInterBranchTransferReport(scopeService.getScope(), startDate, endDate, category, department);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", report);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error generating inter-branch transfer report: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping("/inter-department-transfers")
    public ResponseEntity<Map<String, Object>> interDepartmentTransferReport(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate startDate,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate endDate) {
        try {
            TransferReportData report = reportService.generateInterDepartmentTransferReport(scopeService.getScope(), startDate, endDate, category);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", report);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error generating inter-department transfer report: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping("/asset-assignments")
    public ResponseEntity<Map<String, Object>> assetAssignmentReport(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String department) {
        try {
            AssignmentReportData report = reportService.generateAssetAssignmentReport(scopeService.getScope(), category, department);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", report);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error generating asset assignment report: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping("/unassigned-assets")
    public ResponseEntity<Map<String, Object>> unassignedAssetsReport(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String department) {
        try {
            AssignmentReportData report = reportService.generateUnassignedAssetsReport(scopeService.getScope(), category, department);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", report);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error generating unassigned assets report: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping("/assignment-history")
    public ResponseEntity<Map<String, Object>> assignmentHistoryReport(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate startDate,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate endDate) {
        try {
            TransferReportData report = reportService.generateAssignmentHistoryReport(scopeService.getScope(), startDate, endDate, category, department);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", report);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error generating assignment history report: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping("/location-history")
    public ResponseEntity<Map<String, Object>> locationHistoryReport(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate startDate,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate endDate) {
        try {
            TransferReportData report = reportService.generateLocationHistoryReport(scopeService.getScope(), startDate, endDate, category, department);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", report);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error generating location history report: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping("/pending-transfer-approval")
    public ResponseEntity<Map<String, Object>> pendingTransferApprovalReport(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String department) {
        try {
            TransferReportData report = reportService.generatePendingTransferApprovalReport(scopeService.getScope(), category, department);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", report);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error generating pending transfer approval report: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping("/movement-history")
    public ResponseEntity<Map<String, Object>> movementHistoryReport(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate startDate,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate endDate) {
        try {
            MovementHistoryReportData report = reportService.generateAssetMovementHistoryReport(scopeService.getScope(), startDate, endDate, category, department);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", report);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error generating movement history report: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping("/net-book-value")
    public ResponseEntity<Map<String, Object>> netBookValueReport(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String department) {
        try {
            NbvReportData report = reportService.generateNetBookValueReport(scopeService.getScope(), category, department);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", report);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error generating net book value report: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping("/asset-ageing")
    public ResponseEntity<Map<String, Object>> assetAgeingReport(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String department) {
        try {
            AgeingReportData report = reportService.generateAssetAgeingReport(scopeService.getScope(), category, department);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", report);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error generating asset ageing report: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Revaluation report, history and gain/loss — one endpoint, since all three read the same data.
     */
    @GetMapping("/revaluations")
    public ResponseEntity<Map<String, Object>> revaluationReport(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate startDate,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate endDate) {
        try {
            RevaluationReportData report = reportService.generateRevaluationReport(scopeService.getScope(), startDate, endDate, category, department);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", report);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error generating revaluation report: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    /**
     * Impairment report and history — one endpoint.
     */
    @GetMapping("/impairments")
    public ResponseEntity<Map<String, Object>> impairmentReport(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate startDate,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate endDate) {
        try {
            ImpairmentReportData report = reportService.generateImpairmentReport(scopeService.getScope(), startDate, endDate, category, department);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", report);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error generating impairment report: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping("/depreciation-runs")
    public ResponseEntity<Map<String, Object>> depreciationRunReport() {
        try {
            List<com.example.fams.depreciation.DepreciationRun> report = reportService.generateDepreciationRunReport();
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", report);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error generating depreciation run report: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping("/accumulated-depreciation")
    public ResponseEntity<Map<String, Object>> accumulatedDepreciationReport(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String department) {
        try {
            ReportData report = reportService.generateAccumulatedDepreciationReport(scopeService.getScope(), category, department);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", report);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error generating accumulated depreciation report: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping("/depreciation-monthly")
    public ResponseEntity<Map<String, Object>> monthlyDepreciationReport(@RequestParam String period) {
        try {
            com.example.fams.depreciation.DepreciationReport report = reportService.generateMonthlyDepreciationReport(scopeService.getScope(), period);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", report);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error generating monthly depreciation report: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping("/depreciation-expense")
    public ResponseEntity<Map<String, Object>> depreciationExpenseReport(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate startDate,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate endDate) {
        try {
            AcquisitionCostAnalysisData report = reportService.generateDepreciationExpenseReport(scopeService.getScope(), startDate, endDate, category, department);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", report);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error generating depreciation expense report: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping("/depreciation-by-category")
    public ResponseEntity<Map<String, Object>> depreciationByCategoryReport(@RequestParam String period) {
        try {
            List<com.example.fams.depreciation.DepreciationCategoryReport> report = reportService.generateDepreciationByCategoryPeriodReport(scopeService.getScope(), period);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", report);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error generating depreciation by category report: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping("/depreciation-by-department")
    public ResponseEntity<Map<String, Object>> depreciationByDepartmentReport(@RequestParam String period) {
        try {
            List<com.example.fams.depreciation.DepreciationDepartmentReport> report = reportService.generateDepreciationByDepartmentPeriodReport(scopeService.getScope(), period);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", report);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error generating depreciation by department report: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping("/depreciation-by-cost-centre")
    public ResponseEntity<Map<String, Object>> depreciationByCostCentreReport(@RequestParam String period) {
        try {
            List<Map<String, Object>> report = reportService.generateDepreciationByCostCentrePeriodReport(scopeService.getScope(), period);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", report);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error generating depreciation by cost centre report: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping("/depreciation-by-method")
    public ResponseEntity<Map<String, Object>> depreciationByMethodReport(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String department) {
        try {
            GroupedReportData report = reportService.generateDepreciationByMethodReport(scopeService.getScope(), category, department);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", report);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error generating depreciation by method report: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping("/depreciation-exceptions")
    public ResponseEntity<Map<String, Object>> depreciationExceptionReport(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String department) {
        try {
            ExceptionReportData report = reportService.generateDepreciationExceptionReport(scopeService.getScope(), category, department);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", report);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error generating depreciation exception report: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping("/depreciation-adjustments")
    public ResponseEntity<Map<String, Object>> depreciationAdjustmentReport(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate startDate,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate endDate) {
        try {
            DepreciationAdjustmentReportData report = reportService.generateDepreciationAdjustmentReport(scopeService.getScope(), startDate, endDate, category, department);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", report);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error generating depreciation adjustment report: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping("/depreciation-history")
    public ResponseEntity<Map<String, Object>> depreciationHistoryReport(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String department,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate startDate,
            @RequestParam(required = false) @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate endDate) {
        try {
            HistoryReportData report = reportService.generateDepreciationHistoryReport(scopeService.getScope(), startDate, endDate, category, department);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", report);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error generating depreciation history report: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping("/depreciation-projection")
    public ResponseEntity<Map<String, Object>> depreciationProjectionReport(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String department,
            @RequestParam(defaultValue = "5") int horizonYears) {
        try {
            ProjectionReportData report = reportService.generateDepreciationProjectionReport(scopeService.getScope(), horizonYears, category, department);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", report);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error generating depreciation projection report: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping("/approaching-full-depreciation")
    public ResponseEntity<Map<String, Object>> approachingFullDepreciationReport(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String department,
            @RequestParam(defaultValue = "1") int thresholdYears) {
        try {
            ReportData report = reportService.generateApproachingFullDepreciationReport(scopeService.getScope(), thresholdYears, category, department);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", report);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error generating assets approaching full depreciation report: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping("/purchase-analysis")
    public ResponseEntity<Map<String, Object>> purchaseAnalysisReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        try {
            ReportData report = reportService.generatePurchaseAnalysisReport(scopeService.getScope(), startDate, endDate);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", report);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error generating purchase analysis: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping("/maintenance-history")
    public ResponseEntity<Map<String, Object>> maintenanceHistoryReport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {

        try {
            ReportData report = reportService.generateMaintenanceHistoryReport(scopeService.getScope(), startDate, endDate);
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", report);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error generating maintenance history: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping("/warranty-expiry")
    public ResponseEntity<Map<String, Object>> warrantyExpiryReport() {

        try {
            ReportData report = reportService.generateWarrantyExpiryReport(scopeService.getScope());
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", report);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error generating warranty expiry report: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping("/missing-assets")
    public ResponseEntity<Map<String, Object>> missingAssetsReport() {

        try {
            ReportData report = reportService.generateMissingAssetsReport(scopeService.getScope());
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", report);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error generating missing assets report: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }

    @GetMapping("/disposal-logs")
    public ResponseEntity<Map<String, Object>> disposalLogsReport() {

        try {
            ReportData report = reportService.generateDisposalLogsReport(scopeService.getScope());
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("data", report);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> response = new HashMap<>();
            response.put("success", false);
            response.put("message", "Error generating disposal logs: " + e.getMessage());
            return ResponseEntity.badRequest().body(response);
        }
    }
}