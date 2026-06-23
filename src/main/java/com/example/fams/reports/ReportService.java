package com.example.fams.reports;

import com.example.fams.assets.Asset;
import com.example.fams.assets.AssetRepository;
import com.example.fams.depreciation.DepreciationService;
import com.example.fams.maintenance.MaintenanceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class ReportService {

    private final AssetRepository assetRepository;
    private final DepreciationService depreciationService;
    private final MaintenanceService maintenanceService;

    public ReportService(AssetRepository assetRepository,
                         DepreciationService depreciationService,
                         MaintenanceService maintenanceService) {
        this.assetRepository = assetRepository;
        this.depreciationService = depreciationService;
        this.maintenanceService = maintenanceService;
    }

    /**
     * Generate Asset Register Report - complete list of all assets
     */
    @Transactional(readOnly = true)
    public ReportData generateAssetRegisterReport(String category, String department, String status) {
        ReportData report = new ReportData();
        report.setReportType("assetRegister");
        report.setReportName("Asset Register");
        report.setGeneratedDate(LocalDate.now());
        report.setCategory(category != null && !category.isEmpty() && !category.equals("All Categories") ? category : null);
        report.setDepartment(department != null && !department.isEmpty() && !department.equals("Global Operations") ? department : null);
        report.setStatus(status != null && !status.isEmpty() ? status : null);

        List<Asset> assets = assetRepository.findAllByOrderByCreatedAtDesc();

        // Apply filters
        if (category != null && !category.isEmpty() && !category.equals("All Categories")) {
            assets = assets.stream()
                    .filter(a -> a.getCategory().equalsIgnoreCase(category))
                    .collect(Collectors.toList());
        }

        if (department != null && !department.isEmpty() && !department.equals("Global Operations")) {
            assets = assets.stream()
                    .filter(a -> a.getDepartment().equalsIgnoreCase(department))
                    .collect(Collectors.toList());
        }

        List<ReportRow> rows = assets.stream()
                .map(this::assetToReportRow)
                .collect(Collectors.toList());

        report.setRows(rows);
        calculateTotals(report, rows);

        return report;
    }

    /**
     * Generate Inventory Summary Report - summary by category
     */
    @Transactional(readOnly = true)
    public ReportData generateInventorySummaryReport(String department) {
        ReportData report = new ReportData();
        report.setReportType("inventorySummary");
        report.setReportName("Inventory Summary");
        report.setGeneratedDate(LocalDate.now());
        report.setDepartment(department);

        List<Asset> assets = assetRepository.findAllByOrderByCreatedAtDesc();

        if (department != null && !department.isEmpty() && !department.equals("Global Operations")) {
            assets = assets.stream()
                    .filter(a -> a.getDepartment().equalsIgnoreCase(department))
                    .collect(Collectors.toList());
        }

        List<ReportRow> rows = assets.stream()
                .map(this::assetToReportRow)
                .collect(Collectors.toList());

        report.setRows(rows);
        calculateTotals(report, rows);

        return report;
    }

    /**
     * Generate Location Audit Report - assets by location/department
     */
    @Transactional(readOnly = true)
    public ReportData generateLocationAuditReport(String department) {
        ReportData report = new ReportData();
        report.setReportType("locationAudit");
        report.setReportName("Location Audit");
        report.setGeneratedDate(LocalDate.now());
        report.setDepartment(department);

        List<Asset> assets = assetRepository.findAllByOrderByCreatedAtDesc();

        if (department != null && !department.isEmpty() && !department.equals("Global Operations")) {
            assets = assets.stream()
                    .filter(a -> a.getDepartment().equalsIgnoreCase(department))
                    .collect(Collectors.toList());
        }

        List<ReportRow> rows = assets.stream()
                .map(this::assetToReportRow)
                .collect(Collectors.toList());

        report.setRows(rows);
        calculateTotals(report, rows);

        return report;
    }

    /**
     * Generate Valuation Report - asset values and depreciation
     */
    @Transactional(readOnly = true)
    public ReportData generateValuationReport(String category, String department) {
        ReportData report = new ReportData();
        report.setReportType("valuationReport");
        report.setReportName("Asset Valuation Report");
        report.setGeneratedDate(LocalDate.now());
        report.setCategory(category);
        report.setDepartment(department);

        List<Asset> assets = assetRepository.findAllByOrderByCreatedAtDesc();

        // Apply filters
        if (category != null && !category.isEmpty() && !category.equals("All Categories")) {
            assets = assets.stream()
                    .filter(a -> a.getCategory().equalsIgnoreCase(category))
                    .collect(Collectors.toList());
        }

        if (department != null && !department.isEmpty() && !department.equals("Global Operations")) {
            assets = assets.stream()
                    .filter(a -> a.getDepartment().equalsIgnoreCase(department))
                    .collect(Collectors.toList());
        }

        List<ReportRow> rows = assets.stream()
                .map(this::assetToReportRow)
                .collect(Collectors.toList());

        report.setRows(rows);
        calculateTotals(report, rows);

        return report;
    }

    /**
     * Generate Depreciation Schedule Report
     */
    @Transactional(readOnly = true)
    public ReportData generateDepreciationScheduleReport(String category, String department) {
        ReportData report = new ReportData();
        report.setReportType("depreciationSchedule");
        report.setReportName("Depreciation Schedule");
        report.setGeneratedDate(LocalDate.now());
        report.setCategory(category);
        report.setDepartment(department);

        List<Asset> assets = assetRepository.findAllByOrderByCreatedAtDesc();

        // Apply filters
        if (category != null && !category.isEmpty() && !category.equals("All Categories")) {
            assets = assets.stream()
                    .filter(a -> a.getCategory().equalsIgnoreCase(category))
                    .collect(Collectors.toList());
        }

        if (department != null && !department.isEmpty() && !department.equals("Global Operations")) {
            assets = assets.stream()
                    .filter(a -> a.getDepartment().equalsIgnoreCase(department))
                    .collect(Collectors.toList());
        }

        List<ReportRow> rows = assets.stream()
                .map(this::assetToReportRow)
                .collect(Collectors.toList());

        report.setRows(rows);
        calculateTotals(report, rows);

        return report;
    }

    /**
     * Generate Purchase Analysis Report
     */
    @Transactional(readOnly = true)
    public ReportData generatePurchaseAnalysisReport(LocalDate startDate, LocalDate endDate) {
        ReportData report = new ReportData();
        report.setReportType("purchaseAnalysis");
        report.setReportName("Purchase Analysis");
        report.setGeneratedDate(LocalDate.now());
        report.setStartDate(startDate);
        report.setEndDate(endDate);

        List<Asset> assets = assetRepository.findAllByOrderByCreatedAtDesc();

        if (startDate != null && endDate != null) {
            assets = assets.stream()
                    .filter(a -> a.getPurchaseDate() != null &&
                            !a.getPurchaseDate().isBefore(startDate) &&
                            !a.getPurchaseDate().isAfter(endDate))
                    .collect(Collectors.toList());
        }

        List<ReportRow> rows = assets.stream()
                .map(this::assetToReportRow)
                .collect(Collectors.toList());

        report.setRows(rows);
        calculateTotals(report, rows);

        return report;
    }

    /**
     * Generate Maintenance History Report
     */
    @Transactional(readOnly = true)
    public ReportData generateMaintenanceHistoryReport(LocalDate startDate, LocalDate endDate) {
        ReportData report = new ReportData();
        report.setReportType("maintenanceHistory");
        report.setReportName("Service History");
        report.setGeneratedDate(LocalDate.now());
        report.setStartDate(startDate != null ? startDate : LocalDate.now().minusMonths(1));
        report.setEndDate(endDate != null ? endDate : LocalDate.now());

        List<Asset> assets = assetRepository.findAllByOrderByCreatedAtDesc();
        List<ReportRow> rows = assets.stream()
                .map(this::assetToReportRow)
                .collect(Collectors.toList());

        report.setRows(rows);
        calculateTotals(report, rows);

        return report;
    }

    /**
     * Generate Warranty Expiry Report
     */
    @Transactional(readOnly = true)
    public ReportData generateWarrantyExpiryReport() {
        ReportData report = new ReportData();
        report.setReportType("warrantyExpiry");
        report.setReportName("Warranty Expiry");
        report.setGeneratedDate(LocalDate.now());

        List<Asset> assets = assetRepository.findAllByOrderByCreatedAtDesc();
        // Filter assets with warranty information
        assets = assets.stream()
                .filter(a -> a.getWarrantyExpiry() != null)
                .collect(Collectors.toList());

        List<ReportRow> rows = assets.stream()
                .map(this::assetToReportRow)
                .collect(Collectors.toList());

        report.setRows(rows);
        calculateTotals(report, rows);

        return report;
    }

    /**
     * Generate Missing Assets Report - assets not in assigned status
     */
    @Transactional(readOnly = true)
    public ReportData generateMissingAssetsReport() {
        ReportData report = new ReportData();
        report.setReportType("missingAssets");
        report.setReportName("Missing Asset Report");
        report.setGeneratedDate(LocalDate.now());

        List<Asset> assets = assetRepository.findAllByOrderByCreatedAtDesc();
        // Filter assets that might be missing (not active/assigned)
        assets = assets.stream()
                .filter(a -> !"In Stock".equalsIgnoreCase(a.getStatus()) &&
                        !"Assigned".equalsIgnoreCase(a.getStatus()))
                .collect(Collectors.toList());

        List<ReportRow> rows = assets.stream()
                .map(this::assetToReportRow)
                .collect(Collectors.toList());

        report.setRows(rows);
        calculateTotals(report, rows);

        return report;
    }

    /**
     * Generate Disposal Logs Report
     */
    @Transactional(readOnly = true)
    public ReportData generateDisposalLogsReport() {
        ReportData report = new ReportData();
        report.setReportType("disposalLogs");
        report.setReportName("Disposal Logs");
        report.setGeneratedDate(LocalDate.now());

        List<Asset> assets = assetRepository.findAllByOrderByCreatedAtDesc();
        // Filter disposed assets
        assets = assets.stream()
                .filter(a -> "Disposed".equalsIgnoreCase(a.getStatus()) ||
                        "Retired".equalsIgnoreCase(a.getStatus()))
                .collect(Collectors.toList());

        List<ReportRow> rows = assets.stream()
                .map(this::assetToReportRow)
                .collect(Collectors.toList());

        report.setRows(rows);
        calculateTotals(report, rows);

        return report;
    }

    /**
     * Convert Asset entity to ReportRow
     */
    private ReportRow assetToReportRow(Asset asset) {
        return new ReportRow(
                asset.getId(),
                asset.getAssetCode(),
                asset.getName(),
                asset.getCategory(),
                asset.getDepartment(),
                asset.getCustodian(),
                asset.getPurchaseDate(),
                asset.getPurchaseCost(),
                BigDecimal.ZERO, // TODO: get from depreciation if available
                asset.getPurchaseCost() != null ? asset.getPurchaseCost() : BigDecimal.ZERO,
                asset.getStatus()
        );
    }

    /**
     * Calculate totals for a report
     */
    private void calculateTotals(ReportData report, List<ReportRow> rows) {
        if (rows == null || rows.isEmpty()) {
            report.setEmpty(true);
            report.setTotalRecords(0);
            report.setTotalValue(BigDecimal.ZERO);
            report.setTotalDepreciation(BigDecimal.ZERO);
            report.setTotalNetBookValue(BigDecimal.ZERO);
            return;
        }

        BigDecimal totalValue = rows.stream()
                .map(ReportRow::acquisitionCost)
                .filter(v -> v != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalDepreciation = rows.stream()
                .map(ReportRow::accumulatedDepreciation)
                .filter(v -> v != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalNetValue = rows.stream()
                .map(ReportRow::netBookValue)
                .filter(v -> v != null)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        report.setTotalValue(totalValue);
        report.setTotalDepreciation(totalDepreciation);
        report.setTotalNetBookValue(totalNetValue);
        report.setTotalRecords(rows.size());
        report.setEmpty(false);
    }
}

