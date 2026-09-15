package com.example.fams.reports;

import com.example.fams.assets.Asset;
import com.example.fams.assets.AssetRepository;
import com.example.fams.core.config.SecurityScopeService.ReportScope;
import com.example.fams.depreciation.DepreciationPosting;
import com.example.fams.depreciation.DepreciationService;
import com.example.fams.maintenance.MaintenanceService;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Generates report data sets. All queries are:
 * <ul>
 *     <li><b>Role-scoped</b> — the caller's {@link ReportScope} restricts the underlying
 *         asset set at the database level (department heads → their departments,
 *         employees → their custody, admins/auditors/asset managers → global).</li>
 *     <li><b>Cached</b> — results are cached in the {@code reports} cache keyed by report
 *         type + scope + filters, so bursts of identical requests are served without
 *         hitting the database (high-concurrency support). TTL is the configured SLA window.</li>
 * </ul>
 */
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
     * Load the asset set the current caller is permitted to see, filtered at the database
     * level by their role scope.
     */
    private List<Asset> scopedAssets(ReportScope scope) {
        if (scope == null || scope.isGlobal()) {
            return assetRepository.findAllByOrderByCreatedAtDesc();
        }
        if (scope.isEmptyRestriction()) {
            // Department head with no department / employee with no identity → see nothing.
            return List.of();
        }
        if (scope.isDepartment()) {
            List<String> lowered = scope.departments().stream()
                    .map(String::toLowerCase)
                    .collect(Collectors.toList());
            return assetRepository.findByDepartmentInIgnoreCaseOrderByCreatedAtDesc(lowered);
        }
        // CUSTODIAN — custodians are already lower-cased by SecurityScopeService.
        return assetRepository.findByCustodianInIgnoreCaseOrderByCreatedAtDesc(scope.custodians());
    }

    private boolean hasValue(String value, String ignored) {
        return value != null && !value.isEmpty() && !value.equals(ignored);
    }

    /**
     * Generate Asset Register Report - complete list of all assets
     */
    @Cacheable(cacheNames = "reports",
            key = "'assetRegister|' + #scope.cacheKey() + '|' + #category + '|' + #department + '|' + #status")
    @Transactional(readOnly = true)
    public ReportData generateAssetRegisterReport(ReportScope scope, String category, String department, String status) {
        ReportData report = new ReportData();
        report.setReportType("assetRegister");
        report.setReportName("Asset Register");
        report.setGeneratedDate(LocalDate.now());
        report.setCategory(hasValue(category, "All Categories") ? category : null);
        report.setDepartment(hasValue(department, "Global Operations") ? department : null);
        report.setStatus(status != null && !status.isEmpty() ? status : null);

        List<Asset> assets = scopedAssets(scope);

        if (hasValue(category, "All Categories")) {
            assets = assets.stream()
                    .filter(a -> a.getCategory() != null && a.getCategory().equalsIgnoreCase(category))
                    .collect(Collectors.toList());
        }
        if (hasValue(department, "Global Operations")) {
            assets = assets.stream()
                    .filter(a -> a.getDepartment() != null && a.getDepartment().equalsIgnoreCase(department))
                    .collect(Collectors.toList());
        }
        if (status != null && !status.isEmpty()) {
            assets = assets.stream()
                    .filter(a -> a.getStatus() != null && a.getStatus().equalsIgnoreCase(status))
                    .collect(Collectors.toList());
        }

        return finish(report, assets);
    }

    /**
     * Detailed Fixed Asset Register: same scoping/filtering as the
     * plain register, but expanded to identification, acquisition, location and
     * lifecycle fields.
     */
    @Cacheable(cacheNames = "reports",
            key = "'assetRegisterDetailed|' + #scope.cacheKey() + '|' + #category + '|' + #department + '|' + #status")
    @Transactional(readOnly = true)
    public DetailedReportData generateDetailedAssetRegisterReport(ReportScope scope, String category, String department, String status) {
        DetailedReportData report = new DetailedReportData();
        report.setReportType("assetRegisterDetailed");
        report.setReportName("Detailed Fixed Asset Register");
        report.setGeneratedDate(LocalDate.now());
        report.setCategory(hasValue(category, "All Categories") ? category : null);
        report.setDepartment(hasValue(department, "Global Operations") ? department : null);
        report.setStatus(status != null && !status.isEmpty() ? status : null);

        List<Asset> assets = scopedAssets(scope);
        if (hasValue(category, "All Categories")) {
            assets = assets.stream()
                    .filter(a -> a.getCategory() != null && a.getCategory().equalsIgnoreCase(category))
                    .collect(Collectors.toList());
        }
        if (hasValue(department, "Global Operations")) {
            assets = assets.stream()
                    .filter(a -> a.getDepartment() != null && a.getDepartment().equalsIgnoreCase(department))
                    .collect(Collectors.toList());
        }
        if (status != null && !status.isEmpty()) {
            assets = assets.stream()
                    .filter(a -> a.getStatus() != null && a.getStatus().equalsIgnoreCase(status))
                    .collect(Collectors.toList());
        }

        List<Long> assetIds = assets.stream().map(Asset::getId).collect(Collectors.toList());
        Map<Long, DepreciationPosting> latestPostings = depreciationService.getLatestPostingsForAssets(assetIds);

        List<DetailedAssetRow> rows = assets.stream()
                .map(a -> assetToDetailedRow(a, latestPostings.get(a.getId())))
                .collect(Collectors.toList());
        report.setRows(rows);

        BigDecimal totalValue = rows.stream().map(DetailedAssetRow::acquisitionCost)
                .filter(v -> v != null).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalDep = rows.stream().map(DetailedAssetRow::accumulatedDepreciation)
                .filter(v -> v != null).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalNbv = rows.stream().map(DetailedAssetRow::netBookValue)
                .filter(v -> v != null).reduce(BigDecimal.ZERO, BigDecimal::add);
        report.setTotalValue(totalValue);
        report.setTotalDepreciation(totalDep);
        report.setTotalNetBookValue(totalNbv);

        return report;
    }

    private DetailedAssetRow assetToDetailedRow(Asset asset, DepreciationPosting latestPosting) {
        BigDecimal cost = asset.getPurchaseCost() != null ? asset.getPurchaseCost() : BigDecimal.ZERO;
        BigDecimal accumulatedDepreciation = latestPosting != null && latestPosting.getClosingAccumulatedDepreciation() != null
                ? latestPosting.getClosingAccumulatedDepreciation()
                : BigDecimal.ZERO;
        BigDecimal netBookValue = latestPosting != null && latestPosting.getBookValue() != null
                ? latestPosting.getBookValue()
                : cost;

        return new DetailedAssetRow(
                asset.getId(),
                asset.getAssetCode(),
                asset.getName(),
                asset.getCategory(),
                asset.getDescription(),
                asset.getSerialNumber(),
                asset.getManufacturer(),
                asset.getModel(),
                asset.getPurchaseDate(),
                cost,
                asset.getVendor(),
                asset.getBranch(),
                asset.getDepartment(),
                asset.getCostCentre(),
                asset.getCustodian(),
                accumulatedDepreciation,
                netBookValue,
                asset.getWarrantyExpiry(),
                asset.getStatus(),
                asset.getCreatedAt(),
                asset.getUpdatedAt()
        );
    }

    /**
     * Fixed Asset Summary: population count, historical cost,
     * accumulated depreciation and NBV, both overall and broken down by category.
     */
    @Cacheable(cacheNames = "reports",
            key = "'assetSummary|' + #scope.cacheKey() + '|' + #category + '|' + #department")
    @Transactional(readOnly = true)
    public AssetSummaryData generateAssetSummaryReport(ReportScope scope, String category, String department) {
        List<Asset> assets = scopedAssets(scope);
        if (hasValue(category, "All Categories")) {
            assets = assets.stream()
                    .filter(a -> a.getCategory() != null && a.getCategory().equalsIgnoreCase(category))
                    .collect(Collectors.toList());
        }
        if (hasValue(department, "Global Operations")) {
            assets = assets.stream()
                    .filter(a -> a.getDepartment() != null && a.getDepartment().equalsIgnoreCase(department))
                    .collect(Collectors.toList());
        }

        List<Long> assetIds = assets.stream().map(Asset::getId).collect(Collectors.toList());
        Map<Long, DepreciationPosting> latestPostings = depreciationService.getLatestPostingsForAssets(assetIds);

        AssetSummaryData summary = new AssetSummaryData();
        summary.setGeneratedDate(LocalDate.now());
        summary.setTotalAssets(assets.size());

        Map<String, List<Asset>> byCategory = assets.stream()
                .collect(Collectors.groupingBy(a -> a.getCategory() == null ? "Uncategorized" : a.getCategory()));

        List<AssetSummaryData.CategoryBreakdown> breakdown = byCategory.entrySet().stream()
                .map(e -> {
                    List<Asset> catAssets = e.getValue();
                    BigDecimal cost = catAssets.stream()
                            .map(a -> a.getPurchaseCost() == null ? BigDecimal.ZERO : a.getPurchaseCost())
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    BigDecimal dep = catAssets.stream()
                            .map(a -> {
                                DepreciationPosting p = latestPostings.get(a.getId());
                                return p != null && p.getClosingAccumulatedDepreciation() != null
                                        ? p.getClosingAccumulatedDepreciation() : BigDecimal.ZERO;
                            })
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    BigDecimal nbv = catAssets.stream()
                            .map(a -> {
                                DepreciationPosting p = latestPostings.get(a.getId());
                                BigDecimal assetCost = a.getPurchaseCost() == null ? BigDecimal.ZERO : a.getPurchaseCost();
                                return p != null && p.getBookValue() != null ? p.getBookValue() : assetCost;
                            })
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    return new AssetSummaryData.CategoryBreakdown(e.getKey(), catAssets.size(), cost, dep, nbv);
                })
                .sorted((a, b) -> a.category().compareToIgnoreCase(b.category()))
                .collect(Collectors.toList());

        summary.setByCategory(breakdown);
        summary.setTotalHistoricalCost(breakdown.stream().map(AssetSummaryData.CategoryBreakdown::historicalCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        summary.setTotalAccumulatedDepreciation(breakdown.stream().map(AssetSummaryData.CategoryBreakdown::accumulatedDepreciation)
                .reduce(BigDecimal.ZERO, BigDecimal::add));
        summary.setTotalNetBookValue(breakdown.stream().map(AssetSummaryData.CategoryBreakdown::netBookValue)
                .reduce(BigDecimal.ZERO, BigDecimal::add));

        return summary;
    }

    /**
     * "Asset Register by X".
     * One implementation parameterized by {@link AssetGroupBy} instead of six-plus near-
     * identical report methods; grouping key extraction lives in {@link #groupKeyFor}.
     */
    @Cacheable(cacheNames = "reports",
            key = "'assetRegisterGrouped|' + #scope.cacheKey() + '|' + #groupBy + '|' + #category + '|' + #department + '|' + #status + '|' + #fromDate + '|' + #toDate")
    @Transactional(readOnly = true)
    public GroupedReportData generateGroupedAssetRegisterReport(ReportScope scope, AssetGroupBy groupBy,
                                                                 String category, String department, String status,
                                                                 LocalDate fromDate, LocalDate toDate) {
        List<Asset> assets = scopedAssets(scope);
        if (hasValue(category, "All Categories")) {
            assets = assets.stream()
                    .filter(a -> a.getCategory() != null && a.getCategory().equalsIgnoreCase(category))
                    .collect(Collectors.toList());
        }
        if (hasValue(department, "Global Operations")) {
            assets = assets.stream()
                    .filter(a -> a.getDepartment() != null && a.getDepartment().equalsIgnoreCase(department))
                    .collect(Collectors.toList());
        }
        if (status != null && !status.isEmpty()) {
            assets = assets.stream()
                    .filter(a -> a.getStatus() != null && a.getStatus().equalsIgnoreCase(status))
                    .collect(Collectors.toList());
        }
        if (fromDate != null) {
            assets = assets.stream()
                    .filter(a -> a.getPurchaseDate() != null && !a.getPurchaseDate().isBefore(fromDate))
                    .collect(Collectors.toList());
        }
        if (toDate != null) {
            assets = assets.stream()
                    .filter(a -> a.getPurchaseDate() != null && !a.getPurchaseDate().isAfter(toDate))
                    .collect(Collectors.toList());
        }

        List<Long> assetIds = assets.stream().map(Asset::getId).collect(Collectors.toList());
        Map<Long, DepreciationPosting> latestPostings = depreciationService.getLatestPostingsForAssets(assetIds);

        Map<String, List<Asset>> buckets = assets.stream()
                .collect(Collectors.groupingBy(a -> groupKeyFor(a, groupBy), java.util.LinkedHashMap::new, Collectors.toList()));

        List<AssetGroup> groups = buckets.entrySet().stream()
                .map(e -> {
                    List<ReportRow> rows = e.getValue().stream()
                            .map(a -> assetToReportRow(a, latestPostings.get(a.getId())))
                            .collect(Collectors.toList());
                    BigDecimal cost = rows.stream().map(ReportRow::acquisitionCost)
                            .filter(v -> v != null).reduce(BigDecimal.ZERO, BigDecimal::add);
                    BigDecimal dep = rows.stream().map(ReportRow::accumulatedDepreciation)
                            .filter(v -> v != null).reduce(BigDecimal.ZERO, BigDecimal::add);
                    BigDecimal nbv = rows.stream().map(ReportRow::netBookValue)
                            .filter(v -> v != null).reduce(BigDecimal.ZERO, BigDecimal::add);
                    return new AssetGroup(e.getKey(), rows.size(), cost, dep, nbv, rows);
                })
                .sorted((a, b) -> a.groupKey().compareToIgnoreCase(b.groupKey()))
                .collect(Collectors.toList());

        GroupedReportData report = new GroupedReportData();
        report.setReportName("Asset Register by " + displayName(groupBy));
        report.setGroupBy(groupBy);
        report.setGeneratedDate(LocalDate.now());
        report.setGroups(groups);
        report.setTotalRecords(assets.size());
        report.setTotalValue(groups.stream().map(AssetGroup::totalCost).reduce(BigDecimal.ZERO, BigDecimal::add));
        report.setTotalDepreciation(groups.stream().map(AssetGroup::totalAccumulatedDepreciation).reduce(BigDecimal.ZERO, BigDecimal::add));
        report.setTotalNetBookValue(groups.stream().map(AssetGroup::totalNetBookValue).reduce(BigDecimal.ZERO, BigDecimal::add));

        return report;
    }

    private String groupKeyFor(Asset asset, AssetGroupBy groupBy) {
        return switch (groupBy) {
            case CATEGORY -> orUnspecified(asset.getCategory());
            case LOCATION -> orUnspecified(asset.getBranch());
            case DEPARTMENT -> orUnspecified(asset.getDepartment());
            case COST_CENTRE -> orUnspecified(asset.getCostCentre());
            case CUSTODIAN -> orUnspecified(asset.getCustodian());
            case STATUS -> orUnspecified(asset.getStatus());
            case SUPPLIER -> orUnspecified(asset.getVendor());
            case ACQUISITION_MONTH -> asset.getPurchaseDate() == null ? "Unspecified"
                    : asset.getPurchaseDate().getYear() + "-" + String.format("%02d", asset.getPurchaseDate().getMonthValue());
        };
    }

    private String orUnspecified(String value) {
        return (value == null || value.isBlank()) ? "Unspecified" : value;
    }

    private String displayName(AssetGroupBy groupBy) {
        return switch (groupBy) {
            case CATEGORY -> "Category";
            case LOCATION -> "Location";
            case DEPARTMENT -> "Department";
            case COST_CENTRE -> "Cost Centre";
            case CUSTODIAN -> "Custodian";
            case STATUS -> "Status";
            case SUPPLIER -> "Supplier";
            case ACQUISITION_MONTH -> "Acquisition Date";
        };
    }

    /**
     * Fully Depreciated Asset Register: assets whose latest
     * depreciation posting is flagged fullyDepreciated, but still registered/active
     * (status not Disposed/Retired). Assets never run through depreciation are excluded —
     * "fully depreciated" requires an actual posting saying so.
     */
    @Cacheable(cacheNames = "reports",
            key = "'fullyDepreciatedRegister|' + #scope.cacheKey() + '|' + #category + '|' + #department")
    @Transactional(readOnly = true)
    public ReportData generateFullyDepreciatedAssetRegisterReport(ReportScope scope, String category, String department) {
        ReportData report = new ReportData();
        report.setReportType("fullyDepreciatedRegister");
        report.setReportName("Fully Depreciated Asset Register");
        report.setGeneratedDate(LocalDate.now());
        report.setCategory(hasValue(category, "All Categories") ? category : null);
        report.setDepartment(hasValue(department, "Global Operations") ? department : null);

        List<Asset> assets = scopedAssets(scope);
        if (hasValue(category, "All Categories")) {
            assets = assets.stream()
                    .filter(a -> a.getCategory() != null && a.getCategory().equalsIgnoreCase(category))
                    .collect(Collectors.toList());
        }
        if (hasValue(department, "Global Operations")) {
            assets = assets.stream()
                    .filter(a -> a.getDepartment() != null && a.getDepartment().equalsIgnoreCase(department))
                    .collect(Collectors.toList());
        }
        // Exclude assets already disposed/retired — this report is for assets still
        // registered/operational despite being fully depreciated.
        assets = assets.stream()
                .filter(a -> !"Disposed".equalsIgnoreCase(a.getStatus()) && !"Retired".equalsIgnoreCase(a.getStatus()))
                .collect(Collectors.toList());

        List<Long> assetIds = assets.stream().map(Asset::getId).collect(Collectors.toList());
        Map<Long, DepreciationPosting> latestPostings = depreciationService.getLatestPostingsForAssets(assetIds);

        List<Asset> fullyDepreciated = assets.stream()
                .filter(a -> {
                    DepreciationPosting p = latestPostings.get(a.getId());
                    return p != null && Boolean.TRUE.equals(p.getFullyDepreciated());
                })
                .collect(Collectors.toList());

        return finish(report, fullyDepreciated);
    }

    /**
     * Non-Depreciable Asset Register: assets with no resolvable
     * depreciation policy (no asset-specific or category-wide {@link DepreciationParameters}
     * active today), mirroring {@link DepreciationService#resolveParameters}. Built as a
     * single bulk lookup rather than one resolveParameters() query per asset.
     *
     * <p><b>Caveat:</b> "no policy configured" is a proxy for "non-depreciable" (e.g. land).
     * It will also catch assets that are simply mis-configured / awaiting setup — those
     * are worth reviewing as a data-quality gap, not necessarily true non-depreciable
     * assets. Add an explicit isDepreciable flag on Asset if that ambiguity matters.
     */
    @Cacheable(cacheNames = "reports",
            key = "'nonDepreciableRegister|' + #scope.cacheKey() + '|' + #category + '|' + #department")
    @Transactional(readOnly = true)
    public ReportData generateNonDepreciableAssetRegisterReport(ReportScope scope, String category, String department) {
        ReportData report = new ReportData();
        report.setReportType("nonDepreciableRegister");
        report.setReportName("Non-Depreciable Asset Register");
        report.setGeneratedDate(LocalDate.now());
        report.setCategory(hasValue(category, "All Categories") ? category : null);
        report.setDepartment(hasValue(department, "Global Operations") ? department : null);

        List<Asset> assets = scopedAssets(scope);
        if (hasValue(category, "All Categories")) {
            assets = assets.stream()
                    .filter(a -> a.getCategory() != null && a.getCategory().equalsIgnoreCase(category))
                    .collect(Collectors.toList());
        }
        if (hasValue(department, "Global Operations")) {
            assets = assets.stream()
                    .filter(a -> a.getDepartment() != null && a.getDepartment().equalsIgnoreCase(department))
                    .collect(Collectors.toList());
        }

        List<com.example.fams.depreciation.DepreciationParameters> activeParams = depreciationService.getAllActiveParameters();
        java.util.Set<Long> assetIdsWithSpecificPolicy = activeParams.stream()
                .map(com.example.fams.depreciation.DepreciationParameters::getAssetId)
                .filter(java.util.Objects::nonNull)
                .collect(Collectors.toSet());
        java.util.Set<String> categoriesWithPolicy = activeParams.stream()
                .filter(p -> p.getAssetId() == null && p.getCategory() != null)
                .map(p -> p.getCategory().toLowerCase())
                .collect(Collectors.toSet());

        List<Asset> nonDepreciable = assets.stream()
                .filter(a -> !assetIdsWithSpecificPolicy.contains(a.getId())
                        && !(a.getCategory() != null && categoriesWithPolicy.contains(a.getCategory().toLowerCase())))
                .collect(Collectors.toList());

        return finish(report, nonDepreciable);
    }

    /**
     * Asset Acquisition Report: assets acquired (by purchaseDate)
     * within a reporting period.
     */
    @Cacheable(cacheNames = "reports",
            key = "'assetAcquisition|' + #scope.cacheKey() + '|' + #fromDate + '|' + #toDate + '|' + #category + '|' + #department")
    @Transactional(readOnly = true)
    public ReportData generateAssetAcquisitionReport(ReportScope scope, LocalDate fromDate, LocalDate toDate,
                                                       String category, String department) {
        ReportData report = new ReportData();
        report.setReportType("assetAcquisition");
        report.setReportName("Asset Acquisition Report");
        report.setGeneratedDate(LocalDate.now());
        report.setCategory(hasValue(category, "All Categories") ? category : null);
        report.setDepartment(hasValue(department, "Global Operations") ? department : null);

        List<Asset> assets = filterByAcquisitionPeriod(scopedAssets(scope), fromDate, toDate, category, department);
        return finish(report, assets);
    }

    /**
     * Asset Additions Report: assets newly added to the register
     * within a period.
     *
     * <p><b>Interpretation note:</b> the catalogue distinguishes this from 
     * ("Asset Acquisition Report") without defining how. Read literally — "new
     * additions to the register" — this filters on {@code createdAt} (when the record
     * was entered into FAMS), while item 1 filters on {@code purchaseDate} (when it was
     * actually bought). Those two dates coincide for assets entered promptly but diverge
     * for backlog/catch-up entries — worth confirming this is the distinction your team
     * actually wants before relying on it.
     */
    @Cacheable(cacheNames = "reports",
            key = "'assetAdditions|' + #scope.cacheKey() + '|' + #fromDate + '|' + #toDate + '|' + #category + '|' + #department")
    @Transactional(readOnly = true)
    public ReportData generateAssetAdditionsReport(ReportScope scope, LocalDate fromDate, LocalDate toDate,
                                                     String category, String department) {
        ReportData report = new ReportData();
        report.setReportType("assetAdditions");
        report.setReportName("Asset Additions Report");
        report.setGeneratedDate(LocalDate.now());
        report.setCategory(hasValue(category, "All Categories") ? category : null);
        report.setDepartment(hasValue(department, "Global Operations") ? department : null);

        List<Asset> assets = scopedAssets(scope);
        if (hasValue(category, "All Categories")) {
            assets = assets.stream()
                    .filter(a -> a.getCategory() != null && a.getCategory().equalsIgnoreCase(category))
                    .collect(Collectors.toList());
        }
        if (hasValue(department, "Global Operations")) {
            assets = assets.stream()
                    .filter(a -> a.getDepartment() != null && a.getDepartment().equalsIgnoreCase(department))
                    .collect(Collectors.toList());
        }
        if (fromDate != null) {
            assets = assets.stream()
                    .filter(a -> a.getCreatedAt() != null && !a.getCreatedAt().toLocalDate().isBefore(fromDate))
                    .collect(Collectors.toList());
        }
        if (toDate != null) {
            assets = assets.stream()
                    .filter(a -> a.getCreatedAt() != null && !a.getCreatedAt().toLocalDate().isAfter(toDate))
                    .collect(Collectors.toList());
        }

        return finish(report, assets);
    }

    /**
     * Acquisition Cost Analysis. See {@link AcquisitionCostAnalysisData}
     * javadoc for the scope caveat (total cost only, no cost-component breakdown).
     */
    @Cacheable(cacheNames = "reports",
            key = "'acquisitionCostAnalysis|' + #scope.cacheKey() + '|' + #fromDate + '|' + #toDate + '|' + #category + '|' + #department")
    @Transactional(readOnly = true)
    public AcquisitionCostAnalysisData generateAcquisitionCostAnalysis(ReportScope scope, LocalDate fromDate, LocalDate toDate,
                                                                        String category, String department) {
        List<Asset> assets = filterByAcquisitionPeriod(scopedAssets(scope), fromDate, toDate, category, department);

        AcquisitionCostAnalysisData data = new AcquisitionCostAnalysisData();
        data.setGeneratedDate(LocalDate.now());
        data.setFromDate(fromDate);
        data.setToDate(toDate);
        data.setTotalAssets(assets.size());

        List<BigDecimal> costs = assets.stream()
                .map(a -> a.getPurchaseCost() == null ? BigDecimal.ZERO : a.getPurchaseCost())
                .collect(Collectors.toList());
        BigDecimal total = costs.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        data.setTotalCost(total);
        data.setAverageCost(costs.isEmpty() ? BigDecimal.ZERO
                : total.divide(BigDecimal.valueOf(costs.size()), 2, java.math.RoundingMode.HALF_UP));
        data.setMinCost(costs.stream().min(BigDecimal::compareTo).orElse(BigDecimal.ZERO));
        data.setMaxCost(costs.stream().max(BigDecimal::compareTo).orElse(BigDecimal.ZERO));

        Map<String, List<Asset>> byCategory = assets.stream()
                .collect(Collectors.groupingBy(a -> a.getCategory() == null ? "Uncategorized" : a.getCategory()));
        List<AssetSummaryData.CategoryBreakdown> breakdown = byCategory.entrySet().stream()
                .map(e -> {
                    BigDecimal catCost = e.getValue().stream()
                            .map(a -> a.getPurchaseCost() == null ? BigDecimal.ZERO : a.getPurchaseCost())
                            .reduce(BigDecimal.ZERO, BigDecimal::add);
                    return new AssetSummaryData.CategoryBreakdown(e.getKey(), e.getValue().size(), catCost, BigDecimal.ZERO, BigDecimal.ZERO);
                })
                .sorted((a, b) -> a.category().compareToIgnoreCase(b.category()))
                .collect(Collectors.toList());
        data.setByCategory(breakdown);

        return data;
    }

    /**
     * Asset Acquisition Trend: acquisition count and cost over
     * time, at monthly/quarterly/annual granularity.
     */
    @Cacheable(cacheNames = "reports",
            key = "'acquisitionTrend|' + #scope.cacheKey() + '|' + #granularity + '|' + #fromDate + '|' + #toDate + '|' + #category + '|' + #department")
    @Transactional(readOnly = true)
    public AcquisitionTrendData generateAcquisitionTrendReport(ReportScope scope, TrendGranularity granularity,
                                                                 LocalDate fromDate, LocalDate toDate,
                                                                 String category, String department) {
        List<Asset> assets = filterByAcquisitionPeriod(scopedAssets(scope), fromDate, toDate, category, department)
                .stream()
                .filter(a -> a.getPurchaseDate() != null)
                .collect(Collectors.toList());

        Map<String, List<Asset>> byPeriod = assets.stream()
                .collect(Collectors.groupingBy(a -> trendPeriodKey(a.getPurchaseDate(), granularity)));

        List<TrendPoint> points = byPeriod.entrySet().stream()
                .map(e -> new TrendPoint(
                        e.getKey(),
                        e.getValue().size(),
                        e.getValue().stream().map(a -> a.getPurchaseCost() == null ? BigDecimal.ZERO : a.getPurchaseCost())
                                .reduce(BigDecimal.ZERO, BigDecimal::add)))
                .sorted((a, b) -> a.period().compareTo(b.period())) // "YYYY-MM"/"YYYY-QN"/"YYYY" all sort correctly as strings
                .collect(Collectors.toList());

        AcquisitionTrendData data = new AcquisitionTrendData();
        data.setGeneratedDate(LocalDate.now());
        data.setGranularity(granularity);
        data.setPoints(points);
        return data;
    }

    private String trendPeriodKey(LocalDate date, TrendGranularity granularity) {
        return switch (granularity) {
            case MONTH -> date.getYear() + "-" + String.format("%02d", date.getMonthValue());
            case QUARTER -> date.getYear() + "-Q" + (((date.getMonthValue() - 1) / 3) + 1);
            case YEAR -> String.valueOf(date.getYear());
        };
    }

    /** Shared acquisition-period + category/department filter */
    private List<Asset> filterByAcquisitionPeriod(List<Asset> assets, LocalDate fromDate, LocalDate toDate,
                                                    String category, String department) {
        if (hasValue(category, "All Categories")) {
            assets = assets.stream()
                    .filter(a -> a.getCategory() != null && a.getCategory().equalsIgnoreCase(category))
                    .collect(Collectors.toList());
        }
        if (hasValue(department, "Global Operations")) {
            assets = assets.stream()
                    .filter(a -> a.getDepartment() != null && a.getDepartment().equalsIgnoreCase(department))
                    .collect(Collectors.toList());
        }
        if (fromDate != null) {
            assets = assets.stream()
                    .filter(a -> a.getPurchaseDate() != null && !a.getPurchaseDate().isBefore(fromDate))
                    .collect(Collectors.toList());
        }
        if (toDate != null) {
            assets = assets.stream()
                    .filter(a -> a.getPurchaseDate() != null && !a.getPurchaseDate().isAfter(toDate))
                    .collect(Collectors.toList());
        }
        return assets;
    }

    /**
     * Generate Inventory Summary Report - summary by category
     */
    @Cacheable(cacheNames = "reports", key = "'inventorySummary|' + #scope.cacheKey() + '|' + #department")
    @Transactional(readOnly = true)
    public ReportData generateInventorySummaryReport(ReportScope scope, String department) {
        ReportData report = new ReportData();
        report.setReportType("inventorySummary");
        report.setReportName("Inventory Summary");
        report.setGeneratedDate(LocalDate.now());
        report.setDepartment(department);

        List<Asset> assets = scopedAssets(scope);
        if (hasValue(department, "Global Operations")) {
            assets = assets.stream()
                    .filter(a -> a.getDepartment() != null && a.getDepartment().equalsIgnoreCase(department))
                    .collect(Collectors.toList());
        }
        return finish(report, assets);
    }

    /**
     * Generate Location Audit Report - assets by location/department
     */
    @Cacheable(cacheNames = "reports", key = "'locationAudit|' + #scope.cacheKey() + '|' + #department")
    @Transactional(readOnly = true)
    public ReportData generateLocationAuditReport(ReportScope scope, String department) {
        ReportData report = new ReportData();
        report.setReportType("locationAudit");
        report.setReportName("Location Audit");
        report.setGeneratedDate(LocalDate.now());
        report.setDepartment(department);

        List<Asset> assets = scopedAssets(scope);
        if (hasValue(department, "Global Operations")) {
            assets = assets.stream()
                    .filter(a -> a.getDepartment() != null && a.getDepartment().equalsIgnoreCase(department))
                    .collect(Collectors.toList());
        }
        return finish(report, assets);
    }

    /**
     * Generate Valuation Report - asset values and depreciation
     */
    @Cacheable(cacheNames = "reports",
            key = "'valuation|' + #scope.cacheKey() + '|' + #category + '|' + #department")
    @Transactional(readOnly = true)
    public ReportData generateValuationReport(ReportScope scope, String category, String department) {
        ReportData report = new ReportData();
        report.setReportType("valuationReport");
        report.setReportName("Asset Valuation Report");
        report.setGeneratedDate(LocalDate.now());
        report.setCategory(category);
        report.setDepartment(department);

        List<Asset> assets = scopedAssets(scope);
        if (hasValue(category, "All Categories")) {
            assets = assets.stream()
                    .filter(a -> a.getCategory() != null && a.getCategory().equalsIgnoreCase(category))
                    .collect(Collectors.toList());
        }
        if (hasValue(department, "Global Operations")) {
            assets = assets.stream()
                    .filter(a -> a.getDepartment() != null && a.getDepartment().equalsIgnoreCase(department))
                    .collect(Collectors.toList());
        }
        return finish(report, assets);
    }

    /**
     * Generate Depreciation Schedule Report
     */
    @Cacheable(cacheNames = "reports",
            key = "'depreciationSchedule|' + #scope.cacheKey() + '|' + #category + '|' + #department")
    @Transactional(readOnly = true)
    public ReportData generateDepreciationScheduleReport(ReportScope scope, String category, String department) {
        ReportData report = new ReportData();
        report.setReportType("depreciationSchedule");
        report.setReportName("Depreciation Schedule");
        report.setGeneratedDate(LocalDate.now());
        report.setCategory(category);
        report.setDepartment(department);

        List<Asset> assets = scopedAssets(scope);
        if (hasValue(category, "All Categories")) {
            assets = assets.stream()
                    .filter(a -> a.getCategory() != null && a.getCategory().equalsIgnoreCase(category))
                    .collect(Collectors.toList());
        }
        if (hasValue(department, "Global Operations")) {
            assets = assets.stream()
                    .filter(a -> a.getDepartment() != null && a.getDepartment().equalsIgnoreCase(department))
                    .collect(Collectors.toList());
        }
        return finish(report, assets);
    }

    /**
     * Generate Purchase Analysis Report
     */
    @Cacheable(cacheNames = "reports",
            key = "'purchaseAnalysis|' + #scope.cacheKey() + '|' + #startDate + '|' + #endDate")
    @Transactional(readOnly = true)
    public ReportData generatePurchaseAnalysisReport(ReportScope scope, LocalDate startDate, LocalDate endDate) {
        ReportData report = new ReportData();
        report.setReportType("purchaseAnalysis");
        report.setReportName("Purchase Analysis");
        report.setGeneratedDate(LocalDate.now());
        report.setStartDate(startDate);
        report.setEndDate(endDate);

        List<Asset> assets = scopedAssets(scope);
        if (startDate != null && endDate != null) {
            assets = assets.stream()
                    .filter(a -> a.getPurchaseDate() != null
                            && !a.getPurchaseDate().isBefore(startDate)
                            && !a.getPurchaseDate().isAfter(endDate))
                    .collect(Collectors.toList());
        }
        return finish(report, assets);
    }

    /**
     * Generate Maintenance History Report.
     * Applies the requested date range against the asset purchase date (asset-level date
     * available on the report row); defaults to the last month when unspecified.
     */
    @Cacheable(cacheNames = "reports",
            key = "'maintenanceHistory|' + #scope.cacheKey() + '|' + #startDate + '|' + #endDate")
    @Transactional(readOnly = true)
    public ReportData generateMaintenanceHistoryReport(ReportScope scope, LocalDate startDate, LocalDate endDate) {
        LocalDate effectiveStart = startDate != null ? startDate : LocalDate.now().minusMonths(1);
        LocalDate effectiveEnd = endDate != null ? endDate : LocalDate.now();

        ReportData report = new ReportData();
        report.setReportType("maintenanceHistory");
        report.setReportName("Service History");
        report.setGeneratedDate(LocalDate.now());
        report.setStartDate(effectiveStart);
        report.setEndDate(effectiveEnd);

        List<Asset> assets = scopedAssets(scope).stream()
                .filter(a -> a.getPurchaseDate() != null
                        && !a.getPurchaseDate().isBefore(effectiveStart)
                        && !a.getPurchaseDate().isAfter(effectiveEnd))
                .collect(Collectors.toList());
        return finish(report, assets);
    }

    /**
     * Generate Warranty Expiry Report
     */
    @Cacheable(cacheNames = "reports", key = "'warrantyExpiry|' + #scope.cacheKey()")
    @Transactional(readOnly = true)
    public ReportData generateWarrantyExpiryReport(ReportScope scope) {
        ReportData report = new ReportData();
        report.setReportType("warrantyExpiry");
        report.setReportName("Warranty Expiry");
        report.setGeneratedDate(LocalDate.now());

        List<Asset> assets = scopedAssets(scope).stream()
                .filter(a -> a.getWarrantyExpiry() != null)
                .collect(Collectors.toList());
        return finish(report, assets);
    }

    /**
     * Generate Missing Assets Report - assets not in stock/assigned status
     */
    @Cacheable(cacheNames = "reports", key = "'missingAssets|' + #scope.cacheKey()")
    @Transactional(readOnly = true)
    public ReportData generateMissingAssetsReport(ReportScope scope) {
        ReportData report = new ReportData();
        report.setReportType("missingAssets");
        report.setReportName("Missing Asset Report");
        report.setGeneratedDate(LocalDate.now());

        List<Asset> assets = scopedAssets(scope).stream()
                .filter(a -> !"In Stock".equalsIgnoreCase(a.getStatus())
                        && !"Assigned".equalsIgnoreCase(a.getStatus()))
                .collect(Collectors.toList());
        return finish(report, assets);
    }

    /**
     * Generate Disposal Logs Report
     */
    @Cacheable(cacheNames = "reports", key = "'disposalLogs|' + #scope.cacheKey()")
    @Transactional(readOnly = true)
    public ReportData generateDisposalLogsReport(ReportScope scope) {
        ReportData report = new ReportData();
        report.setReportType("disposalLogs");
        report.setReportName("Disposal Logs");
        report.setGeneratedDate(LocalDate.now());

        List<Asset> assets = scopedAssets(scope).stream()
                .filter(a -> "Disposed".equalsIgnoreCase(a.getStatus())
                        || "Retired".equalsIgnoreCase(a.getStatus()))
                .collect(Collectors.toList());
        return finish(report, assets);
    }

    /**
     * Map assets to rows, attach them to the report and compute totals.
     * Accumulated depreciation / net book value come from each asset's latest
     * {@link DepreciationPosting}, fetched in a single batch query rather than per-asset.
     */
    private ReportData finish(ReportData report, List<Asset> assets) {
        List<Long> assetIds = assets.stream().map(Asset::getId).collect(Collectors.toList());
        Map<Long, DepreciationPosting> latestPostings = depreciationService.getLatestPostingsForAssets(assetIds);

        List<ReportRow> rows = assets.stream()
                .map(a -> assetToReportRow(a, latestPostings.get(a.getId())))
                .collect(Collectors.toList());
        report.setRows(rows);
        calculateTotals(report, rows);
        return report;
    }

    /**
     * Convert Asset entity to ReportRow. If the asset has never been through a
     * depreciation run (no posting yet), accumulated depreciation is 0 and NBV == cost.
     */
    private ReportRow assetToReportRow(Asset asset, DepreciationPosting latestPosting) {
        BigDecimal cost = asset.getPurchaseCost() != null ? asset.getPurchaseCost() : BigDecimal.ZERO;
        BigDecimal accumulatedDepreciation = latestPosting != null && latestPosting.getClosingAccumulatedDepreciation() != null
                ? latestPosting.getClosingAccumulatedDepreciation()
                : BigDecimal.ZERO;
        BigDecimal netBookValue = latestPosting != null && latestPosting.getBookValue() != null
                ? latestPosting.getBookValue()
                : cost;

        return new ReportRow(
                asset.getId(),
                asset.getAssetCode(),
                asset.getName(),
                asset.getCategory(),
                asset.getDepartment(),
                asset.getCustodian(),
                asset.getPurchaseDate(),
                cost,
                accumulatedDepreciation,
                netBookValue,
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