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
    private final com.example.fams.assets.CapitalAdditionService capitalAdditionService;
    private final com.example.fams.depreciation.DepreciationAdjustmentService depreciationAdjustmentService;
    private final com.example.fams.valuation.AssetRevaluationService assetRevaluationService;
    private final com.example.fams.valuation.AssetImpairmentService assetImpairmentService;
    private final com.example.fams.lifecycle.AssetLifecycleService assetLifecycleService;
    private final com.example.fams.lifecycle.AssetLifecycleHistoryRepository lifecycleHistoryRepository;
    private final com.example.fams.lifecycle.AssetLifecycleApprovalActionRepository lifecycleApprovalActionRepository;

    public ReportService(AssetRepository assetRepository,
                         DepreciationService depreciationService,
                         MaintenanceService maintenanceService,
                         com.example.fams.assets.CapitalAdditionService capitalAdditionService,
                         com.example.fams.depreciation.DepreciationAdjustmentService depreciationAdjustmentService,
                         com.example.fams.valuation.AssetRevaluationService assetRevaluationService,
                         com.example.fams.valuation.AssetImpairmentService assetImpairmentService,
                         com.example.fams.lifecycle.AssetLifecycleService assetLifecycleService,
                         com.example.fams.lifecycle.AssetLifecycleHistoryRepository lifecycleHistoryRepository,
                         com.example.fams.lifecycle.AssetLifecycleApprovalActionRepository lifecycleApprovalActionRepository) {
        this.assetRepository = assetRepository;
        this.depreciationService = depreciationService;
        this.maintenanceService = maintenanceService;
        this.capitalAdditionService = capitalAdditionService;
        this.depreciationAdjustmentService = depreciationAdjustmentService;
        this.assetRevaluationService = assetRevaluationService;
        this.assetImpairmentService = assetImpairmentService;
        this.assetLifecycleService = assetLifecycleService;
        this.lifecycleHistoryRepository = lifecycleHistoryRepository;
        this.lifecycleApprovalActionRepository = lifecycleApprovalActionRepository;
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
     * Capitalisation Adjustment Report: subsequent capital
     * additions/improvements recorded against assets (see {@code CapitalAddition}),
     * within a reporting period. Scoped to assets the caller's {@link ReportScope}
     * permits seeing.
     */
    @Cacheable(cacheNames = "reports",
            key = "'capitalAdjustments|' + #scope.cacheKey() + '|' + #fromDate + '|' + #toDate + '|' + #category + '|' + #department")
    @Transactional(readOnly = true)
    public CapitalAdjustmentReportData generateCapitalisationAdjustmentReport(ReportScope scope, LocalDate fromDate, LocalDate toDate,
                                                                               String category, String department) {
        java.util.Set<Long> visibleAssetIds = scopedAssets(scope).stream()
                .map(Asset::getId).collect(Collectors.toSet());

        List<com.example.fams.assets.CapitalAddition> additions = capitalAdditionService.findByDateRange(fromDate, toDate).stream()
                .filter(a -> visibleAssetIds.contains(a.getAsset().getId()))
                .collect(Collectors.toList());

        if (hasValue(category, "All Categories")) {
            additions = additions.stream()
                    .filter(a -> a.getAsset().getCategory() != null && a.getAsset().getCategory().equalsIgnoreCase(category))
                    .collect(Collectors.toList());
        }
        if (hasValue(department, "Global Operations")) {
            additions = additions.stream()
                    .filter(a -> a.getAsset().getDepartment() != null && a.getAsset().getDepartment().equalsIgnoreCase(department))
                    .collect(Collectors.toList());
        }

        List<CapitalAdjustmentRow> rows = additions.stream()
                .map(a -> new CapitalAdjustmentRow(
                        a.getDate(),
                        a.getAsset().getAssetCode(),
                        a.getAsset().getName(),
                        a.getAsset().getCategory(),
                        a.getAsset().getDepartment(),
                        a.getType() == null ? null : a.getType().name(),
                        a.getAmount(),
                        a.getDescription(),
                        a.getRecordedBy()
                ))
                .collect(Collectors.toList());

        CapitalAdjustmentReportData data = new CapitalAdjustmentReportData();
        data.setGeneratedDate(LocalDate.now());
        data.setFromDate(fromDate);
        data.setToDate(toDate);
        data.setRows(rows);
        data.setTotalAmount(rows.stream().map(CapitalAdjustmentRow::amount)
                .filter(v -> v != null).reduce(BigDecimal.ZERO, BigDecimal::add));
        return data;
    }

    /**
     * Asset Component Register: assets that are components of a
     * parent asset (Asset.parentAssetId), shown alongside their parent's identity.
     *
     * <p>Scoping note: only components themselves are checked against the caller's
     * {@link ReportScope} — a department head sees components in their department even
     * if the parent happens to sit elsewhere, since the component is the asset actually
     * assigned/tracked there. The parent's code/name are looked up unscoped purely for
     * display context, not exposed as a full row.
     */
    @Cacheable(cacheNames = "reports",
            key = "'assetComponents|' + #scope.cacheKey() + '|' + #category + '|' + #department")
    @Transactional(readOnly = true)
    public ComponentReportData generateAssetComponentRegisterReport(ReportScope scope, String category, String department) {
        List<Asset> components = scopedAssets(scope).stream()
                .filter(a -> a.getParentAssetId() != null)
                .collect(Collectors.toList());
        if (hasValue(category, "All Categories")) {
            components = components.stream()
                    .filter(a -> a.getCategory() != null && a.getCategory().equalsIgnoreCase(category))
                    .collect(Collectors.toList());
        }
        if (hasValue(department, "Global Operations")) {
            components = components.stream()
                    .filter(a -> a.getDepartment() != null && a.getDepartment().equalsIgnoreCase(department))
                    .collect(Collectors.toList());
        }

        java.util.Set<Long> parentIds = components.stream()
                .map(Asset::getParentAssetId).collect(Collectors.toSet());
        Map<Long, Asset> parentsById = assetRepository.findAllById(parentIds).stream()
                .collect(Collectors.toMap(Asset::getId, a -> a));

        List<ComponentRow> rows = components.stream()
                .map(c -> {
                    Asset parent = parentsById.get(c.getParentAssetId());
                    return new ComponentRow(
                            c.getAssetCode(),
                            c.getName(),
                            c.getCategory(),
                            c.getPurchaseDate(),
                            c.getPurchaseCost(),
                            c.getStatus(),
                            parent != null ? parent.getAssetCode() : "Unknown",
                            parent != null ? parent.getName() : "Unknown (parent asset may have been deleted)"
                    );
                })
                .collect(Collectors.toList());

        ComponentReportData data = new ComponentReportData();
        data.setGeneratedDate(LocalDate.now());
        data.setRows(rows);
        data.setTotalValue(rows.stream().map(ComponentRow::acquisitionCost)
                .filter(v -> v != null).reduce(BigDecimal.ZERO, BigDecimal::add));
        return data;
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
     * "Asset Register by X" reports (category, location, department, cost centre,
     * custodian, status, acquisition date, supplier).
     * One implementation parameterized by {@link AssetGroupBy} instead of six-plus near-
     * identical report methods; grouping key extraction lives in {@link #groupKeyFor}.
     *
     * <p>{@code fromDate}/{@code toDate} are optional and filter on {@code purchaseDate}
     * (acquisition date) when present — this is how the Acquisition by Supplier/Category/
     * Department reports reuse the same grouping for a reporting period. When grouping by
     * capitalisation month they filter on {@code capitalisationDate} instead.
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
        java.util.function.Function<Asset, LocalDate> filterDate = groupBy == AssetGroupBy.CAPITALISATION_MONTH
                ? Asset::getCapitalisationDate : Asset::getPurchaseDate;
        if (fromDate != null) {
            assets = assets.stream()
                    .filter(a -> filterDate.apply(a) != null && !filterDate.apply(a).isBefore(fromDate))
                    .collect(Collectors.toList());
        }
        if (toDate != null) {
            assets = assets.stream()
                    .filter(a -> filterDate.apply(a) != null && !filterDate.apply(a).isAfter(toDate))
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
            case CAPITALISATION_MONTH -> asset.getCapitalisationDate() == null ? "Not yet capitalised"
                    : asset.getCapitalisationDate().getYear() + "-" + String.format("%02d", asset.getCapitalisationDate().getMonthValue());
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
            case CAPITALISATION_MONTH -> "Capitalisation Date";
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
     * Period-based depreciation reports (Monthly,
     * Expense, By Category, By Department, By Cost Centre), built to respect
     * {@link ReportScope}.
     *
     * <p><b>Security note:</b> {@link DepreciationService#getDepreciationReport},
     * {@code getCategoryReport} and {@code getDepartmentReport} query ALL postings for a
     * period company-wide with no scope filtering — appropriate for the admin
     * depreciation module, not for the Reports module where a department head must only
     * see their own department. So these methods re-aggregate from scratch off a
     * scope-filtered posting list, rather than reusing those methods' pre-aggregated
     * totals (which would already be wrong before any filtering could fix them).
     */
    private List<DepreciationPosting> scopedPostingsForPeriod(ReportScope scope, String period) {
        java.util.Set<Long> visibleAssetIds = scopedAssets(scope).stream().map(Asset::getId).collect(Collectors.toSet());
        return depreciationService.getDepreciationReport(period).getPostings().stream()
                .filter(p -> visibleAssetIds.contains(p.getAssetId()))
                .collect(Collectors.toList());
    }

    @Cacheable(cacheNames = "reports", key = "'monthlyDepreciation|' + #scope.cacheKey() + '|' + #period")
    @Transactional(readOnly = true)
    public com.example.fams.depreciation.DepreciationReport generateMonthlyDepreciationReport(ReportScope scope, String period) {
        List<DepreciationPosting> postings = scopedPostingsForPeriod(scope, period);
        return buildDepreciationReport(period, postings);
    }

    @Cacheable(cacheNames = "reports", key = "'depreciationByCategoryPeriod|' + #scope.cacheKey() + '|' + #period")
    @Transactional(readOnly = true)
    public List<com.example.fams.depreciation.DepreciationCategoryReport> generateDepreciationByCategoryPeriodReport(ReportScope scope, String period) {
        List<DepreciationPosting> postings = scopedPostingsForPeriod(scope, period);
        return postings.stream()
                .collect(Collectors.groupingBy(DepreciationPosting::getCategory))
                .entrySet().stream()
                .map(e -> {
                    com.example.fams.depreciation.DepreciationCategoryReport r = new com.example.fams.depreciation.DepreciationCategoryReport();
                    r.setCategory(e.getKey());
                    populateTotals(r::setPostings, r::setTotalOriginalCost, r::setTotalAccumulatedDepreciation,
                            r::setTotalDepreciationCharge, r::setTotalBookValue, r::setAssetCount, e.getValue());
                    return r;
                })
                .sorted((a, b) -> a.getCategory().compareToIgnoreCase(b.getCategory()))
                .collect(Collectors.toList());
    }

    @Cacheable(cacheNames = "reports", key = "'depreciationByDepartmentPeriod|' + #scope.cacheKey() + '|' + #period")
    @Transactional(readOnly = true)
    public List<com.example.fams.depreciation.DepreciationDepartmentReport> generateDepreciationByDepartmentPeriodReport(ReportScope scope, String period) {
        List<DepreciationPosting> postings = scopedPostingsForPeriod(scope, period);
        return postings.stream()
                .collect(Collectors.groupingBy(DepreciationPosting::getDepartment))
                .entrySet().stream()
                .map(e -> {
                    com.example.fams.depreciation.DepreciationDepartmentReport r = new com.example.fams.depreciation.DepreciationDepartmentReport();
                    r.setDepartment(e.getKey());
                    populateTotals(r::setPostings, r::setTotalOriginalCost, r::setTotalAccumulatedDepreciation,
                            r::setTotalDepreciationCharge, r::setTotalBookValue, r::setAssetCount, e.getValue());
                    return r;
                })
                .sorted((a, b) -> a.getDepartment().compareToIgnoreCase(b.getDepartment()))
                .collect(Collectors.toList());
    }

    /**
     * By Cost Centre has no equivalent in DepreciationService at all (postings don't
     * carry cost centre) — grouped here via a join back to Asset.costCentre.
     */
    @Cacheable(cacheNames = "reports", key = "'depreciationByCostCentrePeriod|' + #scope.cacheKey() + '|' + #period")
    @Transactional(readOnly = true)
    public List<Map<String, Object>> generateDepreciationByCostCentrePeriodReport(ReportScope scope, String period) {
        List<DepreciationPosting> postings = scopedPostingsForPeriod(scope, period);
        Map<Long, Asset> assetsById = scopedAssets(scope).stream().collect(Collectors.toMap(Asset::getId, a -> a));

        Map<String, List<DepreciationPosting>> byCostCentre = postings.stream()
                .collect(Collectors.groupingBy(p -> {
                    Asset a = assetsById.get(p.getAssetId());
                    String cc = a != null ? a.getCostCentre() : null;
                    return (cc == null || cc.isBlank()) ? "Unspecified" : cc;
                }));

        List<Map<String, Object>> result = new java.util.ArrayList<>();
        byCostCentre.forEach((costCentre, list) -> {
            java.util.concurrent.atomic.AtomicReference<BigDecimal> cost = new java.util.concurrent.atomic.AtomicReference<>(BigDecimal.ZERO);
            java.util.concurrent.atomic.AtomicReference<BigDecimal> accum = new java.util.concurrent.atomic.AtomicReference<>(BigDecimal.ZERO);
            java.util.concurrent.atomic.AtomicReference<BigDecimal> charge = new java.util.concurrent.atomic.AtomicReference<>(BigDecimal.ZERO);
            java.util.concurrent.atomic.AtomicReference<BigDecimal> book = new java.util.concurrent.atomic.AtomicReference<>(BigDecimal.ZERO);
            for (DepreciationPosting p : list) {
                if (p.getAssetCost() != null) cost.set(cost.get().add(p.getAssetCost()));
                if (p.getClosingAccumulatedDepreciation() != null) accum.set(accum.get().add(p.getClosingAccumulatedDepreciation()));
                if (p.getDepreciationCharge() != null) charge.set(charge.get().add(p.getDepreciationCharge()));
                if (p.getBookValue() != null) book.set(book.get().add(p.getBookValue()));
            }
            Map<String, Object> row = new java.util.LinkedHashMap<>();
            row.put("costCentre", costCentre);
            row.put("assetCount", list.size());
            row.put("totalOriginalCost", cost.get());
            row.put("totalAccumulatedDepreciation", accum.get());
            row.put("totalDepreciationCharge", charge.get());
            row.put("totalBookValue", book.get());
            result.add(row);
        });
        result.sort((a, b) -> ((String) a.get("costCentre")).compareToIgnoreCase((String) b.get("costCentre")));
        return result;
    }

    /**
     * Depreciation Expense Report: depreciation charge recognized
     * within a reporting period (date range), as distinct from the Monthly Depreciation
     * Report's single named period. Sums DepreciationPosting.depreciationCharge for
     * postings whose createdAt falls in range.
     */
    @Cacheable(cacheNames = "reports",
            key = "'depreciationExpense|' + #scope.cacheKey() + '|' + #fromDate + '|' + #toDate + '|' + #category + '|' + #department")
    @Transactional(readOnly = true)
    public AcquisitionCostAnalysisData generateDepreciationExpenseReport(ReportScope scope, LocalDate fromDate, LocalDate toDate,
                                                                           String category, String department) {
        List<Asset> assets = scopedAssets(scope);
        if (hasValue(category, "All Categories")) {
            assets = assets.stream().filter(a -> a.getCategory() != null && a.getCategory().equalsIgnoreCase(category)).collect(Collectors.toList());
        }
        if (hasValue(department, "Global Operations")) {
            assets = assets.stream().filter(a -> a.getDepartment() != null && a.getDepartment().equalsIgnoreCase(department)).collect(Collectors.toList());
        }
        java.util.Set<Long> ids = assets.stream().map(Asset::getId).collect(Collectors.toSet());

        List<DepreciationPosting> postings = assets.stream()
                .flatMap(a -> depreciationService.getDepreciationHistory(a.getId()).stream())
                .filter(p -> {
                    LocalDate postedOn = p.getCreatedAt() != null ? p.getCreatedAt().toLocalDate() : null;
                    if (postedOn == null) return true;
                    if (fromDate != null && postedOn.isBefore(fromDate)) return false;
                    if (toDate != null && postedOn.isAfter(toDate)) return false;
                    return true;
                })
                .collect(Collectors.toList());

        AcquisitionCostAnalysisData data = new AcquisitionCostAnalysisData(); // reused: total/avg/min/max + category breakdown shape fits directly
        data.setGeneratedDate(LocalDate.now());
        data.setFromDate(fromDate);
        data.setToDate(toDate);
        data.setTotalAssets((int) postings.stream().map(DepreciationPosting::getAssetId).distinct().count());

        List<BigDecimal> charges = postings.stream()
                .map(p -> p.getDepreciationCharge() == null ? BigDecimal.ZERO : p.getDepreciationCharge())
                .collect(Collectors.toList());
        BigDecimal total = charges.stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        data.setTotalCost(total);
        data.setAverageCost(charges.isEmpty() ? BigDecimal.ZERO : total.divide(BigDecimal.valueOf(charges.size()), 2, java.math.RoundingMode.HALF_UP));
        data.setMinCost(charges.stream().min(BigDecimal::compareTo).orElse(BigDecimal.ZERO));
        data.setMaxCost(charges.stream().max(BigDecimal::compareTo).orElse(BigDecimal.ZERO));

        Map<String, List<DepreciationPosting>> byCategory = postings.stream()
                .collect(Collectors.groupingBy(p -> p.getCategory() == null ? "Uncategorized" : p.getCategory()));
        data.setByCategory(byCategory.entrySet().stream()
                .map(e -> new AssetSummaryData.CategoryBreakdown(e.getKey(), e.getValue().size(),
                        e.getValue().stream().map(p -> p.getDepreciationCharge() == null ? BigDecimal.ZERO : p.getDepreciationCharge()).reduce(BigDecimal.ZERO, BigDecimal::add),
                        BigDecimal.ZERO, BigDecimal.ZERO))
                .sorted((a, b) -> a.category().compareToIgnoreCase(b.category()))
                .collect(Collectors.toList()));

        return data;
    }

    private void populateTotals(java.util.function.Consumer<List<DepreciationPosting>> setPostings,
                                  java.util.function.Consumer<BigDecimal> setCost,
                                  java.util.function.Consumer<BigDecimal> setAccum,
                                  java.util.function.Consumer<BigDecimal> setCharge,
                                  java.util.function.Consumer<BigDecimal> setBook,
                                  java.util.function.IntConsumer setCount,
                                  List<DepreciationPosting> postings) {
        setPostings.accept(postings);
        setCost.accept(postings.stream().map(p -> p.getAssetCost() == null ? BigDecimal.ZERO : p.getAssetCost()).reduce(BigDecimal.ZERO, BigDecimal::add));
        setAccum.accept(postings.stream().map(p -> p.getClosingAccumulatedDepreciation() == null ? BigDecimal.ZERO : p.getClosingAccumulatedDepreciation()).reduce(BigDecimal.ZERO, BigDecimal::add));
        setCharge.accept(postings.stream().map(p -> p.getDepreciationCharge() == null ? BigDecimal.ZERO : p.getDepreciationCharge()).reduce(BigDecimal.ZERO, BigDecimal::add));
        setBook.accept(postings.stream().map(p -> p.getBookValue() == null ? BigDecimal.ZERO : p.getBookValue()).reduce(BigDecimal.ZERO, BigDecimal::add));
        setCount.accept((int) postings.stream().map(DepreciationPosting::getAssetId).distinct().count());
    }

    private com.example.fams.depreciation.DepreciationReport buildDepreciationReport(String period, List<DepreciationPosting> postings) {
        com.example.fams.depreciation.DepreciationReport report = new com.example.fams.depreciation.DepreciationReport();
        report.setPeriod(period);
        report.setPostings(postings);
        report.setTotalOriginalCost(postings.stream().map(p -> p.getAssetCost() == null ? BigDecimal.ZERO : p.getAssetCost()).reduce(BigDecimal.ZERO, BigDecimal::add));
        report.setTotalAccumulatedDepreciation(postings.stream().map(p -> p.getClosingAccumulatedDepreciation() == null ? BigDecimal.ZERO : p.getClosingAccumulatedDepreciation()).reduce(BigDecimal.ZERO, BigDecimal::add));
        report.setTotalDepreciationCharge(postings.stream().map(p -> p.getDepreciationCharge() == null ? BigDecimal.ZERO : p.getDepreciationCharge()).reduce(BigDecimal.ZERO, BigDecimal::add));
        report.setTotalBookValue(postings.stream().map(p -> p.getBookValue() == null ? BigDecimal.ZERO : p.getBookValue()).reduce(BigDecimal.ZERO, BigDecimal::add));
        report.setAssetCount((int) postings.stream().map(DepreciationPosting::getAssetId).distinct().count());
        report.setFullyDepreciatedCount((int) postings.stream().filter(p -> Boolean.TRUE.equals(p.getFullyDepreciated())).count());
        return report;
    }

    /**
     * Depreciation Adjustment Report: manual corrections,
     * reversals and recalculations recorded against assets (see
     * {@code DepreciationAdjustment}), scoped to assets the caller's {@link ReportScope}
     * permits seeing.
     */
    @Cacheable(cacheNames = "reports",
            key = "'depreciationAdjustments|' + #scope.cacheKey() + '|' + #fromDate + '|' + #toDate + '|' + #category + '|' + #department")
    @Transactional(readOnly = true)
    public DepreciationAdjustmentReportData generateDepreciationAdjustmentReport(ReportScope scope, LocalDate fromDate, LocalDate toDate,
                                                                                   String category, String department) {
        java.util.Set<Long> visibleAssetIds = scopedAssets(scope).stream().map(Asset::getId).collect(Collectors.toSet());

        List<com.example.fams.depreciation.DepreciationAdjustment> adjustments = depreciationAdjustmentService.findByDateRange(fromDate, toDate).stream()
                .filter(a -> visibleAssetIds.contains(a.getAsset().getId()))
                .collect(Collectors.toList());

        if (hasValue(category, "All Categories")) {
            adjustments = adjustments.stream()
                    .filter(a -> a.getAsset().getCategory() != null && a.getAsset().getCategory().equalsIgnoreCase(category))
                    .collect(Collectors.toList());
        }
        if (hasValue(department, "Global Operations")) {
            adjustments = adjustments.stream()
                    .filter(a -> a.getAsset().getDepartment() != null && a.getAsset().getDepartment().equalsIgnoreCase(department))
                    .collect(Collectors.toList());
        }

        List<DepreciationAdjustmentRow> rows = adjustments.stream()
                .map(a -> new DepreciationAdjustmentRow(
                        a.getDate(), a.getAsset().getAssetCode(), a.getAsset().getName(),
                        a.getAsset().getCategory(), a.getAsset().getDepartment(), a.getPeriod(),
                        a.getType() == null ? null : a.getType().name(), a.getAmount(), a.getReason(), a.getRecordedBy()
                ))
                .collect(Collectors.toList());

        DepreciationAdjustmentReportData data = new DepreciationAdjustmentReportData();
        data.setGeneratedDate(LocalDate.now());
        data.setRows(rows);
        data.setTotalAmount(rows.stream().map(DepreciationAdjustmentRow::amount).filter(v -> v != null).reduce(BigDecimal.ZERO, BigDecimal::add));
        return data;
    }

    // Transfer and assignment reports — several of these share the same transfer data

    private List<com.example.fams.lifecycle.AssetLifecycleWorkflow> scopedTransferWorkflows(ReportScope scope, String category) {
        java.util.Set<Long> visibleAssetIds = scopedAssets(scope).stream().map(Asset::getId).collect(Collectors.toSet());
        List<com.example.fams.lifecycle.AssetLifecycleWorkflow> workflows = assetLifecycleService.findAllWorkflows().stream()
                .filter(w -> w.getType() == com.example.fams.lifecycle.LifecycleWorkflowType.TRANSFER)
                .filter(w -> visibleAssetIds.contains(w.getAsset().getId()))
                .collect(Collectors.toList());
        if (hasValue(category, "All Categories")) {
            workflows = workflows.stream()
                    .filter(w -> w.getAsset().getCategory() != null && w.getAsset().getCategory().equalsIgnoreCase(category))
                    .collect(Collectors.toList());
        }
        return workflows;
    }

    private List<com.example.fams.lifecycle.AssetLifecycleWorkflow> filterByRequestedDateRange(
            List<com.example.fams.lifecycle.AssetLifecycleWorkflow> workflows, LocalDate fromDate, LocalDate toDate) {
        if (fromDate != null) {
            workflows = workflows.stream()
                    .filter(w -> w.getRequestedEffectiveDate() != null && !w.getRequestedEffectiveDate().isBefore(fromDate))
                    .collect(Collectors.toList());
        }
        if (toDate != null) {
            workflows = workflows.stream()
                    .filter(w -> w.getRequestedEffectiveDate() != null && !w.getRequestedEffectiveDate().isAfter(toDate))
                    .collect(Collectors.toList());
        }
        return workflows;
    }

    private TransferRow toTransferRow(com.example.fams.lifecycle.AssetLifecycleWorkflow w) {
        return new TransferRow(
                w.getRequestedEffectiveDate(), w.getAsset().getAssetCode(), w.getAsset().getName(), w.getAsset().getCategory(),
                w.getStatus() == null ? null : w.getStatus().name(),
                w.getFromDepartment(), w.getToDepartment(), w.getFromBranch(), w.getToBranch(),
                w.getFromLocation(), w.getToLocation(), w.getFromEmployee(), w.getToEmployee(),
                w.getRequestedBy(), w.getReason()
        );
    }

    private TransferReportData wrapTransferRows(List<com.example.fams.lifecycle.AssetLifecycleWorkflow> workflows) {
        TransferReportData data = new TransferReportData();
        data.setGeneratedDate(LocalDate.now());
        data.setRows(workflows.stream().map(this::toTransferRow).collect(Collectors.toList()));
        return data;
    }

    /** Asset Transfer Report: all transfers, optionally within a period. */
    @Transactional(readOnly = true)
    public TransferReportData generateAssetTransferReport(ReportScope scope, LocalDate fromDate, LocalDate toDate,
                                                            String category, String department) {
        List<com.example.fams.lifecycle.AssetLifecycleWorkflow> workflows = scopedTransferWorkflows(scope, category);
        if (hasValue(department, "Global Operations")) {
            workflows = workflows.stream()
                    .filter(w -> w.getAsset().getDepartment() != null && w.getAsset().getDepartment().equalsIgnoreCase(department))
                    .collect(Collectors.toList());
        }
        workflows = filterByRequestedDateRange(workflows, fromDate, toDate);
        return wrapTransferRows(workflows);
    }

    /**
     * Transfer-In Register: transfers received into a unit. "unit"
     * matches against {@code toDepartment}, {@code toBranch} or {@code toLocation} (any
     * match counts) — the report doesn't define which dimension "unit" means, so this
     * accepts whichever one the caller has data for rather than forcing department only.
     */
    @Transactional(readOnly = true)
    public TransferReportData generateTransferInRegister(ReportScope scope, LocalDate fromDate, LocalDate toDate,
                                                           String category, String unit) {
        List<com.example.fams.lifecycle.AssetLifecycleWorkflow> workflows = scopedTransferWorkflows(scope, category);
        workflows = filterByRequestedDateRange(workflows, fromDate, toDate);
        if (unit != null && !unit.isBlank() && !unit.equalsIgnoreCase("All")) {
            workflows = workflows.stream()
                    .filter(w -> unit.equalsIgnoreCase(w.getToDepartment())
                            || unit.equalsIgnoreCase(w.getToBranch())
                            || unit.equalsIgnoreCase(w.getToLocation()))
                    .collect(Collectors.toList());
        }
        return wrapTransferRows(workflows);
    }

    /** Transfer-Out Register: same as the Transfer-In Register, mirrored on the "from" side. */
    @Transactional(readOnly = true)
    public TransferReportData generateTransferOutRegister(ReportScope scope, LocalDate fromDate, LocalDate toDate,
                                                            String category, String unit) {
        List<com.example.fams.lifecycle.AssetLifecycleWorkflow> workflows = scopedTransferWorkflows(scope, category);
        workflows = filterByRequestedDateRange(workflows, fromDate, toDate);
        if (unit != null && !unit.isBlank() && !unit.equalsIgnoreCase("All")) {
            workflows = workflows.stream()
                    .filter(w -> unit.equalsIgnoreCase(w.getFromDepartment())
                            || unit.equalsIgnoreCase(w.getFromBranch())
                            || unit.equalsIgnoreCase(w.getFromLocation()))
                    .collect(Collectors.toList());
        }
        return wrapTransferRows(workflows);
    }

    /** Inter-Branch Transfer Report: transfers where the branch actually changed. */
    @Transactional(readOnly = true)
    public TransferReportData generateInterBranchTransferReport(ReportScope scope, LocalDate fromDate, LocalDate toDate,
                                                                  String category, String department) {
        List<com.example.fams.lifecycle.AssetLifecycleWorkflow> workflows = scopedTransferWorkflows(scope, category);
        if (hasValue(department, "Global Operations")) {
            workflows = workflows.stream()
                    .filter(w -> w.getAsset().getDepartment() != null && w.getAsset().getDepartment().equalsIgnoreCase(department))
                    .collect(Collectors.toList());
        }
        workflows = filterByRequestedDateRange(workflows, fromDate, toDate);
        workflows = workflows.stream()
                .filter(w -> w.getFromBranch() != null && w.getToBranch() != null && !w.getFromBranch().equalsIgnoreCase(w.getToBranch()))
                .collect(Collectors.toList());
        return wrapTransferRows(workflows);
    }

    /** Inter-Department Transfer Report: transfers where the department actually changed. */
    @Transactional(readOnly = true)
    public TransferReportData generateInterDepartmentTransferReport(ReportScope scope, LocalDate fromDate, LocalDate toDate,
                                                                      String category) {
        List<com.example.fams.lifecycle.AssetLifecycleWorkflow> workflows = scopedTransferWorkflows(scope, category);
        workflows = filterByRequestedDateRange(workflows, fromDate, toDate);
        workflows = workflows.stream()
                .filter(w -> w.getFromDepartment() != null && w.getToDepartment() != null && !w.getFromDepartment().equalsIgnoreCase(w.getToDepartment()))
                .collect(Collectors.toList());
        return wrapTransferRows(workflows);
    }

    /** Asset Assignment Report: current custodian per asset. */
    @Cacheable(cacheNames = "reports", key = "'assetAssignment|' + #scope.cacheKey() + '|' + #category + '|' + #department")
    @Transactional(readOnly = true)
    public AssignmentReportData generateAssetAssignmentReport(ReportScope scope, String category, String department) {
        List<Asset> assets = scopedAssets(scope);
        if (hasValue(category, "All Categories")) {
            assets = assets.stream().filter(a -> a.getCategory() != null && a.getCategory().equalsIgnoreCase(category)).collect(Collectors.toList());
        }
        if (hasValue(department, "Global Operations")) {
            assets = assets.stream().filter(a -> a.getDepartment() != null && a.getDepartment().equalsIgnoreCase(department)).collect(Collectors.toList());
        }
        assets = assets.stream().filter(a -> a.getCustodian() != null && !a.getCustodian().isBlank()).collect(Collectors.toList());

        AssignmentReportData data = new AssignmentReportData();
        data.setGeneratedDate(LocalDate.now());
        data.setRows(assets.stream()
                .map(a -> new AssignmentRow(a.getAssetCode(), a.getName(), a.getCategory(), a.getCustodian(), a.getDepartment(), a.getBranch(), a.getStatus()))
                .collect(Collectors.toList()));
        return data;
    }

    /** Unassigned Assets Report: assets with no current custodian. */
    @Cacheable(cacheNames = "reports", key = "'unassignedAssets|' + #scope.cacheKey() + '|' + #category + '|' + #department")
    @Transactional(readOnly = true)
    public AssignmentReportData generateUnassignedAssetsReport(ReportScope scope, String category, String department) {
        List<Asset> assets = scopedAssets(scope);
        if (hasValue(category, "All Categories")) {
            assets = assets.stream().filter(a -> a.getCategory() != null && a.getCategory().equalsIgnoreCase(category)).collect(Collectors.toList());
        }
        if (hasValue(department, "Global Operations")) {
            assets = assets.stream().filter(a -> a.getDepartment() != null && a.getDepartment().equalsIgnoreCase(department)).collect(Collectors.toList());
        }
        assets = assets.stream().filter(a -> a.getCustodian() == null || a.getCustodian().isBlank()).collect(Collectors.toList());

        AssignmentReportData data = new AssignmentReportData();
        data.setGeneratedDate(LocalDate.now());
        data.setRows(assets.stream()
                .map(a -> new AssignmentRow(a.getAssetCode(), a.getName(), a.getCategory(), a.getCustodian(), a.getDepartment(), a.getBranch(), a.getStatus()))
                .collect(Collectors.toList()));
        return data;
    }

    /** Asset Assignment History: transfers where the custodian actually changed. */
    @Transactional(readOnly = true)
    public TransferReportData generateAssignmentHistoryReport(ReportScope scope, LocalDate fromDate, LocalDate toDate,
                                                                String category, String department) {
        List<com.example.fams.lifecycle.AssetLifecycleWorkflow> workflows = scopedTransferWorkflows(scope, category);
        if (hasValue(department, "Global Operations")) {
            workflows = workflows.stream()
                    .filter(w -> w.getAsset().getDepartment() != null && w.getAsset().getDepartment().equalsIgnoreCase(department))
                    .collect(Collectors.toList());
        }
        workflows = filterByRequestedDateRange(workflows, fromDate, toDate);
        workflows = workflows.stream()
                .filter(w -> !java.util.Objects.equals(w.getFromEmployee(), w.getToEmployee()))
                .collect(Collectors.toList());
        return wrapTransferRows(workflows);
    }

    /** Asset Location History: transfers where branch or location actually changed. */
    @Transactional(readOnly = true)
    public TransferReportData generateLocationHistoryReport(ReportScope scope, LocalDate fromDate, LocalDate toDate,
                                                              String category, String department) {
        List<com.example.fams.lifecycle.AssetLifecycleWorkflow> workflows = scopedTransferWorkflows(scope, category);
        if (hasValue(department, "Global Operations")) {
            workflows = workflows.stream()
                    .filter(w -> w.getAsset().getDepartment() != null && w.getAsset().getDepartment().equalsIgnoreCase(department))
                    .collect(Collectors.toList());
        }
        workflows = filterByRequestedDateRange(workflows, fromDate, toDate);
        workflows = workflows.stream()
                .filter(w -> !java.util.Objects.equals(w.getFromBranch(), w.getToBranch())
                        || !java.util.Objects.equals(w.getFromLocation(), w.getToLocation()))
                .collect(Collectors.toList());
        return wrapTransferRows(workflows);
    }

    /** Assets Pending Transfer Approval. */
    @Transactional(readOnly = true)
    public TransferReportData generatePendingTransferApprovalReport(ReportScope scope, String category, String department) {
        List<com.example.fams.lifecycle.AssetLifecycleWorkflow> workflows = scopedTransferWorkflows(scope, category);
        if (hasValue(department, "Global Operations")) {
            workflows = workflows.stream()
                    .filter(w -> w.getAsset().getDepartment() != null && w.getAsset().getDepartment().equalsIgnoreCase(department))
                    .collect(Collectors.toList());
        }
        workflows = workflows.stream()
                .filter(w -> w.getStatus() == com.example.fams.lifecycle.LifecycleWorkflowStatus.PENDING_APPROVAL)
                .collect(Collectors.toList());
        return wrapTransferRows(workflows);
    }

    /** Asset Movement History: full chronological lifecycle event trail. */
    @Transactional(readOnly = true)
    public MovementHistoryReportData generateAssetMovementHistoryReport(ReportScope scope, LocalDate fromDate, LocalDate toDate,
                                                                          String category, String department) {
        List<Asset> assets = scopedAssets(scope);
        if (hasValue(category, "All Categories")) {
            assets = assets.stream().filter(a -> a.getCategory() != null && a.getCategory().equalsIgnoreCase(category)).collect(Collectors.toList());
        }
        if (hasValue(department, "Global Operations")) {
            assets = assets.stream().filter(a -> a.getDepartment() != null && a.getDepartment().equalsIgnoreCase(department)).collect(Collectors.toList());
        }

        List<com.example.fams.lifecycle.AssetLifecycleHistory> history = lifecycleHistoryRepository.findByAssetInOrderByEventAtDesc(assets);
        if (fromDate != null) {
            history = history.stream().filter(h -> h.getEventAt() != null && !h.getEventAt().toLocalDate().isBefore(fromDate)).collect(Collectors.toList());
        }
        if (toDate != null) {
            history = history.stream().filter(h -> h.getEventAt() != null && !h.getEventAt().toLocalDate().isAfter(toDate)).collect(Collectors.toList());
        }

        MovementHistoryReportData data = new MovementHistoryReportData();
        data.setGeneratedDate(LocalDate.now());
        data.setRows(history.stream()
                .map(h -> new MovementHistoryRow(h.getAsset().getAssetCode(), h.getAsset().getName(),
                        h.getEventType() == null ? null : h.getEventType().name(), h.getEventAt(), h.getActor(),
                        h.getTitle(), h.getFromValue(), h.getToValue()))
                .collect(Collectors.toList()));
        return data;
    }

    // Disposal and retirement reports

    private List<com.example.fams.lifecycle.AssetLifecycleWorkflow> scopedDisposalWorkflows(ReportScope scope, String category, String department) {
        java.util.Set<Long> visibleAssetIds = scopedAssets(scope).stream().map(Asset::getId).collect(Collectors.toSet());
        List<com.example.fams.lifecycle.AssetLifecycleWorkflow> workflows = assetLifecycleService.findAllWorkflows().stream()
                .filter(w -> w.getType() == com.example.fams.lifecycle.LifecycleWorkflowType.DISPOSAL)
                .filter(w -> visibleAssetIds.contains(w.getAsset().getId()))
                .collect(Collectors.toList());
        if (hasValue(category, "All Categories")) {
            workflows = workflows.stream()
                    .filter(w -> w.getAsset().getCategory() != null && w.getAsset().getCategory().equalsIgnoreCase(category))
                    .collect(Collectors.toList());
        }
        if (hasValue(department, "Global Operations")) {
            workflows = workflows.stream()
                    .filter(w -> w.getAsset().getDepartment() != null && w.getAsset().getDepartment().equalsIgnoreCase(department))
                    .collect(Collectors.toList());
        }
        return workflows;
    }

    private DisposalRow toDisposalRow(com.example.fams.lifecycle.AssetLifecycleWorkflow w) {
        return new DisposalRow(
                w.getRequestedEffectiveDate(), w.getAsset().getAssetCode(), w.getAsset().getName(),
                w.getAsset().getCategory(), w.getAsset().getDepartment(),
                w.getStatus() == null ? null : w.getStatus().name(), w.getDisposalMethod(),
                w.getDisposalProceeds(), w.getAccumulatedDepreciation(), w.getNetBookValue(),
                w.getFinancialImpact(), w.getReason(), w.getRequestedBy()
        );
    }

    private DisposalReportData wrapDisposalRows(List<com.example.fams.lifecycle.AssetLifecycleWorkflow> workflows) {
        List<DisposalRow> rows = workflows.stream().map(this::toDisposalRow).collect(Collectors.toList());
        DisposalReportData data = new DisposalReportData();
        data.setGeneratedDate(LocalDate.now());
        data.setRows(rows);
        data.setTotalProceeds(rows.stream().map(DisposalRow::disposalProceeds).filter(v -> v != null).reduce(BigDecimal.ZERO, BigDecimal::add));
        data.setTotalGain(rows.stream().map(DisposalRow::financialImpact).filter(v -> v != null && v.compareTo(BigDecimal.ZERO) > 0).reduce(BigDecimal.ZERO, BigDecimal::add));
        data.setTotalLoss(rows.stream().map(DisposalRow::financialImpact).filter(v -> v != null && v.compareTo(BigDecimal.ZERO) < 0).reduce(BigDecimal.ZERO, BigDecimal::add));
        data.setNetGainLoss(rows.stream().map(DisposalRow::financialImpact).filter(v -> v != null).reduce(BigDecimal.ZERO, BigDecimal::add));
        return data;
    }

    /**
     * Disposal register, detail, by-period, by-category, sales, scrapped, proceeds,
     * gain/loss and retirement reports — one method serves all of these; they
     * differ only in which workflows are selected. {@code methods}, when non-empty,
     * restricts to those {@code disposalMethod} values (case-insensitive) — used for
     * Asset Sales ["Sale"], Scrapped Assets ["Scrap"], and Asset Retirement
     * ["Write-off", "Scrap"] (retirement has no distinct concept in this system; see
     * class-level note in ReportController's disposal endpoints for that interpretation).
     * The register, detail, by-period, by-category, proceeds and gain/loss reports pass an
     * empty/null {@code methods} list — same data, different framing.
     */
    @Transactional(readOnly = true)
    public DisposalReportData generateDisposalReport(ReportScope scope, LocalDate fromDate, LocalDate toDate,
                                                       String category, String department, List<String> methods) {
        List<com.example.fams.lifecycle.AssetLifecycleWorkflow> workflows = scopedDisposalWorkflows(scope, category, department);
        workflows = workflows.stream()
                .filter(w -> w.getStatus() == com.example.fams.lifecycle.LifecycleWorkflowStatus.COMPLETED)
                .collect(Collectors.toList());
        if (fromDate != null) {
            workflows = workflows.stream()
                    .filter(w -> w.getRequestedEffectiveDate() != null && !w.getRequestedEffectiveDate().isBefore(fromDate))
                    .collect(Collectors.toList());
        }
        if (toDate != null) {
            workflows = workflows.stream()
                    .filter(w -> w.getRequestedEffectiveDate() != null && !w.getRequestedEffectiveDate().isAfter(toDate))
                    .collect(Collectors.toList());
        }
        if (methods != null && !methods.isEmpty()) {
            java.util.Set<String> methodSet = methods.stream().map(String::toLowerCase).collect(Collectors.toSet());
            workflows = workflows.stream()
                    .filter(w -> w.getDisposalMethod() != null && methodSet.contains(w.getDisposalMethod().toLowerCase()))
                    .collect(Collectors.toList());
        }
        return wrapDisposalRows(workflows);
    }

    /** Assets Pending Disposal: disposal workflows not yet completed. */
    @Transactional(readOnly = true)
    public DisposalReportData generatePendingDisposalReport(ReportScope scope, String category, String department) {
        List<com.example.fams.lifecycle.AssetLifecycleWorkflow> workflows = scopedDisposalWorkflows(scope, category, department);
        workflows = workflows.stream()
                .filter(w -> w.getStatus() == com.example.fams.lifecycle.LifecycleWorkflowStatus.DRAFT
                        || w.getStatus() == com.example.fams.lifecycle.LifecycleWorkflowStatus.PENDING_APPROVAL)
                .collect(Collectors.toList());
        return wrapDisposalRows(workflows);
    }

    /** Disposal Approval History: approval actions on disposal workflows. */
    @Transactional(readOnly = true)
    public ApprovalHistoryReportData generateDisposalApprovalHistoryReport(ReportScope scope, String category, String department) {
        List<com.example.fams.lifecycle.AssetLifecycleWorkflow> workflows = scopedDisposalWorkflows(scope, category, department);
        List<com.example.fams.lifecycle.AssetLifecycleApprovalAction> actions =
                lifecycleApprovalActionRepository.findByWorkflowInOrderByActionAtAsc(workflows);

        ApprovalHistoryReportData data = new ApprovalHistoryReportData();
        data.setGeneratedDate(LocalDate.now());
        data.setRows(actions.stream()
                .map(a -> new ApprovalHistoryRow(
                        a.getWorkflow().getAsset().getAssetCode(), a.getWorkflow().getAsset().getName(),
                        a.getTaskName(), a.getActor(), a.getDecision() == null ? null : a.getDecision().name(),
                        a.getComment(), a.getActionAt()))
                .collect(Collectors.toList()));
        return data;
    }

    /**
     * Net Book Value Report: cost, accumulated depreciation,
     * cumulative net impairment, and NBV. See {@link NbvRow} javadoc for the scope note
     * on why revaluation isn't folded in here.
     */
    @Cacheable(cacheNames = "reports",
            key = "'netBookValue|' + #scope.cacheKey() + '|' + #category + '|' + #department")
    @Transactional(readOnly = true)
    public NbvReportData generateNetBookValueReport(ReportScope scope, String category, String department) {
        List<Asset> assets = scopedAssets(scope);
        if (hasValue(category, "All Categories")) {
            assets = assets.stream().filter(a -> a.getCategory() != null && a.getCategory().equalsIgnoreCase(category)).collect(Collectors.toList());
        }
        if (hasValue(department, "Global Operations")) {
            assets = assets.stream().filter(a -> a.getDepartment() != null && a.getDepartment().equalsIgnoreCase(department)).collect(Collectors.toList());
        }

        List<Long> assetIds = assets.stream().map(Asset::getId).collect(Collectors.toList());
        Map<Long, DepreciationPosting> latestPostings = depreciationService.getLatestPostingsForAssets(assetIds);
        Map<Long, BigDecimal> cumulativeImpairment = assetImpairmentService.getCumulativeImpairmentForAssets(assetIds);

        List<NbvRow> rows = assets.stream()
                .map(a -> {
                    BigDecimal cost = a.getPurchaseCost() == null ? BigDecimal.ZERO : a.getPurchaseCost();
                    DepreciationPosting posting = latestPostings.get(a.getId());
                    BigDecimal accumulated = posting != null && posting.getClosingAccumulatedDepreciation() != null
                            ? posting.getClosingAccumulatedDepreciation() : BigDecimal.ZERO;
                    BigDecimal impairment = cumulativeImpairment.getOrDefault(a.getId(), BigDecimal.ZERO);
                    BigDecimal nbv = cost.subtract(accumulated).subtract(impairment);
                    return new NbvRow(a.getAssetCode(), a.getName(), a.getCategory(), cost, accumulated, impairment, nbv);
                })
                .collect(Collectors.toList());

        NbvReportData data = new NbvReportData();
        data.setGeneratedDate(LocalDate.now());
        data.setRows(rows);
        data.setTotalHistoricalCost(rows.stream().map(NbvRow::historicalCost).filter(v -> v != null).reduce(BigDecimal.ZERO, BigDecimal::add));
        data.setTotalAccumulatedDepreciation(rows.stream().map(NbvRow::accumulatedDepreciation).filter(v -> v != null).reduce(BigDecimal.ZERO, BigDecimal::add));
        data.setTotalImpairment(rows.stream().map(NbvRow::impairment).filter(v -> v != null).reduce(BigDecimal.ZERO, BigDecimal::add));
        data.setTotalNetBookValue(rows.stream().map(NbvRow::netBookValue).filter(v -> v != null).reduce(BigDecimal.ZERO, BigDecimal::add));
        return data;
    }

    /**
     * Asset Ageing and Value Report: age (from purchaseDate) bucketed
     * alongside current cost/NBV.
     */
    @Cacheable(cacheNames = "reports",
            key = "'assetAgeing|' + #scope.cacheKey() + '|' + #category + '|' + #department")
    @Transactional(readOnly = true)
    public AgeingReportData generateAssetAgeingReport(ReportScope scope, String category, String department) {
        List<Asset> assets = scopedAssets(scope);
        if (hasValue(category, "All Categories")) {
            assets = assets.stream().filter(a -> a.getCategory() != null && a.getCategory().equalsIgnoreCase(category)).collect(Collectors.toList());
        }
        if (hasValue(department, "Global Operations")) {
            assets = assets.stream().filter(a -> a.getDepartment() != null && a.getDepartment().equalsIgnoreCase(department)).collect(Collectors.toList());
        }

        List<Long> assetIds = assets.stream().map(Asset::getId).collect(Collectors.toList());
        Map<Long, DepreciationPosting> latestPostings = depreciationService.getLatestPostingsForAssets(assetIds);

        List<AgeingRow> rows = assets.stream()
                .map(a -> {
                    BigDecimal cost = a.getPurchaseCost() == null ? BigDecimal.ZERO : a.getPurchaseCost();
                    DepreciationPosting posting = latestPostings.get(a.getId());
                    BigDecimal nbv = posting != null && posting.getBookValue() != null ? posting.getBookValue() : cost;
                    int ageYears = a.getPurchaseDate() != null
                            ? java.time.Period.between(a.getPurchaseDate(), LocalDate.now()).getYears() : 0;
                    String band = a.getPurchaseDate() == null ? "Unknown"
                            : ageYears < 1 ? "0-1 years"
                            : ageYears < 3 ? "1-3 years"
                            : ageYears < 5 ? "3-5 years"
                            : "5+ years";
                    return new AgeingRow(a.getAssetCode(), a.getName(), a.getCategory(), a.getPurchaseDate(), ageYears, band, cost, nbv);
                })
                .sorted((a, b) -> b.ageYears() - a.ageYears())
                .collect(Collectors.toList());

        AgeingReportData data = new AgeingReportData();
        data.setGeneratedDate(LocalDate.now());
        data.setRows(rows);
        return data;
    }

    /**
     * Revaluation Report / History / Gain-Loss Report. One method
     * serves all three; they differ only in UI framing, not underlying data — see
     * {@link com.example.fams.valuation.AssetRevaluation} javadoc.
     */
    @Cacheable(cacheNames = "reports",
            key = "'revaluations|' + #scope.cacheKey() + '|' + #fromDate + '|' + #toDate + '|' + #category + '|' + #department")
    @Transactional(readOnly = true)
    public RevaluationReportData generateRevaluationReport(ReportScope scope, LocalDate fromDate, LocalDate toDate,
                                                             String category, String department) {
        java.util.Set<Long> visibleAssetIds = scopedAssets(scope).stream().map(Asset::getId).collect(Collectors.toSet());

        List<com.example.fams.valuation.AssetRevaluation> revaluations = assetRevaluationService.findByDateRange(fromDate, toDate).stream()
                .filter(r -> visibleAssetIds.contains(r.getAsset().getId()))
                .collect(Collectors.toList());

        if (hasValue(category, "All Categories")) {
            revaluations = revaluations.stream()
                    .filter(r -> r.getAsset().getCategory() != null && r.getAsset().getCategory().equalsIgnoreCase(category))
                    .collect(Collectors.toList());
        }
        if (hasValue(department, "Global Operations")) {
            revaluations = revaluations.stream()
                    .filter(r -> r.getAsset().getDepartment() != null && r.getAsset().getDepartment().equalsIgnoreCase(department))
                    .collect(Collectors.toList());
        }

        List<RevaluationRow> rows = revaluations.stream()
                .map(r -> new RevaluationRow(r.getDate(), r.getAsset().getAssetCode(), r.getAsset().getName(),
                        r.getAsset().getCategory(), r.getAsset().getDepartment(), r.getPreviousCarryingAmount(),
                        r.getRevaluedAmount(), r.getGainLoss(), r.getReason(), r.getRecordedBy()))
                .collect(Collectors.toList());

        RevaluationReportData data = new RevaluationReportData();
        data.setGeneratedDate(LocalDate.now());
        data.setRows(rows);
        data.setTotalGain(rows.stream().map(RevaluationRow::gainLoss).filter(v -> v != null && v.compareTo(BigDecimal.ZERO) > 0).reduce(BigDecimal.ZERO, BigDecimal::add));
        data.setTotalLoss(rows.stream().map(RevaluationRow::gainLoss).filter(v -> v != null && v.compareTo(BigDecimal.ZERO) < 0).reduce(BigDecimal.ZERO, BigDecimal::add));
        data.setNetMovement(rows.stream().map(RevaluationRow::gainLoss).filter(v -> v != null).reduce(BigDecimal.ZERO, BigDecimal::add));
        return data;
    }

    /**
     * Impairment Report / History. One method serves both.
     */
    @Cacheable(cacheNames = "reports",
            key = "'impairments|' + #scope.cacheKey() + '|' + #fromDate + '|' + #toDate + '|' + #category + '|' + #department")
    @Transactional(readOnly = true)
    public ImpairmentReportData generateImpairmentReport(ReportScope scope, LocalDate fromDate, LocalDate toDate,
                                                           String category, String department) {
        java.util.Set<Long> visibleAssetIds = scopedAssets(scope).stream().map(Asset::getId).collect(Collectors.toSet());

        List<com.example.fams.valuation.AssetImpairment> impairments = assetImpairmentService.findByDateRange(fromDate, toDate).stream()
                .filter(i -> visibleAssetIds.contains(i.getAsset().getId()))
                .collect(Collectors.toList());

        if (hasValue(category, "All Categories")) {
            impairments = impairments.stream()
                    .filter(i -> i.getAsset().getCategory() != null && i.getAsset().getCategory().equalsIgnoreCase(category))
                    .collect(Collectors.toList());
        }
        if (hasValue(department, "Global Operations")) {
            impairments = impairments.stream()
                    .filter(i -> i.getAsset().getDepartment() != null && i.getAsset().getDepartment().equalsIgnoreCase(department))
                    .collect(Collectors.toList());
        }

        List<ImpairmentRow> rows = impairments.stream()
                .map(i -> new ImpairmentRow(i.getDate(), i.getAsset().getAssetCode(), i.getAsset().getName(),
                        i.getAsset().getCategory(), i.getAsset().getDepartment(),
                        i.getType() == null ? null : i.getType().name(), i.getAmount(),
                        i.getCarryingAmountBefore(), i.getCarryingAmountAfter(), i.getReason(), i.getRecordedBy()))
                .collect(Collectors.toList());

        ImpairmentReportData data = new ImpairmentReportData();
        data.setGeneratedDate(LocalDate.now());
        data.setRows(rows);
        data.setTotalImpairmentLoss(impairments.stream()
                .filter(i -> i.getType() == com.example.fams.valuation.ImpairmentType.IMPAIRMENT_LOSS)
                .map(com.example.fams.valuation.AssetImpairment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
        data.setTotalReversal(impairments.stream()
                .filter(i -> i.getType() == com.example.fams.valuation.ImpairmentType.REVERSAL)
                .map(com.example.fams.valuation.AssetImpairment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add));
        data.setNetImpairment(data.getTotalImpairmentLoss().subtract(data.getTotalReversal()));
        return data;
    }

    /**
     * Depreciation Run Report: history of depreciation batch runs.
     * Deliberately NOT filtered by {@link ReportScope} — a run touches all assets
     * company-wide by design, so which runs happened is an operational/audit fact about
     * the depreciation process itself, not asset data a department head's visibility
     * should restrict. If that's wrong for your organization, this is the one depreciation
     * report that would need scope filtering added.
     */
    @Transactional(readOnly = true)
    public List<com.example.fams.depreciation.DepreciationRun> generateDepreciationRunReport() {
        return depreciationService.getRunHistory();
    }

    /**
     * Depreciation Schedule:
     * cost, residual value, useful life, method, current charge and carrying amount per
     * asset, using the same bulk parameter-resolution as the Non-Depreciable report so
     * this stays a small, fixed number of queries regardless of asset count.
     */
    @Cacheable(cacheNames = "reports",
            key = "'depreciationSchedule|' + #scope.cacheKey() + '|' + #category + '|' + #department")
    @Transactional(readOnly = true)
    public DepreciationScheduleData generateDepreciationScheduleReport(ReportScope scope, String category, String department) {
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
        Map<Long, com.example.fams.depreciation.DepreciationParameters> paramsByAsset = depreciationService.resolveParametersForAssets(assets);

        List<DepreciationScheduleRow> rows = assets.stream()
                .map(a -> {
                    com.example.fams.depreciation.DepreciationParameters params = paramsByAsset.get(a.getId());
                    DepreciationPosting posting = latestPostings.get(a.getId());
                    BigDecimal cost = a.getPurchaseCost() == null ? BigDecimal.ZERO : a.getPurchaseCost();
                    BigDecimal accumulated = posting != null && posting.getClosingAccumulatedDepreciation() != null
                            ? posting.getClosingAccumulatedDepreciation() : BigDecimal.ZERO;
                    BigDecimal charge = posting != null && posting.getDepreciationCharge() != null
                            ? posting.getDepreciationCharge() : BigDecimal.ZERO;
                    BigDecimal carrying = posting != null && posting.getBookValue() != null ? posting.getBookValue() : cost;
                    return new DepreciationScheduleRow(
                            a.getAssetCode(), a.getName(), a.getCategory(), cost,
                            params != null ? params.getResidualValue() : null,
                            params != null ? params.getUsefulLifeYears() : null,
                            params != null ? params.getMethod().name() : "Not configured",
                            charge, accumulated, carrying, a.getStatus());
                })
                .collect(Collectors.toList());

        DepreciationScheduleData data = new DepreciationScheduleData();
        data.setGeneratedDate(LocalDate.now());
        data.setRows(rows);
        data.setTotalCost(rows.stream().map(DepreciationScheduleRow::cost).filter(v -> v != null).reduce(BigDecimal.ZERO, BigDecimal::add));
        data.setTotalAccumulatedDepreciation(rows.stream().map(DepreciationScheduleRow::accumulatedDepreciation).filter(v -> v != null).reduce(BigDecimal.ZERO, BigDecimal::add));
        data.setTotalCarryingAmount(rows.stream().map(DepreciationScheduleRow::carryingAmount).filter(v -> v != null).reduce(BigDecimal.ZERO, BigDecimal::add));
        return data;
    }

    /**
     * Accumulated Depreciation Report: current accumulated
     * depreciation balance per asset (same underlying data as the register, framed as
     * its own report).
     */
    @Cacheable(cacheNames = "reports",
            key = "'accumulatedDepreciation|' + #scope.cacheKey() + '|' + #category + '|' + #department")
    @Transactional(readOnly = true)
    public ReportData generateAccumulatedDepreciationReport(ReportScope scope, String category, String department) {
        ReportData report = new ReportData();
        report.setReportType("accumulatedDepreciation");
        report.setReportName("Accumulated Depreciation Report");
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
        return finish(report, assets);
    }

    /**
     * Depreciation by Method: assets grouped by their resolved
     * depreciation method. Reuses {@link AssetGroup}/{@link GroupedReportData} (same
     * shape as the grouped asset register reports) even though method isn't a
     * plain Asset field — it's resolved via {@link DepreciationService#resolveParametersForAssets}.
     */
    @Cacheable(cacheNames = "reports",
            key = "'depreciationByMethod|' + #scope.cacheKey() + '|' + #category + '|' + #department")
    @Transactional(readOnly = true)
    public GroupedReportData generateDepreciationByMethodReport(ReportScope scope, String category, String department) {
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
        Map<Long, com.example.fams.depreciation.DepreciationParameters> paramsByAsset = depreciationService.resolveParametersForAssets(assets);

        Map<String, List<Asset>> byMethod = assets.stream()
                .collect(Collectors.groupingBy(a -> {
                    com.example.fams.depreciation.DepreciationParameters p = paramsByAsset.get(a.getId());
                    return p != null ? p.getMethod().name() : "Not configured";
                }, java.util.LinkedHashMap::new, Collectors.toList()));

        List<AssetGroup> groups = byMethod.entrySet().stream()
                .map(e -> {
                    List<ReportRow> rows = e.getValue().stream()
                            .map(a -> assetToReportRow(a, latestPostings.get(a.getId())))
                            .collect(Collectors.toList());
                    BigDecimal cost = rows.stream().map(ReportRow::acquisitionCost).filter(v -> v != null).reduce(BigDecimal.ZERO, BigDecimal::add);
                    BigDecimal dep = rows.stream().map(ReportRow::accumulatedDepreciation).filter(v -> v != null).reduce(BigDecimal.ZERO, BigDecimal::add);
                    BigDecimal nbv = rows.stream().map(ReportRow::netBookValue).filter(v -> v != null).reduce(BigDecimal.ZERO, BigDecimal::add);
                    return new AssetGroup(e.getKey(), rows.size(), cost, dep, nbv, rows);
                })
                .sorted((a, b) -> a.groupKey().compareToIgnoreCase(b.groupKey()))
                .collect(Collectors.toList());

        GroupedReportData report = new GroupedReportData();
        report.setReportName("Depreciation by Method");
        report.setGeneratedDate(LocalDate.now());
        report.setGroups(groups);
        report.setTotalRecords(assets.size());
        report.setTotalValue(groups.stream().map(AssetGroup::totalCost).reduce(BigDecimal.ZERO, BigDecimal::add));
        report.setTotalDepreciation(groups.stream().map(AssetGroup::totalAccumulatedDepreciation).reduce(BigDecimal.ZERO, BigDecimal::add));
        report.setTotalNetBookValue(groups.stream().map(AssetGroup::totalNetBookValue).reduce(BigDecimal.ZERO, BigDecimal::add));
        return report;
    }

    /**
     * Depreciation Exception Report: assets with no resolvable
     * depreciation parameters (same underlying signal as the Non-Depreciable register,
     * but framed here as an exception needing attention rather than an expected state
     * like land — the two reports serve different audiences even though they share logic).
     */
    @Cacheable(cacheNames = "reports",
            key = "'depreciationExceptions|' + #scope.cacheKey() + '|' + #category + '|' + #department")
    @Transactional(readOnly = true)
    public ExceptionReportData generateDepreciationExceptionReport(ReportScope scope, String category, String department) {
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

        Map<Long, com.example.fams.depreciation.DepreciationParameters> paramsByAsset = depreciationService.resolveParametersForAssets(assets);

        List<ExceptionRow> rows = assets.stream()
                .filter(a -> !paramsByAsset.containsKey(a.getId()))
                .map(a -> new ExceptionRow(a.getAssetCode(), a.getName(), a.getCategory(), a.getDepartment(), a.getStatus(),
                        "No active depreciation parameters resolved (no asset-specific or category-wide policy configured)"))
                .collect(Collectors.toList());

        ExceptionReportData data = new ExceptionReportData();
        data.setGeneratedDate(LocalDate.now());
        data.setRows(rows);
        return data;
    }

    /**
     * Depreciation History Report (all-assets view): every
     * historical posting across scoped assets, optionally within a date range. The
     * per-asset drill-down already exists at {@link DepreciationService#getDepreciationHistory}.
     */
    @Cacheable(cacheNames = "reports",
            key = "'depreciationHistory|' + #scope.cacheKey() + '|' + #fromDate + '|' + #toDate + '|' + #category + '|' + #department")
    @Transactional(readOnly = true)
    public HistoryReportData generateDepreciationHistoryReport(ReportScope scope, LocalDate fromDate, LocalDate toDate,
                                                                 String category, String department) {
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

        List<HistoryRow> rows = assets.stream()
                .flatMap(a -> depreciationService.getDepreciationHistory(a.getId()).stream())
                .filter(p -> {
                    if (fromDate == null && toDate == null) return true;
                    // depreciationPeriod is a string (e.g. "2026-03"); use createdAt as the
                    // reliable, always-comparable date for range filtering instead.
                    LocalDate postedOn = p.getCreatedAt() != null ? p.getCreatedAt().toLocalDate() : null;
                    if (postedOn == null) return true;
                    if (fromDate != null && postedOn.isBefore(fromDate)) return false;
                    if (toDate != null && postedOn.isAfter(toDate)) return false;
                    return true;
                })
                .map(p -> new HistoryRow(p.getAssetCode(), p.getAssetName(), p.getDepreciationPeriod(),
                        p.getDepreciationCharge(), p.getClosingAccumulatedDepreciation(), p.getBookValue(),
                        Boolean.TRUE.equals(p.getFullyDepreciated())))
                .collect(Collectors.toList());

        HistoryReportData data = new HistoryReportData();
        data.setGeneratedDate(LocalDate.now());
        data.setRows(rows);
        return data;
    }

    /**
     * Depreciation Projection Report: forecast of future
     * depreciation over a selected horizon (in years). See
     * {@link DepreciationService#projectDepreciation} for the projection method and its
     * documented assumptions.
     */
    @Cacheable(cacheNames = "reports",
            key = "'depreciationProjection|' + #scope.cacheKey() + '|' + #horizonYears + '|' + #category + '|' + #department")
    @Transactional(readOnly = true)
    public ProjectionReportData generateDepreciationProjectionReport(ReportScope scope, int horizonYears,
                                                                       String category, String department) {
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
        Map<Long, com.example.fams.depreciation.DepreciationParameters> paramsByAsset = depreciationService.resolveParametersForAssets(assets);

        List<ProjectionAssetRow> assetRows = assets.stream()
                .filter(a -> paramsByAsset.containsKey(a.getId()))
                .map(a -> new ProjectionAssetRow(a.getAssetCode(), a.getName(), a.getCategory(),
                        depreciationService.projectDepreciation(a, paramsByAsset.get(a.getId()), latestPostings.get(a.getId()), horizonYears)))
                .filter(row -> !row.points().isEmpty())
                .collect(Collectors.toList());

        ProjectionReportData data = new ProjectionReportData();
        data.setGeneratedDate(LocalDate.now());
        data.setHorizonYears(horizonYears);
        data.setAssets(assetRows);
        return data;
    }

    /**
     * Assets Approaching Full Depreciation: assets projected to
     * reach full depreciation within {@code thresholdYears}. Reuses the same projection
     * as the Depreciation Projection Report but only to determine whether the horizon is
     * reached, not to report the full trajectory.
     */
    @Cacheable(cacheNames = "reports",
            key = "'approachingFullDepreciation|' + #scope.cacheKey() + '|' + #thresholdYears + '|' + #category + '|' + #department")
    @Transactional(readOnly = true)
    public ReportData generateApproachingFullDepreciationReport(ReportScope scope, int thresholdYears,
                                                                  String category, String department) {
        ReportData report = new ReportData();
        report.setReportType("approachingFullDepreciation");
        report.setReportName("Assets Approaching Full Depreciation");
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

        List<Long> assetIds = assets.stream().map(Asset::getId).collect(Collectors.toList());
        Map<Long, DepreciationPosting> latestPostings = depreciationService.getLatestPostingsForAssets(assetIds);
        Map<Long, com.example.fams.depreciation.DepreciationParameters> paramsByAsset = depreciationService.resolveParametersForAssets(assets);

        List<Asset> approaching = assets.stream()
                .filter(a -> paramsByAsset.containsKey(a.getId()))
                .filter(a -> {
                    DepreciationPosting posting = latestPostings.get(a.getId());
                    if (posting != null && Boolean.TRUE.equals(posting.getFullyDepreciated())) {
                        return false; // already fully depreciated — belongs to the Fully Depreciated Assets report
                    }
                    List<DepreciationProjectionPoint> projection = depreciationService.projectDepreciation(
                            a, paramsByAsset.get(a.getId()), posting, thresholdYears);
                    // Reaches full depreciation within the horizon if the projection stopped
                    // early (fewer points than the horizon) — see projectDepreciation's loop.
                    return !projection.isEmpty() && projection.size() < thresholdYears;
                })
                .collect(Collectors.toList());

        return finish(report, approaching);
    }

    /**
     * Capitalisation Report: assets formally capitalised (as
     * distinct from merely acquired) within a reporting period, filtered on
     * {@code Asset.capitalisationDate}.
     */
    @Cacheable(cacheNames = "reports",
            key = "'capitalisation|' + #scope.cacheKey() + '|' + #fromDate + '|' + #toDate + '|' + #category + '|' + #department")
    @Transactional(readOnly = true)
    public ReportData generateCapitalisationReport(ReportScope scope, LocalDate fromDate, LocalDate toDate,
                                                     String category, String department) {
        ReportData report = new ReportData();
        report.setReportType("capitalisation");
        report.setReportName("Capitalisation Report");
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
        assets = assets.stream().filter(Asset::isCapitalised).collect(Collectors.toList());
        if (fromDate != null) {
            assets = assets.stream()
                    .filter(a -> a.getCapitalisationDate() != null && !a.getCapitalisationDate().isBefore(fromDate))
                    .collect(Collectors.toList());
        }
        if (toDate != null) {
            assets = assets.stream()
                    .filter(a -> a.getCapitalisationDate() != null && !a.getCapitalisationDate().isAfter(toDate))
                    .collect(Collectors.toList());
        }

        return finish(report, assets);
    }

    /**
     * Assets Awaiting Capitalisation: assets acquired and on the
     * register but not yet marked {@code capitalised}.
     */
    @Cacheable(cacheNames = "reports",
            key = "'awaitingCapitalisation|' + #scope.cacheKey() + '|' + #category + '|' + #department")
    @Transactional(readOnly = true)
    public ReportData generateAwaitingCapitalisationReport(ReportScope scope, String category, String department) {
        ReportData report = new ReportData();
        report.setReportType("awaitingCapitalisation");
        report.setReportName("Assets Awaiting Capitalisation");
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
        assets = assets.stream().filter(a -> !a.isCapitalised()).collect(Collectors.toList());

        return finish(report, assets);
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
     * <p><b>Interpretation note:</b> this filters on {@code createdAt} (when the record
     * was entered into FAMS), while the Asset Acquisition Report filters on
     * {@code purchaseDate} (when it was actually bought). The two coincide for assets
     * entered promptly but diverge for backlog/catch-up entries.
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

    /** Shared acquisition-period + category/department filter (acquisition, cost analysis, trend). */
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

    /** Assets by location/department. */
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

    /** Assets that are not in stock or assigned status. */
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