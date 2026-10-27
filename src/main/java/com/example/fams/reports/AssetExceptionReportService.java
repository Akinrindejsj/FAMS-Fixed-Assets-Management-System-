package com.example.fams.reports;

import com.example.fams.assets.Asset;
import com.example.fams.assets.AssetCheckout;
import com.example.fams.assets.AssetCheckoutRepository;
import com.example.fams.assets.AssetRepository;
import com.example.fams.audit.AssetCondition;
import com.example.fams.audit.AuditVerificationResult;
import com.example.fams.audit.AuditVerificationResultRepository;
import com.example.fams.audit.AuditVerificationStatus;
import com.example.fams.common.AppClock;
import com.example.fams.core.config.SecurityScopeService.ReportScope;
import com.example.fams.depreciation.DepreciationParameters;
import com.example.fams.depreciation.DepreciationPosting;
import com.example.fams.depreciation.DepreciationService;
import com.example.fams.depreciation.DepreciationValidation;
import com.example.fams.gl.GLAccountMapping;
import com.example.fams.gl.GLAccountMappingRepository;
import com.example.fams.lifecycle.AssetLifecycleHistory;
import com.example.fams.lifecycle.AssetLifecycleHistoryRepository;
import com.example.fams.maintenance.MaintenanceRecord;
import com.example.fams.maintenance.MaintenanceRecordRepository;
import com.example.fams.maintenance.MaintenanceStatus;
import com.example.fams.organization.BranchRepository;
import com.example.fams.organization.LocationRepository;
import com.example.fams.reports.TabularReport.Column;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Lifecycle and data-quality exception reports over the asset register: missing or
 * invalid master data, depreciation and ledger set-up gaps, assets past their useful life,
 * idle and dormant assets, replacement candidates, long maintenance, unverified assets,
 * duplicate identifiers and an overall data-quality summary.
 *
 * <p>"Active" means any status other than Disposed or Retired. Results follow the caller's
 * report scope, and can be narrowed by category and department. Thresholds (days or
 * percent) come from the {@code threshold} request value, with the defaults noted on each
 * report.
 */
@Service
@Transactional(readOnly = true)
public class AssetExceptionReportService {

    private static final Set<String> INACTIVE_STATUSES = Set.of("disposed", "retired");
    private static final Set<String> UNASSIGNED_STATUSES = Set.of("in stock", "available", "returned");
    private static final LocalDate MIN_DATE = LocalDate.of(1900, 1, 1);
    private static final LocalDate MAX_DATE = LocalDate.of(2999, 12, 31);

    private final AssetRepository assetRepository;
    private final DepreciationService depreciationService;
    private final GLAccountMappingRepository mappingRepository;
    private final BranchRepository branchRepository;
    private final LocationRepository locationRepository;
    private final AssetCheckoutRepository checkoutRepository;
    private final AssetLifecycleHistoryRepository historyRepository;
    private final MaintenanceRecordRepository maintenanceRepository;
    private final AuditVerificationResultRepository verificationRepository;

    public AssetExceptionReportService(AssetRepository assetRepository,
                                       DepreciationService depreciationService,
                                       GLAccountMappingRepository mappingRepository,
                                       BranchRepository branchRepository,
                                       LocationRepository locationRepository,
                                       AssetCheckoutRepository checkoutRepository,
                                       AssetLifecycleHistoryRepository historyRepository,
                                       MaintenanceRecordRepository maintenanceRepository,
                                       AuditVerificationResultRepository verificationRepository) {
        this.assetRepository = assetRepository;
        this.depreciationService = depreciationService;
        this.mappingRepository = mappingRepository;
        this.branchRepository = branchRepository;
        this.locationRepository = locationRepository;
        this.checkoutRepository = checkoutRepository;
        this.historyRepository = historyRepository;
        this.maintenanceRepository = maintenanceRepository;
        this.verificationRepository = verificationRepository;
    }

    // ------------------------------------------------------------------ helpers

    private static final Map<String, Column> COLUMNS = new LinkedHashMap<>();

    private static void col(String key, String label, String type) {
        COLUMNS.put(key, new Column(key, label, type));
    }

    static {
        col("assetCode", "Asset Code", "text");
        col("assetName", "Asset", "text");
        col("category", "Category", "text");
        col("department", "Department", "text");
        col("branch", "Location", "text");
        col("custodian", "Custodian", "text");
        col("status", "Status", "text");
        col("purchaseDate", "Purchase Date", "date");
        col("cost", "Cost", "money");
        col("issue", "Issue", "text");
    }

    private List<Column> columns(String... keys) {
        List<Column> list = new ArrayList<>();
        for (String key : keys) {
            list.add(COLUMNS.get(key));
        }
        return list;
    }

