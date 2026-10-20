package com.example.fams.reports;

import com.example.fams.assets.Asset;
import com.example.fams.assets.AssetCheckout;
import com.example.fams.assets.AssetCheckoutRepository;
import com.example.fams.assets.CapitalAddition;
import com.example.fams.assets.CapitalAdditionRepository;
import com.example.fams.audit.AuditVerificationResult;
import com.example.fams.audit.AuditVerificationResultRepository;
import com.example.fams.audit.AuditVerificationStatus;
import com.example.fams.common.AppClock;
import com.example.fams.core.config.SecurityScopeService.ReportScope;
import com.example.fams.depreciation.DepreciationParameters;
import com.example.fams.depreciation.DepreciationPosting;
import com.example.fams.depreciation.DepreciationPostingRepository;
import com.example.fams.depreciation.DepreciationService;
import com.example.fams.lifecycle.AssetLifecycleWorkflow;
import com.example.fams.lifecycle.AssetLifecycleWorkflowRepository;
import com.example.fams.lifecycle.LifecycleWorkflowStatus;
import com.example.fams.lifecycle.LifecycleWorkflowType;
import com.example.fams.maintenance.MaintenanceRecord;
import com.example.fams.maintenance.MaintenanceRecordRepository;
import com.example.fams.maintenance.MaintenanceType;
import com.example.fams.reports.TabularReport.Column;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Management and executive reports: the executive summary, CAPEX, monthly and growth
 * trends, utilisation, condition, replacement forecast and top assets.
 *
 * <p>Results follow the caller's report scope and can be narrowed by category and
 * department. Monthly trends cover the requested date range, or the last 12 months
 * (24 for growth) when none is given.
 */
@Service
@Transactional(readOnly = true)
public class ManagementReportService {

    private static final LocalDate MIN_DATE = LocalDate.of(1900, 1, 1);
    private static final LocalDate MAX_DATE = LocalDate.of(2999, 12, 31);
    private static final int MAX_MONTHS = 120;

    private final ScopedAssetFinder assetFinder;
    private final DepreciationService depreciationService;
    private final DepreciationPostingRepository postingRepository;
    private final CapitalAdditionRepository capitalAdditionRepository;
    private final AssetLifecycleWorkflowRepository workflowRepository;
    private final MaintenanceRecordRepository maintenanceRepository;
    private final AssetCheckoutRepository checkoutRepository;
    private final AuditVerificationResultRepository verificationRepository;
    private final AssetExceptionReportService exceptionService;
    private final MaintenanceReportService maintenanceReportService;

    public ManagementReportService(ScopedAssetFinder assetFinder,
                                   DepreciationService depreciationService,
                                   DepreciationPostingRepository postingRepository,
                                   CapitalAdditionRepository capitalAdditionRepository,
                                   AssetLifecycleWorkflowRepository workflowRepository,
                                   MaintenanceRecordRepository maintenanceRepository,
                                   AssetCheckoutRepository checkoutRepository,
                                   AuditVerificationResultRepository verificationRepository,
                                   AssetExceptionReportService exceptionService,
                                   MaintenanceReportService maintenanceReportService) {
        this.assetFinder = assetFinder;
        this.depreciationService = depreciationService;
        this.postingRepository = postingRepository;
        this.capitalAdditionRepository = capitalAdditionRepository;
        this.workflowRepository = workflowRepository;
        this.maintenanceRepository = maintenanceRepository;
        this.checkoutRepository = checkoutRepository;
        this.verificationRepository = verificationRepository;
        this.exceptionService = exceptionService;
        this.maintenanceReportService = maintenanceReportService;
    }

    // ------------------------------------------------------------------ helpers