    private String nz(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    private boolean active(Asset asset) {
        return asset.getStatus() == null || !INACTIVE_STATUSES.contains(asset.getStatus().trim().toLowerCase());
    }

    private boolean unassigned(Asset asset) {
        return asset.getStatus() != null && UNASSIGNED_STATUSES.contains(asset.getStatus().trim().toLowerCase());
    }

    private Map<String, Object> row(Object... keyValues) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            row.put((String) keyValues[i], keyValues[i + 1]);
        }
        return row;
    }

    private Map<String, Object> summary(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], String.valueOf(keyValues[i + 1]));
        }
        return map;
    }

    private TabularReport table(String title, String description, List<Column> columns, List<Map<String, Object>> rows,
                                Map<String, Object> summary, List<String> notes) {
        return new TabularReport(title, description, AppClock.today(), columns, rows, Map.<String, Object>of(),
                summary == null ? Map.<String, Object>of() : summary,
                notes == null ? List.<String>of() : notes, rows.isEmpty());
    }

    /** The common asset columns for a row, plus an optional issue description. */
    private Map<String, Object> assetRow(Asset a, String issue) {
        Map<String, Object> row = row("assetCode", a.getAssetCode(), "assetName", a.getName(), "category", nz(a.getCategory()),
                "department", nz(a.getDepartment()), "branch", nz(a.getBranch()), "custodian", nz(a.getCustodian()),
                "status", nz(a.getStatus()), "purchaseDate", a.getPurchaseDate() == null ? "" : a.getPurchaseDate().toString(),
                "cost", money(a.getPurchaseCost()));
        if (issue != null) {
            row.put("issue", issue);
        }
        return row;
    }

    private static final String[] BASE = {"assetCode", "assetName", "category", "department", "branch", "custodian", "status"};

    private List<Column> withExtras(Column... extras) {
        List<Column> cols = new ArrayList<>(columns(BASE));
        cols.addAll(List.of(extras));
        return cols;
    }

    /** Assets within the caller's scope, narrowed by the optional category and department filters. */
    private List<Asset> scopedAssets(ReportScope scope, String category, String department) {
        List<Asset> assets;
        if (scope == null || scope.isGlobal()) {
            assets = assetRepository.findAll();
        } else if (scope.isEmptyRestriction()) {
            assets = List.of();
        } else if (scope.isDepartment()) {
            assets = assetRepository.findByDepartmentInIgnoreCaseOrderByCreatedAtDesc(
                    scope.departments().stream().map(String::toLowerCase).collect(Collectors.toList()));
        } else {
            assets = assetRepository.findByCustodianInIgnoreCaseOrderByCreatedAtDesc(scope.custodians());
        }
        return assets.stream()
                .filter(a -> blank(category) || "All Categories".equalsIgnoreCase(category)
                        || (a.getCategory() != null && a.getCategory().equalsIgnoreCase(category.trim())))
                .filter(a -> blank(department) || "Global Operations".equalsIgnoreCase(department)
                        || (a.getDepartment() != null && a.getDepartment().equalsIgnoreCase(department.trim())))
                .filter(ReportFilters::matches)
                .sorted(Comparator.comparing((Asset a) -> nz(a.getAssetCode())))
                .toList();
    }

    private List<Asset> activeOf(List<Asset> assets) {
        return assets.stream().filter(this::active).toList();
    }

    private int thresholdOr(Integer threshold, int fallback) {
        return threshold != null && threshold > 0 ? threshold : fallback;
    }

    private double ageYears(Asset asset, LocalDate today) {
        LocalDate start = asset.getCapitalisationDate() != null ? asset.getCapitalisationDate() : asset.getPurchaseDate();
        if (start == null) return 0;
        return Math.max(0, ChronoUnit.DAYS.between(start, today) / 365.25);
    }

    private double oneDecimal(double value) {
        return Math.round(value * 10.0) / 10.0;
    }

    // ------------------------------------------------- master-data exceptions

    /** Assets Without Custodian: in-use active assets with nobody responsible. Stock held unassigned is not listed. */
    public TabularReport noCustodian(ReportScope scope, String category, String department) {
        List<Map<String, Object>> rows = activeOf(scopedAssets(scope, category, department)).stream()
                .filter(a -> blank(a.getCustodian()) && !unassigned(a))
                .map(a -> assetRow(a, null)).toList();
        return table("Assets Without Custodian", "Active assets in use that have no responsible custodian recorded.",
                withExtras(), rows, summary("Assets", rows.size()),
                List.of("Assets with status In Stock, Available or Returned are expected to be unassigned and are not listed."));
    }

    private Set<String> knownLocations() {
        Set<String> names = new HashSet<>();
        branchRepository.findAll().forEach(b -> {
            if (!blank(b.getName())) names.add(b.getName().trim().toLowerCase());
        });
        locationRepository.findAll().forEach(l -> {
            if (!blank(l.getName())) names.add(l.getName().trim().toLowerCase());
        });
        return names;
    }

    /** Assets Without Location: no location recorded, or one that matches no known branch/location. */
    public TabularReport noLocation(ReportScope scope, String category, String department) {
        Set<String> known = knownLocations();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Asset a : activeOf(scopedAssets(scope, category, department))) {
            if (blank(a.getBranch())) {
                rows.add(assetRow(a, "No location recorded"));
            } else if (!known.isEmpty() && !known.contains(a.getBranch().trim().toLowerCase())) {
                rows.add(assetRow(a, "Location '" + a.getBranch() + "' is not a known branch or location"));
            }
        }
        List<String> notes = known.isEmpty()
                ? List.of("No branches or locations are set up, so only blank locations can be detected.") : null;
        return table("Assets Without Location", "Active assets with no valid physical location.",
                withExtras(COLUMNS.get("issue")), rows, summary("Assets", rows.size()), notes);
    }

    /** Assets Without Serial Number: serial number blank; manufacturer/model gaps shown alongside. */
    public TabularReport noSerial(ReportScope scope, String category, String department) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Asset a : activeOf(scopedAssets(scope, category, department))) {
            if (!blank(a.getSerialNumber())) continue;
            List<String> missing = new ArrayList<>(List.of("Serial number"));
            if (blank(a.getManufacturer())) missing.add("Manufacturer");
            if (blank(a.getModel())) missing.add("Model");
            rows.add(assetRow(a, String.join(", ", missing)));
        }
        return table("Assets Without Serial Number", "Active assets missing a serial number (and other identification).",
                withExtras(new Column("issue", "Missing", "text")), rows, summary("Assets", rows.size()), null);
    }

    private Map<Long, DepreciationParameters> policies(List<Asset> assets) {
        return depreciationService.resolveParametersForAssets(assets);
    }

    /** Assets Without Depreciation Policy: no asset-specific or category-wide active depreciation parameters. */
    public TabularReport noDepreciationPolicy(ReportScope scope, String category, String department) {
        List<Asset> active = activeOf(scopedAssets(scope, category, department));
        Map<Long, DepreciationParameters> policies = policies(active);
        List<Map<String, Object>> rows = active.stream()
                .filter(a -> a.getPurchaseCost() != null && a.getPurchaseCost().signum() > 0)
                .filter(a -> !policies.containsKey(a.getId()))
                .map(a -> assetRow(a, "No active depreciation parameters for this asset or its category")).toList();
        return table("Assets Without Depreciation Policy", "Depreciable assets with no applicable depreciation rule.",
                withExtras(COLUMNS.get("cost"), COLUMNS.get("issue")), rows, summary("Assets", rows.size()),
                List.of("These assets are skipped by depreciation runs until a policy is configured."));
    }

    private String mappingGap(GLAccountMapping mapping) {
        if (mapping == null) return "No GL mapping for the category and no global default";
        List<String> missing = new ArrayList<>();
        if (mapping.getAssetCostAccount() == null) missing.add("asset cost");
        if (mapping.getAccumulatedDepreciationAccount() == null) missing.add("accumulated depreciation");
        if (mapping.getDepreciationExpenseAccount() == null) missing.add("depreciation expense");
        if (mapping.getCashPayablesAccount() == null) missing.add("cash/payables");
        return missing.isEmpty() ? null : "Mapping is missing: " + String.join(", ", missing);
    }

    /** Assets Without GL Mapping: category has no ledger mapping, or its mapping lacks a required account. */
    public TabularReport noGlMapping(ReportScope scope, String category, String department) {
        Map<String, GLAccountMapping> byCategory = new HashMap<>();
        GLAccountMapping fallback = null;
        for (GLAccountMapping m : mappingRepository.findAll()) {
            if (blank(m.getCategory())) {
                fallback = m;
            } else {
                byCategory.put(m.getCategory().trim().toLowerCase(), m);
            }
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Asset a : activeOf(scopedAssets(scope, category, department))) {
            GLAccountMapping mapping = a.getCategory() == null ? fallback
                    : byCategory.getOrDefault(a.getCategory().trim().toLowerCase(), fallback);
            String gap = mappingGap(mapping);
            if (gap != null) rows.add(assetRow(a, gap));
        }
        return table("Assets Without GL Mapping", "Assets whose category lacks the ledger mapping needed to post journals.",
                withExtras(COLUMNS.get("issue")), rows, summary("Assets", rows.size()),
                List.of("Journals for these assets are not generated; they appear in the Journal Exception Report when a transaction occurs."));
    }

    /** Assets Missing Acquisition Cost. */
    public TabularReport missingCost(ReportScope scope, String category, String department) {
        List<Map<String, Object>> rows = activeOf(scopedAssets(scope, category, department)).stream()
                .filter(a -> a.getPurchaseCost() == null || a.getPurchaseCost().signum() <= 0)
                .map(a -> assetRow(a, a.getPurchaseCost() == null ? "No cost recorded" : "Cost is zero or negative")).toList();
        return table("Assets Missing Acquisition Cost", "Assets without a valid acquisition or capitalised cost.",
                withExtras(COLUMNS.get("issue")), rows, summary("Assets", rows.size()), null);
    }

    /** Assets with Invalid Useful Life: a policy exists but its useful life is missing, zero, negative or over 100 years. */
    public TabularReport invalidUsefulLife(ReportScope scope, String category, String department) {
        List<Asset> active = activeOf(scopedAssets(scope, category, department));
        Map<Long, DepreciationParameters> policies = policies(active);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Asset a : active) {
            DepreciationParameters p = policies.get(a.getId());
            if (p == null) continue;
            Integer life = p.getUsefulLifeYears();
            if (life == null) {
                rows.add(assetRow(a, "Useful life is not set"));
            } else if (life <= 0) {
                rows.add(assetRow(a, "Useful life is " + life + " years"));
            } else if (life > 100) {
                rows.add(assetRow(a, "Useful life of " + life + " years exceeds 100"));
            }
        }
        return table("Assets with Invalid Useful Life", "Assets whose depreciation policy has an invalid useful life.",
                withExtras(COLUMNS.get("issue")), rows, summary("Assets", rows.size()),
                List.of("Assets with no policy at all are listed in Assets Without Depreciation Policy."));
    }

    // ----------------------------------------------- lifecycle exceptions

    /** Assets Beyond Useful Life: still active after their configured useful life has elapsed. */
    public TabularReport beyondUsefulLife(ReportScope scope, String category, String department) {
        LocalDate today = AppClock.today();
        List<Asset> active = activeOf(scopedAssets(scope, category, department));
        Map<Long, DepreciationParameters> policies = policies(active);
        Map<Long, DepreciationPosting> postings = depreciationService.getLatestPostingsForAssets(
                active.stream().map(Asset::getId).toList());
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Asset a : active) {
            DepreciationParameters p = policies.get(a.getId());
            if (p == null || p.getUsefulLifeYears() == null || p.getUsefulLifeYears() <= 0) continue;
            double age = ageYears(a, today);
            if (age <= p.getUsefulLifeYears()) continue;
            DepreciationPosting posting = postings.get(a.getId());
            Map<String, Object> r = assetRow(a, null);
            r.put("life", p.getUsefulLifeYears());
            r.put("age", oneDecimal(age));
            r.put("beyond", oneDecimal(age - p.getUsefulLifeYears()));
            r.put("nbv", money(posting == null ? a.getPurchaseCost() : posting.getBookValue()));
            rows.add(r);
        }
        rows.sort(Comparator.comparing((Map<String, Object> m) -> (Double) m.get("beyond")).reversed());
        return table("Assets Beyond Useful Life", "Active assets that have outlived their configured useful life.",
                withExtras(new Column("life", "Useful Life (yrs)", "number"), new Column("age", "Age (yrs)", "number"),
                        new Column("beyond", "Years Beyond", "number"), new Column("nbv", "Net Book Value", "money")),
                rows, summary("Assets", rows.size()),
                List.of("Age runs from the capitalisation date, or the purchase date if the asset was not capitalised."));
    }

    /** Fully Depreciated but Active Assets: latest depreciation posting shows the asset fully depreciated. */
    public TabularReport fullyDepreciatedActive(ReportScope scope, String category, String department) {
        List<Asset> active = activeOf(scopedAssets(scope, category, department));
        Map<Long, DepreciationPosting> postings = depreciationService.getLatestPostingsForAssets(
                active.stream().map(Asset::getId).toList());
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Asset a : active) {
            DepreciationPosting p = postings.get(a.getId());
            if (p == null || !Boolean.TRUE.equals(p.getFullyDepreciated())) continue;
            Map<String, Object> r = assetRow(a, null);
            r.put("period", nz(p.getDepreciationPeriod()));
            r.put("nbv", money(p.getBookValue()));
            rows.add(r);
        }
        return table("Fully Depreciated but Active Assets", "Operational assets that have completed depreciation.",
                withExtras(COLUMNS.get("cost"), new Column("period", "Fully Depreciated As Of", "text"),
                        new Column("nbv", "Net Book Value", "money")),
                rows, summary("Assets", rows.size()), null);
    }

    /** Last recorded activity per asset: checkouts, lifecycle events, maintenance, verifications and record edits. */
    private Map<Long, LocalDateTime> lastActivity(List<Asset> assets) {
        Map<Long, LocalDateTime> last = new HashMap<>();
        for (Asset a : assets) {
            LocalDateTime base = a.getUpdatedAt() != null ? a.getUpdatedAt() : a.getCreatedAt();
            if (base != null) last.put(a.getId(), base);
        }
        for (AssetCheckout c : checkoutRepository.findAll()) {
            LocalDateTime at = c.getCheckoutDate() != null ? c.getCheckoutDate().atStartOfDay() : c.getCreatedAt();
            if (c.getUpdatedAt() != null && (at == null || c.getUpdatedAt().isAfter(at))) at = c.getUpdatedAt();
            mergeLatest(last, c.getAsset(), at);
        }
        for (AssetLifecycleHistory h : historyRepository.findAll()) {
            mergeLatest(last, h.getAsset(), h.getEventAt());
        }
        for (MaintenanceRecord m : maintenanceRepository.findByMaintenanceDateBetweenOrderByMaintenanceDateDesc(MIN_DATE, MAX_DATE)) {
            mergeLatest(last, m.getAsset(), m.getMaintenanceDate() == null ? null : m.getMaintenanceDate().atStartOfDay());
        }
        for (AuditVerificationResult r : verificationRepository.findInRangeFetched(MIN_DATE.atStartOfDay(), MAX_DATE.atStartOfDay())) {
            mergeLatest(last, r.getAsset(), r.getVerifiedAt());
        }
        return last;
    }

    private void mergeLatest(Map<Long, LocalDateTime> last, Asset asset, LocalDateTime at) {
        if (asset == null || at == null) return;
        last.merge(asset.getId(), at, (x, y) -> x.isAfter(y) ? x : y);
    }

    private TabularReport inactivityReport(String title, String description, List<Asset> candidates, int days,
                                           List<String> notes) {
        LocalDate today = AppClock.today();
        Map<Long, LocalDateTime> last = lastActivity(candidates);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Asset a : candidates) {
            LocalDateTime at = last.get(a.getId());
            long idle = at == null ? Long.MAX_VALUE : ChronoUnit.DAYS.between(at.toLocalDate(), today);
            if (idle < days) continue;
            Map<String, Object> r = assetRow(a, null);
            r.put("lastActivity", at == null ? "No activity recorded" : at.toLocalDate().toString());
            r.put("days", at == null ? null : idle);
            rows.add(r);
        }
        rows.sort(Comparator.comparing((Map<String, Object> m) -> m.get("days") == null ? Long.MAX_VALUE : (Long) m.get("days")).reversed());
        return table(title, description,
                withExtras(new Column("lastActivity", "Last Activity", "date"), new Column("days", "Days Inactive", "number")),
                rows, summary("Assets", rows.size(), "Threshold (days)", days), notes);
    }

    /** Idle Assets: unassigned stock with no activity for at least {@code threshold} days (default 90). */
    public TabularReport idle(ReportScope scope, String category, String department, Integer threshold) {
        int days = thresholdOr(threshold, 90);
        List<Asset> stock = activeOf(scopedAssets(scope, category, department)).stream()
                .filter(a -> unassigned(a) && blank(a.getCustodian())).toList();
        return inactivityReport("Idle Assets", "Assets held unassigned with no recent use or movement.", stock, days,
                List.of("Idle means status In Stock, Available or Returned, no custodian, and no checkout, transfer, maintenance, "
                        + "verification or record edit for the threshold period."));
    }

    /** Dormant Assets: any active asset with no activity for at least {@code threshold} days (default 365). */
    public TabularReport dormant(ReportScope scope, String category, String department, Integer threshold) {
        int days = thresholdOr(threshold, 365);
        return inactivityReport("Dormant Assets", "Assets with no meaningful movement or activity over the period.",
                activeOf(scopedAssets(scope, category, department)), days,
                List.of("Activity is any checkout, transfer or lifecycle event, maintenance record, physical verification or edit to the asset record."));
    }

    /**
     * Assets Due for Replacement: active assets past their useful life, in poor condition at
     * their latest verification, or whose maintenance cost reaches {@code threshold} percent
     * of cost (default 50).
     */
    public TabularReport dueForReplacement(ReportScope scope, String category, String department, Integer threshold) {
        int percent = thresholdOr(threshold, 50);
        LocalDate today = AppClock.today();
        List<Asset> active = activeOf(scopedAssets(scope, category, department));
        Map<Long, DepreciationParameters> policies = policies(active);

        Map<Long, BigDecimal> maintenanceCost = new HashMap<>();
        for (MaintenanceRecord m : maintenanceRepository.findByMaintenanceDateBetweenOrderByMaintenanceDateDesc(MIN_DATE, MAX_DATE)) {
            if (m.getAsset() != null && m.getMaintenanceCost() != null) {
                maintenanceCost.merge(m.getAsset().getId(), m.getMaintenanceCost(), BigDecimal::add);
            }
        }
        Map<Long, AuditVerificationResult> latestVerification = new HashMap<>();
        for (AuditVerificationResult r : verificationRepository.findInRangeFetched(MIN_DATE.atStartOfDay(), MAX_DATE.atStartOfDay())) {
            latestVerification.putIfAbsent(r.getAsset().getId(), r); // newest first
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Asset a : active) {
            List<String> reasons = new ArrayList<>();
            DepreciationParameters p = policies.get(a.getId());
            double age = ageYears(a, today);
            if (p != null && p.getUsefulLifeYears() != null && p.getUsefulLifeYears() > 0 && age > p.getUsefulLifeYears()) {
                reasons.add("Past useful life");
            }
            AuditVerificationResult v = latestVerification.get(a.getId());
            if (v != null && (v.getConditionRating() == AssetCondition.POOR || v.getResultStatus() == AuditVerificationStatus.DAMAGED)) {
                reasons.add("Poor condition at last verification");
            }
            BigDecimal spent = maintenanceCost.getOrDefault(a.getId(), BigDecimal.ZERO);
            BigDecimal cost = a.getPurchaseCost() == null ? BigDecimal.ZERO : a.getPurchaseCost();
            double ratio = cost.signum() > 0 ? spent.doubleValue() * 100.0 / cost.doubleValue() : 0;
            if (cost.signum() > 0 && ratio >= percent) {
                reasons.add("Maintenance cost " + oneDecimal(ratio) + "% of cost");
            }
            if (reasons.isEmpty()) continue;
            Map<String, Object> r = assetRow(a, String.join("; ", reasons));
            r.put("age", oneDecimal(age));
            r.put("maintenance", money(spent));
            rows.add(r);
        }
        rows.sort(Comparator.comparing((Map<String, Object> m) -> String.valueOf(m.get("issue")).split(";").length).reversed());
        return table("Assets Due for Replacement", "Assets meeting age, condition or maintenance-cost replacement criteria.",
                withExtras(COLUMNS.get("cost"), new Column("age", "Age (yrs)", "number"),
                        new Column("maintenance", "Maintenance Cost", "money"), new Column("issue", "Reasons", "text")),
                rows, summary("Assets", rows.size(), "Maintenance cost threshold", percent + "% of cost"),
                List.of("Criteria: age beyond useful life; poor or damaged at the latest verification; or maintenance cost at or above the threshold share of cost."));
    }

    /** Assets Under Maintenance for Excessive Period: open maintenance older than {@code threshold} days (default 30). */
    public TabularReport excessiveMaintenance(ReportScope scope, String category, String department, Integer threshold) {
        int days = thresholdOr(threshold, 30);
        LocalDate today = AppClock.today();
        Set<Long> visible = scopedAssets(scope, category, department).stream().map(Asset::getId).collect(Collectors.toSet());
        List<Map<String, Object>> rows = new ArrayList<>();
        for (MaintenanceRecord m : maintenanceRepository.findByMaintenanceDateBetweenOrderByMaintenanceDateDesc(MIN_DATE, MAX_DATE)) {
            if (m.getAsset() == null || !visible.contains(m.getAsset().getId())) continue;
            if (m.getStatus() != MaintenanceStatus.OPEN && m.getStatus() != MaintenanceStatus.IN_PROGRESS) continue;
            LocalDate start = m.getDowntimeStart() != null ? m.getDowntimeStart() : m.getCreatedAt().toLocalDate();
            long open = ChronoUnit.DAYS.between(start, today);
            if (open < days) continue;
            Asset a = m.getAsset();
            rows.add(row("assetCode", a.getAssetCode(), "assetName", a.getName(), "category", nz(a.getCategory()),
                    "issue", nz(m.getIssueDescription()), "provider", nz(m.getServiceProvider()),
                    "since", start.toString(), "days", open, "status", m.getStatus().name()));
        }
        rows.sort(Comparator.comparing((Map<String, Object> r) -> (Long) r.get("days")).reversed());
        List<Column> cols = List.of(COLUMNS.get("assetCode"), COLUMNS.get("assetName"), COLUMNS.get("category"),
                new Column("issue", "Description", "text"), new Column("provider", "Service Provider", "text"),
                new Column("since", "Open Since", "date"), new Column("days", "Days Open", "number"),
                new Column("status", "Status", "text"));
        return table("Assets Under Maintenance for Excessive Period", "Assets whose maintenance has stayed open beyond the threshold.",
                cols, rows, summary("Assets", rows.size(), "Threshold (days)", days),
                List.of("Counted from the recorded unavailable-from date, or when the event was logged if none was entered."));
    }

    /** Assets Not Physically Verified: no FOUND verification within {@code threshold} days (default 365). */
    public TabularReport notVerified(ReportScope scope, String category, String department, Integer threshold) {
        int days = thresholdOr(threshold, 365);
        LocalDate today = AppClock.today();
        Map<Long, LocalDateTime> lastFound = new HashMap<>();
        for (AuditVerificationResult r : verificationRepository.findInRangeFetched(MIN_DATE.atStartOfDay(), MAX_DATE.atStartOfDay())) {
            if (r.getResultStatus() == AuditVerificationStatus.FOUND) {
                lastFound.merge(r.getAsset().getId(), r.getVerifiedAt(), (x, y) -> x.isAfter(y) ? x : y);
            }
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Asset a : activeOf(scopedAssets(scope, category, department))) {
            LocalDateTime at = lastFound.get(a.getId());
            long since = at == null ? Long.MAX_VALUE : ChronoUnit.DAYS.between(at.toLocalDate(), today);
            if (since < days) continue;
            Map<String, Object> r = assetRow(a, null);
            r.put("lastVerified", at == null ? "Never verified" : at.toLocalDate().toString());
            r.put("days", at == null ? null : since);
            rows.add(r);
        }
        rows.sort(Comparator.comparing((Map<String, Object> m) -> m.get("days") == null ? Long.MAX_VALUE : (Long) m.get("days")).reversed());
        return table("Assets Not Physically Verified", "Assets without a successful verification within the required interval.",
                withExtras(new Column("lastVerified", "Last Successful Verification", "date"), new Column("days", "Days Since", "number")),
                rows, summary("Assets", rows.size(), "Interval (days)", days),
                List.of("Only verifications where the asset was found count as successful."));
    }

    // -------------------------------------------------- data quality

    private String key(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private record DuplicateGroup(String type, String value, List<Asset> assets) {
    }

    private List<DuplicateGroup> duplicateGroups(List<Asset> assets) {
        List<DuplicateGroup> groups = new ArrayList<>();
        collectDuplicates(groups, "Serial number", assets, Asset::getSerialNumber);
        collectDuplicates(groups, "Asset code", assets, Asset::getAssetCode);
        collectDuplicates(groups, "Barcode value", assets, Asset::getBarcodeValue);
        return groups;
    }

    private void collectDuplicates(List<DuplicateGroup> groups, String type, List<Asset> assets,
                                   java.util.function.Function<Asset, String> identifier) {
        Map<String, List<Asset>> byValue = new LinkedHashMap<>();
        for (Asset a : assets) {
            String id = key(identifier.apply(a));
            if (!id.isEmpty()) byValue.computeIfAbsent(id, k -> new ArrayList<>()).add(a);
        }
        byValue.forEach((value, list) -> {
            if (list.size() > 1) groups.add(new DuplicateGroup(type, identifier.apply(list.get(0)).trim(), list));
        });
    }

    /** Duplicate Asset/Serial Number Report: identifiers shared by more than one asset. */
    public TabularReport duplicates(ReportScope scope, String category, String department) {
        List<Asset> all = scopedAssets(scope, category, department);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (DuplicateGroup g : duplicateGroups(all)) {
            rows.add(row("type", g.type(), "value", g.value(), "count", g.assets().size(),
                    "assets", g.assets().stream().map(a -> nz(a.getAssetCode()) + " (" + nz(a.getStatus()) + ")")
                            .collect(Collectors.joining(", "))));
        }
        List<Column> cols = List.of(new Column("type", "Identifier", "text"), new Column("value", "Value", "text"),
                new Column("count", "Assets Sharing It", "number"), new Column("assets", "Assets", "text"));
        return table("Duplicate Asset / Serial Number Report", "Asset codes, serial numbers and barcodes shared by more than one asset.",
                cols, rows, summary("Duplicate groups", rows.size()),
                List.of("Comparison ignores case and surrounding spaces. Disposed and retired assets are included because their identifiers can still clash."));
    }

    private static final String[] CORE_FIELDS = {"Category", "Purchase date", "Purchase cost", "Department", "Location", "Cost centre"};

    private List<String> missingCore(Asset a) {
        List<String> missing = new ArrayList<>();
        if (blank(a.getCategory())) missing.add("Category");
        if (a.getPurchaseDate() == null) missing.add("Purchase date");
        if (a.getPurchaseCost() == null || a.getPurchaseCost().signum() <= 0) missing.add("Purchase cost");
        if (blank(a.getDepartment())) missing.add("Department");
        if (blank(a.getBranch())) missing.add("Location");
        if (blank(a.getCostCentre())) missing.add("Cost centre");
        return missing;
    }

    private List<String> missingRecommended(Asset a) {
        List<String> missing = new ArrayList<>();
        if (blank(a.getSerialNumber())) missing.add("Serial number");
        if (blank(a.getManufacturer())) missing.add("Manufacturer");
        if (blank(a.getModel())) missing.add("Model");
        if (blank(a.getVendor())) missing.add("Vendor");
        return missing;
    }

    /** Incomplete Asset Records: assets missing mandatory master-data fields. */
    public TabularReport incompleteRecords(ReportScope scope, String category, String department) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Asset a : activeOf(scopedAssets(scope, category, department))) {
            List<String> core = missingCore(a);
            if (core.isEmpty()) continue;
            Map<String, Object> r = assetRow(a, String.join(", ", core));
            r.put("missingCount", core.size());
            r.put("recommended", String.join(", ", missingRecommended(a)));
            rows.add(r);
        }
        rows.sort(Comparator.comparing((Map<String, Object> m) -> (Integer) m.get("missingCount")).reversed());
        return table("Incomplete Asset Records", "Assets missing mandatory master-data fields.",
                withExtras(new Column("issue", "Missing Mandatory Fields", "text"), new Column("missingCount", "Count", "number"),
                        new Column("recommended", "Also Missing (recommended)", "text")),
                rows, summary("Assets", rows.size()),
                List.of("Mandatory fields: " + String.join(", ", CORE_FIELDS)
                        + ". Recommended fields: serial number, manufacturer, model, vendor."));
    }

    private record Check(String name, String severity, Set<Long> affected) {
    }

    /** Asset Data Quality Report: how many active assets fail each completeness and validity check. */
    public TabularReport dataQuality(ReportScope scope, String category, String department) {
        List<Asset> all = scopedAssets(scope, category, department);
        List<Asset> active = activeOf(all);
        Set<Long> activeIds = active.stream().map(Asset::getId).collect(Collectors.toSet());
        Map<Long, DepreciationParameters> policies = policies(active);
        Set<String> known = knownLocations();

        Map<String, GLAccountMapping> byCategory = new HashMap<>();
        GLAccountMapping fallback = null;
        for (GLAccountMapping m : mappingRepository.findAll()) {
            if (blank(m.getCategory())) fallback = m; else byCategory.put(key(m.getCategory()), m);
        }

        Set<Long> noCustodian = new HashSet<>(), noLocation = new HashSet<>(), noSerial = new HashSet<>(),
                noPolicy = new HashSet<>(), noMapping = new HashSet<>(), noCost = new HashSet<>(),
                badLife = new HashSet<>(), incomplete = new HashSet<>(), duplicate = new HashSet<>();
        for (Asset a : active) {
            if (blank(a.getCustodian()) && !unassigned(a)) noCustodian.add(a.getId());
            if (blank(a.getBranch()) || (!known.isEmpty() && !known.contains(key(a.getBranch())))) noLocation.add(a.getId());
            if (blank(a.getSerialNumber())) noSerial.add(a.getId());
            DepreciationParameters p = policies.get(a.getId());
            if (p == null && a.getPurchaseCost() != null && a.getPurchaseCost().signum() > 0) noPolicy.add(a.getId());
            if (p != null && !DepreciationValidation.validateParameters(p).isValid()
                    && (p.getUsefulLifeYears() == null || p.getUsefulLifeYears() <= 0 || p.getUsefulLifeYears() > 100)) badLife.add(a.getId());
            GLAccountMapping mapping = a.getCategory() == null ? fallback : byCategory.getOrDefault(key(a.getCategory()), fallback);
            if (mappingGap(mapping) != null) noMapping.add(a.getId());
            if (a.getPurchaseCost() == null || a.getPurchaseCost().signum() <= 0) noCost.add(a.getId());
            if (!missingCore(a).isEmpty()) incomplete.add(a.getId());
        }
        for (DuplicateGroup g : duplicateGroups(all)) {
            g.assets().stream().map(Asset::getId).filter(activeIds::contains).forEach(duplicate::add);
        }

        List<Check> checks = List.of(
                new Check("Missing custodian (assets in use)", "Medium", noCustodian),
                new Check("Missing or unknown location", "High", noLocation),
                new Check("Missing serial number", "Low", noSerial),
                new Check("No depreciation policy", "High", noPolicy),
                new Check("Invalid useful life", "High", badLife),
                new Check("No GL mapping", "High", noMapping),
                new Check("Missing acquisition cost", "High", noCost),
                new Check("Incomplete mandatory fields", "Medium", incomplete),
                new Check("Duplicate identifiers", "High", duplicate));

        List<Map<String, Object>> rows = new ArrayList<>();
        Set<Long> withIssue = new HashSet<>();
        for (Check c : checks) {
            withIssue.addAll(c.affected());
            double share = active.isEmpty() ? 0 : c.affected().size() * 100.0 / active.size();
            rows.add(row("check", c.name(), "severity", c.severity(), "affected", c.affected().size(),
                    "share", oneDecimal(share) + "%", "result", c.affected().isEmpty() ? "Pass" : "Needs attention"));
        }
        double clean = active.isEmpty() ? 100 : (active.size() - withIssue.size()) * 100.0 / active.size();
        List<Column> cols = List.of(new Column("check", "Check", "text"), new Column("severity", "Severity", "text"),
                new Column("affected", "Assets Affected", "number"), new Column("share", "% of Active Assets", "text"),
                new Column("result", "Result", "text"));
        return table("Asset Data Quality Report", "Consolidated assessment of asset data completeness, validity and consistency.",
                cols, rows,
                summary("Active assets assessed", active.size(), "Assets with at least one issue", withIssue.size(),
                        "Clean assets", oneDecimal(clean) + "%"),
                List.of("Each check counts active assets only; open the matching exception report to see the assets."));
    }
}