    private BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private String text(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private boolean active(Asset a) {
        return a.getStatus() == null
                || !(a.getStatus().equalsIgnoreCase("Disposed") || a.getStatus().equalsIgnoreCase("Retired"));
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
                                Map<String, Object> totals, Map<String, Object> summary, List<String> notes) {
        return new TabularReport(title, description, AppClock.today(), columns, rows,
                totals == null ? Map.<String, Object>of() : totals,
                summary == null ? Map.<String, Object>of() : summary,
                notes == null ? List.<String>of() : notes, rows.isEmpty());
    }

    private Column col(String key, String label, String type) {
        return new Column(key, label, type);
    }

    private String percent(double part, double whole) {
        return whole == 0 ? "—" : String.format("%.1f%%", part * 100.0 / whole);
    }

    /** The months to report on: the given range, or the last {@code defaultMonths} ending this month. */
    private List<YearMonth> months(LocalDate from, LocalDate to, int defaultMonths) {
        YearMonth end = YearMonth.from(to != null ? to : AppClock.today());
        YearMonth start = from != null ? YearMonth.from(from) : end.minusMonths(defaultMonths - 1L);
        if (start.isAfter(end)) {
            throw new IllegalArgumentException("The start date cannot be after the end date.");
        }
        if (ChronoUnit.MONTHS.between(start, end) >= MAX_MONTHS) {
            throw new IllegalArgumentException("Choose a range of at most 10 years.");
        }
        List<YearMonth> months = new ArrayList<>();
        for (YearMonth m = start; !m.isAfter(end); m = m.plusMonths(1)) {
            months.add(m);
        }
        return months;
    }

    private YearMonth periodMonth(String period) {
        if (period == null) return null;
        try {
            return YearMonth.parse(period.trim());
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private Set<Long> ids(List<Asset> assets) {
        return assets.stream().map(Asset::getId).collect(Collectors.toSet());
    }

    private LocalDate disposalDate(AssetLifecycleWorkflow w) {
        if (w.getRequestedEffectiveDate() != null) return w.getRequestedEffectiveDate();
        return w.getRequestedAt() == null ? null : w.getRequestedAt().toLocalDate();
    }

    private List<AssetLifecycleWorkflow> completedDisposals(Set<Long> assetIds) {
        return workflowRepository.findAllByOrderByRequestedAtDesc().stream()
                .filter(w -> w.getType() == LifecycleWorkflowType.DISPOSAL && w.getStatus() == LifecycleWorkflowStatus.COMPLETED)
                .filter(w -> w.getAsset() != null && assetIds.contains(w.getAsset().getId()))
                .toList();
    }

    private double ageYears(Asset asset, LocalDate today) {
        LocalDate start = asset.getCapitalisationDate() != null ? asset.getCapitalisationDate() : asset.getPurchaseDate();
        return start == null ? -1 : Math.max(0, ChronoUnit.DAYS.between(start, today) / 365.25);
    }

    /** Net book value now: the latest depreciation posting's book value, else cost. */
    private BigDecimal nbv(Asset asset, Map<Long, DepreciationPosting> postings) {
        DepreciationPosting p = postings.get(asset.getId());
        return p != null && p.getBookValue() != null ? p.getBookValue() : nz(asset.getPurchaseCost());
    }

    // ----------------------------------------------------- executive summary

    /** Fixed Asset Executive Summary: portfolio, activity in the period and key exceptions. */
    public TabularReport executiveSummary(ReportScope scope, String category, String department, LocalDate from, LocalDate to) {
        LocalDate today = AppClock.today();
        LocalDate start = from != null ? from : today.withDayOfYear(1);
        LocalDate end = to != null ? to : today;

        List<Asset> all = assetFinder.find(scope, category, department);
        List<Asset> active = all.stream().filter(this::active).toList();
        Set<Long> allIds = ids(all);
        Map<Long, DepreciationPosting> latest = depreciationService.getLatestPostingsForAssets(ids(active));

        BigDecimal cost = BigDecimal.ZERO, accumulated = BigDecimal.ZERO, book = BigDecimal.ZERO;
        for (Asset a : active) {
            BigDecimal c = nz(a.getPurchaseCost());
            DepreciationPosting p = latest.get(a.getId());
            cost = cost.add(c);
            accumulated = accumulated.add(p != null && p.getClosingAccumulatedDepreciation() != null
                    ? p.getClosingAccumulatedDepreciation() : BigDecimal.ZERO);
            book = book.add(nbv(a, latest));
        }

        List<Asset> acquired = all.stream()
                .filter(a -> a.getPurchaseDate() != null && !a.getPurchaseDate().isBefore(start) && !a.getPurchaseDate().isAfter(end))
                .toList();
        BigDecimal acquiredCost = acquired.stream().map(a -> nz(a.getPurchaseCost())).reduce(BigDecimal.ZERO, BigDecimal::add);

        List<CapitalAddition> additions = capitalAdditionRepository.findAll().stream()
                .filter(c -> c.getAsset() != null && allIds.contains(c.getAsset().getId()))
                .filter(c -> c.getDate() != null && !c.getDate().isBefore(start) && !c.getDate().isAfter(end))
                .toList();
        BigDecimal additionsAmount = additions.stream().map(c -> nz(c.getAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);

        List<AssetLifecycleWorkflow> disposals = completedDisposals(allIds).stream()
                .filter(w -> {
                    LocalDate d = disposalDate(w);
                    return d != null && !d.isBefore(start) && !d.isAfter(end);
                }).toList();
        BigDecimal proceeds = disposals.stream().map(w -> nz(w.getDisposalProceeds())).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal gainLoss = disposals.stream().map(w -> nz(w.getFinancialImpact())).reduce(BigDecimal.ZERO, BigDecimal::add);

        YearMonth startMonth = YearMonth.from(start), endMonth = YearMonth.from(end);
        BigDecimal depreciation = postingRepository.findAll().stream()
                .filter(p -> allIds.contains(p.getAssetId()))
                .filter(p -> {
                    YearMonth m = periodMonth(p.getDepreciationPeriod());
                    return m != null && !m.isBefore(startMonth) && !m.isAfter(endMonth);
                })
                .map(p -> nz(p.getDepreciationCharge())).reduce(BigDecimal.ZERO, BigDecimal::add);

        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(metric("Portfolio", "Active assets", active.size(), null));
        rows.add(metric("Portfolio", "Historical cost", null, cost));
        rows.add(metric("Portfolio", "Accumulated depreciation", null, accumulated));
        rows.add(metric("Portfolio", "Net book value", null, book));
        rows.add(metric("Activity " + start + " to " + end, "Assets acquired (cost)", acquired.size(), acquiredCost));
        rows.add(metric("Activity " + start + " to " + end, "Capital additions", additions.size(), additionsAmount));
        rows.add(metric("Activity " + start + " to " + end, "Disposals (proceeds)", disposals.size(), proceeds));
        rows.add(metric("Activity " + start + " to " + end, "Net gain/loss on disposals", null, gainLoss));
        rows.add(metric("Activity " + start + " to " + end, "Depreciation charged", null, depreciation));
        rows.add(metric("Key exceptions", "Assets missing acquisition cost", exceptionService.missingCost(scope, category, department).rows().size(), null));
        rows.add(metric("Key exceptions", "Assets without a depreciation policy", exceptionService.noDepreciationPolicy(scope, category, department).rows().size(), null));
        rows.add(metric("Key exceptions", "Assets without GL mapping", exceptionService.noGlMapping(scope, category, department).rows().size(), null));
        rows.add(metric("Key exceptions", "Fully depreciated but still active", exceptionService.fullyDepreciatedActive(scope, category, department).rows().size(), null));
        rows.add(metric("Key exceptions", "Assets not verified in 365 days", exceptionService.notVerified(scope, category, department, 365).rows().size(), null));
        rows.add(metric("Key exceptions", "Overdue maintenance items", maintenanceReportService.overdue(scope).rows().size(), null));

        List<Column> cols = List.of(col("section", "Section", "text"), col("measure", "Measure", "text"),
                col("count", "Count", "number"), col("amount", "Amount", "money"));
        return table("Fixed Asset Executive Summary", "Asset population, value, activity and key exceptions at a glance.",
                cols, rows, null, null,
                List.of("Historical cost is the recorded purchase cost of active assets; net book value uses each asset's latest depreciation posting.",
                        "Depreciation charged counts postings whose period (YYYY-MM) falls in the date range."));
    }

    private Map<String, Object> metric(String section, String measure, Integer count, BigDecimal amount) {
        return row("section", section, "measure", measure, "count", count, "amount", amount == null ? null : money(amount));
    }

    // ---------------------------------------------------------------- CAPEX

    /** CAPEX Summary: new acquisitions and capital additions by category in the period (default: this year to date). */
    public TabularReport capex(ReportScope scope, String category, String department, LocalDate from, LocalDate to) {
        LocalDate start = from != null ? from : AppClock.today().withDayOfYear(1);
        LocalDate end = to != null ? to : AppClock.today();
        List<Asset> all = assetFinder.find(scope, category, department);
        Set<Long> allIds = ids(all);

        Map<String, BigDecimal[]> byCategory = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        Map<String, int[]> counts = new HashMap<>();
        for (Asset a : all) {
            if (a.getPurchaseDate() == null || a.getPurchaseDate().isBefore(start) || a.getPurchaseDate().isAfter(end)) continue;
            String key = a.getCategory() == null || a.getCategory().isBlank() ? "Uncategorised" : a.getCategory();
            byCategory.computeIfAbsent(key, k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            byCategory.get(key)[0] = byCategory.get(key)[0].add(nz(a.getPurchaseCost()));
            counts.computeIfAbsent(key, k -> new int[1])[0]++;
        }
        for (CapitalAddition c : capitalAdditionRepository.findAll()) {
            if (c.getAsset() == null || !allIds.contains(c.getAsset().getId())) continue;
            if (c.getDate() == null || c.getDate().isBefore(start) || c.getDate().isAfter(end)) continue;
            String cat = c.getAsset().getCategory();
            String key = cat == null || cat.isBlank() ? "Uncategorised" : cat;
            byCategory.computeIfAbsent(key, k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            byCategory.get(key)[1] = byCategory.get(key)[1].add(nz(c.getAmount()));
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        BigDecimal acquisitions = BigDecimal.ZERO, additions = BigDecimal.ZERO;
        int assetCount = 0;
        for (Map.Entry<String, BigDecimal[]> e : byCategory.entrySet()) {
            int n = counts.getOrDefault(e.getKey(), new int[1])[0];
            acquisitions = acquisitions.add(e.getValue()[0]);
            additions = additions.add(e.getValue()[1]);
            assetCount += n;
            rows.add(row("category", e.getKey(), "count", n, "acquisitions", money(e.getValue()[0]),
                    "additions", money(e.getValue()[1]), "total", money(e.getValue()[0].add(e.getValue()[1]))));
        }
        rows.sort(Comparator.comparing((Map<String, Object> m) -> (BigDecimal) m.get("total")).reversed());
        List<Column> cols = List.of(col("category", "Category", "text"), col("count", "Assets Acquired", "number"),
                col("acquisitions", "Acquisition Cost", "money"), col("additions", "Capital Additions", "money"),
                col("total", "Total CAPEX", "money"));
        return table("CAPEX Summary", "Capital expenditure from " + start + " to " + end + ", by category.", cols, rows,
                rows.isEmpty() ? null : row("category", "Totals", "count", assetCount, "acquisitions", money(acquisitions),
                        "additions", money(additions), "total", money(acquisitions.add(additions))),
                null, List.of("Acquisitions are assets whose purchase date falls in the period; capital additions are improvements and upgrades dated in the period."));
    }

    // ---------------------------------------------------------------- trends

    /** Monthly Asset Additions: assets capitalised and capital additions booked each month. */
    public TabularReport monthlyAdditions(ReportScope scope, String category, String department, LocalDate from, LocalDate to) {
        List<YearMonth> months = months(from, to, 12);
        List<Asset> all = assetFinder.find(scope, category, department);
        Set<Long> allIds = ids(all);
        Map<YearMonth, Integer> counts = new HashMap<>();
        Map<YearMonth, BigDecimal> cost = new HashMap<>();
        for (Asset a : all) {
            if (!a.isCapitalised() || a.getCapitalisationDate() == null) continue;
            YearMonth m = YearMonth.from(a.getCapitalisationDate());
            counts.merge(m, 1, Integer::sum);
            cost.merge(m, nz(a.getPurchaseCost()), BigDecimal::add);
        }
        Map<YearMonth, BigDecimal> additions = new HashMap<>();
        for (CapitalAddition c : capitalAdditionRepository.findAll()) {
            if (c.getAsset() == null || !allIds.contains(c.getAsset().getId()) || c.getDate() == null) continue;
            additions.merge(YearMonth.from(c.getDate()), nz(c.getAmount()), BigDecimal::add);
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        int totalCount = 0;
        BigDecimal totalCost = BigDecimal.ZERO, totalAdditions = BigDecimal.ZERO;
        for (YearMonth m : months) {
            int n = counts.getOrDefault(m, 0);
            BigDecimal c = cost.getOrDefault(m, BigDecimal.ZERO), add = additions.getOrDefault(m, BigDecimal.ZERO);
            totalCount += n;
            totalCost = totalCost.add(c);
            totalAdditions = totalAdditions.add(add);
            rows.add(row("month", m.toString(), "count", n, "cost", money(c), "additions", money(add), "total", money(c.add(add))));
        }
        List<Column> cols = List.of(col("month", "Month", "text"), col("count", "Assets Capitalised", "number"),
                col("cost", "Cost Capitalised", "money"), col("additions", "Capital Additions", "money"), col("total", "Total", "money"));
        return table("Monthly Asset Additions", "Newly capitalised assets and capital additions by month.", cols, rows,
                row("month", "Totals", "count", totalCount, "cost", money(totalCost), "additions", money(totalAdditions),
                        "total", money(totalCost.add(totalAdditions))),
                null, List.of("Only assets with a capitalisation date are counted. Assets not yet capitalised are in the Assets Awaiting Capitalisation report."));
    }

    /** Monthly Disposals: completed disposals, proceeds and gain/loss by month. */
    public TabularReport monthlyDisposals(ReportScope scope, String category, String department, LocalDate from, LocalDate to) {
        List<YearMonth> months = months(from, to, 12);
        Set<Long> allIds = ids(assetFinder.find(scope, category, department));
        Map<YearMonth, int[]> counts = new HashMap<>();
        Map<YearMonth, BigDecimal[]> amounts = new HashMap<>();
        for (AssetLifecycleWorkflow w : completedDisposals(allIds)) {
            LocalDate d = disposalDate(w);
            if (d == null) continue;
            YearMonth m = YearMonth.from(d);
            counts.computeIfAbsent(m, k -> new int[1])[0]++;
            amounts.computeIfAbsent(m, k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            amounts.get(m)[0] = amounts.get(m)[0].add(nz(w.getDisposalProceeds()));
            amounts.get(m)[1] = amounts.get(m)[1].add(nz(w.getFinancialImpact()));
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        int total = 0;
        BigDecimal proceeds = BigDecimal.ZERO, gainLoss = BigDecimal.ZERO;
        for (YearMonth m : months) {
            int n = counts.getOrDefault(m, new int[1])[0];
            BigDecimal[] a = amounts.getOrDefault(m, new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            total += n;
            proceeds = proceeds.add(a[0]);
            gainLoss = gainLoss.add(a[1]);
            rows.add(row("month", m.toString(), "count", n, "proceeds", money(a[0]), "gainLoss", money(a[1])));
        }
        List<Column> cols = List.of(col("month", "Month", "text"), col("count", "Disposals", "number"),
                col("proceeds", "Proceeds", "money"), col("gainLoss", "Gain / (Loss)", "money"));
        return table("Monthly Disposals", "Completed disposals, proceeds and gain or loss by month.", cols, rows,
                row("month", "Totals", "count", total, "proceeds", money(proceeds), "gainLoss", money(gainLoss)), null,
                List.of("Based on completed disposal workflows dated by their effective date."));
    }

    /** Monthly Depreciation: depreciation expense by depreciation period. */
    public TabularReport monthlyDepreciation(ReportScope scope, String category, String department, LocalDate from, LocalDate to) {
        List<YearMonth> months = months(from, to, 12);
        YearMonth first = months.get(0), last = months.get(months.size() - 1);
        Set<Long> allIds = ids(assetFinder.find(scope, category, department));
        Map<YearMonth, List<DepreciationPosting>> byMonth = new HashMap<>();
        for (DepreciationPosting p : postingRepository.findAll()) {
            if (!allIds.contains(p.getAssetId())) continue;
            YearMonth m = periodMonth(p.getDepreciationPeriod());
            if (m == null || m.isBefore(first) || m.isAfter(last)) continue;
            byMonth.computeIfAbsent(m, k -> new ArrayList<>()).add(p);
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (YearMonth m : months) {
            List<DepreciationPosting> postings = byMonth.getOrDefault(m, List.of());
            BigDecimal charge = postings.stream().map(p -> nz(p.getDepreciationCharge())).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal accumulated = postings.stream().map(p -> nz(p.getClosingAccumulatedDepreciation())).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal book = postings.stream().map(p -> nz(p.getBookValue())).reduce(BigDecimal.ZERO, BigDecimal::add);
            total = total.add(charge);
            rows.add(row("month", m.toString(), "assets", postings.size(), "charge", money(charge),
                    "accumulated", money(accumulated), "bookValue", money(book)));
        }
        List<Column> cols = List.of(col("month", "Period", "text"), col("assets", "Assets Depreciated", "number"),
                col("charge", "Depreciation Charge", "money"), col("accumulated", "Accumulated Depreciation", "money"),
                col("bookValue", "Net Book Value", "money"));
        return table("Monthly Depreciation Trend", "Depreciation expense by month.", cols, rows,
                row("month", "Total charge", "charge", money(total)), null,
                List.of("Accumulated depreciation and net book value are the sums of each month's postings, for assets depreciated that month."));
    }

    /** Maintenance Cost Trend: preventive and corrective maintenance cost by month. */
    public TabularReport maintenanceTrend(ReportScope scope, String category, String department, LocalDate from, LocalDate to) {
        List<YearMonth> months = months(from, to, 12);
        Set<Long> allIds = ids(assetFinder.find(scope, category, department));
        Map<YearMonth, BigDecimal[]> costs = new HashMap<>();
        Map<YearMonth, int[]> events = new HashMap<>();
        for (MaintenanceRecord r : maintenanceRepository.findByMaintenanceDateBetweenOrderByMaintenanceDateDesc(MIN_DATE, MAX_DATE)) {
            if (r.getAsset() == null || !allIds.contains(r.getAsset().getId()) || r.getMaintenanceDate() == null) continue;
            YearMonth m = YearMonth.from(r.getMaintenanceDate());
            costs.computeIfAbsent(m, k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            int slot = r.getType() == MaintenanceType.PREVENTIVE ? 0 : 1;
            costs.get(m)[slot] = costs.get(m)[slot].add(nz(r.getMaintenanceCost()));
            events.computeIfAbsent(m, k -> new int[1])[0]++;
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        BigDecimal preventive = BigDecimal.ZERO, corrective = BigDecimal.ZERO;
        int total = 0;
        for (YearMonth m : months) {
            BigDecimal[] c = costs.getOrDefault(m, new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            int n = events.getOrDefault(m, new int[1])[0];
            preventive = preventive.add(c[0]);
            corrective = corrective.add(c[1]);
            total += n;
            rows.add(row("month", m.toString(), "events", n, "preventive", money(c[0]), "corrective", money(c[1]),
                    "total", money(c[0].add(c[1]))));
        }
        List<Column> cols = List.of(col("month", "Month", "text"), col("events", "Events", "number"),
                col("preventive", "Preventive Cost", "money"), col("corrective", "Corrective Cost", "money"), col("total", "Total Cost", "money"));
        return table("Maintenance Cost Trend", "Maintenance expenditure by month.", cols, rows,
                row("month", "Totals", "events", total, "preventive", money(preventive), "corrective", money(corrective),
                        "total", money(preventive.add(corrective))), null, null);
    }

    /** Asset Growth Trend: cumulative acquisitions, disposals, active population and cost by month-end. */
    public TabularReport growthTrend(ReportScope scope, String category, String department, LocalDate from, LocalDate to) {
        List<YearMonth> months = months(from, to, 24);
        List<Asset> all = assetFinder.find(scope, category, department);
        Set<Long> allIds = ids(all);

        Map<Long, LocalDate> disposedOn = new HashMap<>();
        for (AssetLifecycleWorkflow w : completedDisposals(allIds)) {
            LocalDate d = disposalDate(w);
            if (d != null) disposedOn.putIfAbsent(w.getAsset().getId(), d);
        }
        for (Asset a : all) {
            if ("Disposed".equalsIgnoreCase(a.getStatus()) && !disposedOn.containsKey(a.getId()) && a.getUpdatedAt() != null) {
                disposedOn.put(a.getId(), a.getUpdatedAt().toLocalDate()); // no workflow: estimate from last update
            }
        }
        List<CapitalAddition> additions = capitalAdditionRepository.findAll().stream()
                .filter(c -> c.getAsset() != null && allIds.contains(c.getAsset().getId()) && c.getDate() != null).toList();

        List<Map<String, Object>> rows = new ArrayList<>();
        for (YearMonth m : months) {
            LocalDate end = m.atEndOfMonth();
            int acquired = 0, disposed = 0;
            BigDecimal cost = BigDecimal.ZERO;
            for (Asset a : all) {
                if (a.getPurchaseDate() == null || a.getPurchaseDate().isAfter(end)) continue;
                acquired++;
                LocalDate gone = disposedOn.get(a.getId());
                if (gone != null && !gone.isAfter(end)) {
                    disposed++;
                } else {
                    cost = cost.add(nz(a.getPurchaseCost()));
                }
            }
            for (CapitalAddition c : additions) {
                if (c.getDate().isAfter(end)) continue;
                LocalDate gone = disposedOn.get(c.getAsset().getId());
                if (gone == null || gone.isAfter(end)) cost = cost.add(nz(c.getAmount()));
            }
            rows.add(row("month", m.toString(), "acquired", acquired, "disposed", disposed,
                    "population", acquired - disposed, "cost", money(cost)));
        }
        List<Column> cols = List.of(col("month", "Month End", "text"), col("acquired", "Cumulative Acquired", "number"),
                col("disposed", "Cumulative Disposed", "number"), col("population", "Active Population", "number"),
                col("cost", "Cost of Active Assets", "money"));
        return table("Asset Growth Trend", "Growth in asset population and cost, month by month.", cols, rows, null, null,
                List.of("Assets are dated by purchase date. Disposals use completed disposal workflows; assets marked Disposed without a workflow use the date of their last update.",
                        "Assets with no purchase date are not counted."));
    }

    // ----------------------------------------------------------- snapshots

    /** Asset Utilisation: share of active assets in use, by category, plus checkouts in the period. */
    public TabularReport utilisation(ReportScope scope, String category, String department, LocalDate from, LocalDate to) {
        LocalDate start = from != null ? from : MIN_DATE;
        LocalDate end = to != null ? to : MAX_DATE;
        List<Asset> active = assetFinder.find(scope, category, department).stream().filter(this::active).toList();
        Set<Long> activeIds = ids(active);

        Map<String, int[]> byCategory = new TreeMap<>(String.CASE_INSENSITIVE_ORDER); // active, inUse, unassigned
        for (Asset a : active) {
            String key = a.getCategory() == null || a.getCategory().isBlank() ? "Uncategorised" : a.getCategory();
            int[] c = byCategory.computeIfAbsent(key, k -> new int[3]);
            c[0]++;
            String status = a.getStatus() == null ? "" : a.getStatus().trim().toLowerCase();
            if (status.equals("assigned") || status.equals("checked out") || status.equals("in use")) c[1]++;
            else if (status.equals("in stock") || status.equals("available") || status.equals("returned")) c[2]++;
        }
        Map<String, Integer> checkouts = new HashMap<>();
        for (AssetCheckout c : checkoutRepository.findAll()) {
            if (c.getAsset() == null || !activeIds.contains(c.getAsset().getId()) || c.getCheckoutDate() == null) continue;
            if (c.getCheckoutDate().isBefore(start) || c.getCheckoutDate().isAfter(end)) continue;
            String cat = c.getAsset().getCategory();
            checkouts.merge(cat == null || cat.isBlank() ? "Uncategorised" : cat, 1, Integer::sum);
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        int totalActive = 0, totalInUse = 0, totalUnassigned = 0, totalCheckouts = 0;
        for (Map.Entry<String, int[]> e : byCategory.entrySet()) {
            int[] c = e.getValue();
            int co = checkouts.getOrDefault(e.getKey(), 0);
            totalActive += c[0];
            totalInUse += c[1];
            totalUnassigned += c[2];
            totalCheckouts += co;
            rows.add(row("category", e.getKey(), "active", c[0], "inUse", c[1], "unassigned", c[2], "other", c[0] - c[1] - c[2],
                    "utilisation", percent(c[1], c[0]), "checkouts", co));
        }
        List<Column> cols = List.of(col("category", "Category", "text"), col("active", "Active Assets", "number"),
                col("inUse", "In Use", "number"), col("unassigned", "Unassigned", "number"), col("other", "Other", "number"),
                col("utilisation", "Utilisation", "text"), col("checkouts", "Checkouts in Period", "number"));
        return table("Asset Utilisation", "Share of active assets currently in use, by category.", cols, rows,
                rows.isEmpty() ? null : row("category", "Totals", "active", totalActive, "inUse", totalInUse,
                        "unassigned", totalUnassigned, "other", totalActive - totalInUse - totalUnassigned,
                        "utilisation", percent(totalInUse, totalActive), "checkouts", totalCheckouts),
                null, List.of("In use means status Assigned, Checked Out or In Use; unassigned means In Stock, Available or Returned.",
                        "The system holds no usage-hours or throughput data, so utilisation is the share of assets in use today."));
    }

    /** Asset Condition Summary: each active asset's latest verified condition, counted by category. */
    public TabularReport conditionSummary(ReportScope scope, String category, String department) {
        List<Asset> active = assetFinder.find(scope, category, department).stream().filter(this::active).toList();
        Map<Long, AuditVerificationResult> latest = new HashMap<>();
        for (AuditVerificationResult r : verificationRepository.findInRangeFetched(MIN_DATE.atStartOfDay(), MAX_DATE.atStartOfDay())) {
            latest.putIfAbsent(r.getAsset().getId(), r); // newest first
        }
        Map<String, int[]> byCategory = new TreeMap<>(String.CASE_INSENSITIVE_ORDER); // good, fair, poor, damaged, not assessed
        for (Asset a : active) {
            String key = a.getCategory() == null || a.getCategory().isBlank() ? "Uncategorised" : a.getCategory();
            int[] c = byCategory.computeIfAbsent(key, k -> new int[5]);
            AuditVerificationResult r = latest.get(a.getId());
            int slot;
            if (r == null) slot = 4;
            else if (r.getResultStatus() == AuditVerificationStatus.DAMAGED) slot = 3;
            else if (r.getConditionRating() == null) slot = 4;
            else slot = switch (r.getConditionRating()) {
                case GOOD -> 0;
                case FAIR -> 1;
                case POOR -> 2;
            };
            c[slot]++;
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        int[] totals = new int[5];
        for (Map.Entry<String, int[]> e : byCategory.entrySet()) {
            int[] c = e.getValue();
            for (int i = 0; i < 5; i++) totals[i] += c[i];
            rows.add(row("category", e.getKey(), "good", c[0], "fair", c[1], "poor", c[2], "damaged", c[3],
                    "unassessed", c[4], "total", c[0] + c[1] + c[2] + c[3] + c[4]));
        }
        List<Column> cols = List.of(col("category", "Category", "text"), col("good", "Good", "number"), col("fair", "Fair", "number"),
                col("poor", "Poor", "number"), col("damaged", "Damaged", "number"), col("unassessed", "Not Assessed", "number"),
                col("total", "Total", "number"));
        return table("Asset Condition Summary", "Condition of active assets at their latest physical verification, by category.",
                cols, rows,
                rows.isEmpty() ? null : row("category", "Totals", "good", totals[0], "fair", totals[1], "poor", totals[2],
                        "damaged", totals[3], "unassessed", totals[4],
                        "total", totals[0] + totals[1] + totals[2] + totals[3] + totals[4]),
                null, List.of("Condition comes from physical verification. Assets never verified, or verified without a rating, are Not Assessed."));
    }

    /** Asset Replacement Forecast: active assets reaching the end of their useful life, by year. */
    public TabularReport replacementForecast(ReportScope scope, String category, String department, Integer years) {
        int horizon = years != null && years > 0 ? Math.min(years, 30) : 5;
        LocalDate today = AppClock.today();
        List<Asset> active = assetFinder.find(scope, category, department).stream().filter(this::active).toList();
        Map<Long, DepreciationParameters> policies = depreciationService.resolveParametersForAssets(active);
        Map<Long, DepreciationPosting> latest = depreciationService.getLatestPostingsForAssets(ids(active));

        int firstYear = today.getYear();
        Map<Integer, List<Asset>> byYear = new TreeMap<>();
        List<Asset> past = new ArrayList<>();
        int noPolicy = 0;
        for (Asset a : active) {
            DepreciationParameters p = policies.get(a.getId());
            LocalDate start = a.getCapitalisationDate() != null ? a.getCapitalisationDate() : a.getPurchaseDate();
            if (p == null || p.getUsefulLifeYears() == null || p.getUsefulLifeYears() <= 0 || start == null) {
                noPolicy++;
                continue;
            }
            LocalDate endOfLife = start.plusYears(p.getUsefulLifeYears());
            if (endOfLife.isBefore(today)) past.add(a);
            else if (endOfLife.getYear() <= firstYear + horizon - 1) byYear.computeIfAbsent(endOfLife.getYear(), k -> new ArrayList<>()).add(a);
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(forecastRow("Already past useful life", past, latest));
        for (int y = firstYear; y < firstYear + horizon; y++) {
            rows.add(forecastRow(String.valueOf(y), byYear.getOrDefault(y, List.of()), latest));
        }
        int countTotal = rows.stream().mapToInt(r -> (Integer) r.get("count")).sum();
        BigDecimal costTotal = rows.stream().map(r -> (BigDecimal) r.get("cost")).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal nbvTotal = rows.stream().map(r -> (BigDecimal) r.get("nbv")).reduce(BigDecimal.ZERO, BigDecimal::add);
        List<Column> cols = List.of(col("period", "Reaches End of Useful Life", "text"), col("count", "Assets", "number"),
                col("cost", "Original Cost", "money"), col("nbv", "Net Book Value Today", "money"));
        List<String> notes = new ArrayList<>();
        notes.add("End of life is the capitalisation (or purchase) date plus the useful life in the asset's depreciation policy. "
                + "Cost shown is the original cost, not an inflation-adjusted replacement price.");
        if (noPolicy > 0) {
            notes.add(noPolicy + " active asset(s) have no usable policy or start date and are not forecast.");
        }
        return table("Asset Replacement Forecast", "Assets expected to need replacement over the next " + horizon + " years.", cols, rows,
                row("period", "Totals", "count", countTotal, "cost", money(costTotal), "nbv", money(nbvTotal)),
                summary("Horizon (years)", horizon), notes);
    }

    private Map<String, Object> forecastRow(String period, List<Asset> assets, Map<Long, DepreciationPosting> latest) {
        BigDecimal cost = assets.stream().map(a -> nz(a.getPurchaseCost())).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal book = assets.stream().map(a -> nbv(a, latest)).reduce(BigDecimal.ZERO, BigDecimal::add);
        return row("period", period, "count", assets.size(), "cost", money(cost), "nbv", money(book));
    }

    /** Top 10 Highest-Value Assets, by original cost or by net book value. */
    public TabularReport topAssets(ReportScope scope, String category, String department, boolean byNbv) {
        List<Asset> active = assetFinder.find(scope, category, department).stream().filter(this::active).toList();
        Map<Long, DepreciationPosting> latest = depreciationService.getLatestPostingsForAssets(ids(active));
        List<Asset> top = active.stream()
                .sorted(Comparator.comparing((Asset a) -> byNbv ? nbv(a, latest) : nz(a.getPurchaseCost())).reversed())
                .limit(10).toList();
        List<Map<String, Object>> rows = new ArrayList<>();
        int rank = 1;
        for (Asset a : top) {
            DepreciationPosting p = latest.get(a.getId());
            rows.add(row("rank", rank++, "assetCode", a.getAssetCode(), "assetName", a.getName(), "category", text(a.getCategory()),
                    "department", text(a.getDepartment()), "branch", text(a.getBranch()), "cost", money(a.getPurchaseCost()),
                    "accumulated", money(p == null ? null : p.getClosingAccumulatedDepreciation()), "nbv", money(nbv(a, latest))));
        }
        List<Column> cols = List.of(col("rank", "Rank", "number"), col("assetCode", "Asset Code", "text"), col("assetName", "Asset", "text"),
                col("category", "Category", "text"), col("department", "Department", "text"), col("branch", "Location", "text"),
                col("cost", "Cost", "money"), col("accumulated", "Accumulated Depreciation", "money"), col("nbv", "Net Book Value", "money"));
        return table(byNbv ? "Top 10 Assets by Net Book Value" : "Top 10 Assets by Cost",
                byNbv ? "The ten active assets with the highest carrying amount." : "The ten active assets with the highest original cost.",
                cols, rows, null, null, null);
    }
}
