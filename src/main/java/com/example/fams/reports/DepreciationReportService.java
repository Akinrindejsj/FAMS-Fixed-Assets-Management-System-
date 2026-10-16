package com.example.fams.reports;

import com.example.fams.assets.Asset;
import com.example.fams.assets.AssetRepository;
import com.example.fams.common.AppClock;
import com.example.fams.core.config.SecurityScopeService.ReportScope;
import com.example.fams.depreciation.DepreciationPosting;
import com.example.fams.depreciation.DepreciationPostingRepository;
import com.example.fams.gl.GLAccount;
import com.example.fams.gl.GLAccountMapping;
import com.example.fams.gl.GLAccountMappingRepository;
import com.example.fams.reports.TabularReport.Column;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Depreciation and valuation reports in the uniform table shape: depreciation by
 * location and by GL account for a period, and cost against accumulated depreciation.
 * Results follow the caller's report scope.
 */
@Service
@Transactional(readOnly = true)
public class DepreciationReportService {

    private final DepreciationPostingRepository postingRepository;
    private final AssetRepository assetRepository;
    private final GLAccountMappingRepository mappingRepository;
    private final ReportService reportService;

    public DepreciationReportService(DepreciationPostingRepository postingRepository,
                                     AssetRepository assetRepository,
                                     GLAccountMappingRepository mappingRepository,
                                     ReportService reportService) {
        this.postingRepository = postingRepository;
        this.assetRepository = assetRepository;
        this.mappingRepository = mappingRepository;
        this.reportService = reportService;
    }

    private BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    private BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private Map<String, Object> row(Object... keyValues) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            row.put((String) keyValues[i], keyValues[i + 1]);
        }
        return row;
    }

    /** Postings for a period, limited to assets the caller may see. */
    private List<DepreciationPosting> scopedPostings(ReportScope scope, String period) {
        if (period == null || period.isBlank()) {
            throw new IllegalArgumentException("Enter a depreciation period, for example 2026-03.");
        }
        List<DepreciationPosting> postings = postingRepository.findByDepreciationPeriodOrderByAssetCode(period.trim());
        if (scope == null || scope.isGlobal()) {
            return postings;
        }
        if (scope.isEmptyRestriction()) {
            return List.of();
        }
        List<Asset> allowed = scope.isDepartment()
                ? assetRepository.findByDepartmentInIgnoreCaseOrderByCreatedAtDesc(
                        scope.departments().stream().map(String::toLowerCase).collect(Collectors.toList()))
                : assetRepository.findByCustodianInIgnoreCaseOrderByCreatedAtDesc(scope.custodians());
        Set<Long> ids = allowed.stream().map(Asset::getId).collect(Collectors.toSet());
        return postings.stream().filter(p -> ids.contains(p.getAssetId())).toList();
    }

    private record Totals(long assets, BigDecimal cost, BigDecimal charge, BigDecimal accumulated, BigDecimal bookValue) {
        static Totals of(List<DepreciationPosting> postings) {
            BigDecimal cost = BigDecimal.ZERO, charge = BigDecimal.ZERO, accumulated = BigDecimal.ZERO, book = BigDecimal.ZERO;
            for (DepreciationPosting p : postings) {
                cost = cost.add(p.getAssetCost() == null ? BigDecimal.ZERO : p.getAssetCost());
                charge = charge.add(p.getDepreciationCharge() == null ? BigDecimal.ZERO : p.getDepreciationCharge());
                accumulated = accumulated.add(p.getClosingAccumulatedDepreciation() == null ? BigDecimal.ZERO : p.getClosingAccumulatedDepreciation());
                book = book.add(p.getBookValue() == null ? BigDecimal.ZERO : p.getBookValue());
            }
            return new Totals(postings.size(), cost, charge, accumulated, book);
        }
    }

    private TabularReport table(String title, String description, List<Column> columns, List<Map<String, Object>> rows,
                                Map<String, Object> totals, List<String> notes) {
        return new TabularReport(title, description, AppClock.today(), columns, rows,
                totals == null ? Map.<String, Object>of() : totals, Map.<String, Object>of(),
                notes == null ? List.<String>of() : notes, rows.isEmpty());
    }

    private Map<String, Object> groupRow(String keyName, String keyValue, Totals t) {
        return row(keyName, keyValue, "assets", t.assets(), "cost", money(t.cost()), "charge", money(t.charge()),
                "accumulated", money(t.accumulated()), "bookValue", money(t.bookValue()));
    }

    /** Depreciation expense for a period, grouped by the asset's branch/location. */
    public TabularReport byLocation(ReportScope scope, String period) {
        List<DepreciationPosting> postings = scopedPostings(scope, period);
        Map<Long, Asset> assets = assetRepository.findAllById(
                postings.stream().map(DepreciationPosting::getAssetId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(Asset::getId, a -> a));
        Map<String, List<DepreciationPosting>> groups = postings.stream().collect(Collectors.groupingBy(p -> {
            Asset a = assets.get(p.getAssetId());
            return a == null || a.getBranch() == null || a.getBranch().isBlank() ? "Unspecified" : a.getBranch();
        }, LinkedHashMap::new, Collectors.toList()));

        List<Map<String, Object>> rows = new ArrayList<>();
        groups.forEach((location, list) -> rows.add(groupRow("group", location, Totals.of(list))));
        rows.sort(Comparator.comparing((Map<String, Object> m) -> (BigDecimal) m.get("charge")).reversed());
        Totals all = Totals.of(postings);
        List<Column> cols = List.of(new Column("group", "Location", "text"), new Column("assets", "Assets", "number"),
                new Column("cost", "Original Cost", "money"), new Column("charge", "Depreciation Charge", "money"),
                new Column("accumulated", "Accumulated Depreciation", "money"), new Column("bookValue", "Book Value", "money"));
        return table("Depreciation by Location", "Depreciation charged in " + period.trim() + ", by branch/location.", cols, rows,
                rows.isEmpty() ? null : groupRow("group", "Totals", all), null);
    }

    /** Depreciation expense for a period, grouped by the GL accounts each category maps to. */
    public TabularReport byGlAccount(ReportScope scope, String period) {
        List<DepreciationPosting> postings = scopedPostings(scope, period);
        Map<String, GLAccountMapping> byCategory = new HashMap<>();
        GLAccountMapping fallback = null;
        for (GLAccountMapping m : mappingRepository.findAll()) {
            if (m.getCategory() == null || m.getCategory().isBlank()) {
                fallback = m;
            } else {
                byCategory.put(m.getCategory().trim().toLowerCase(), m);
            }
        }
        final GLAccountMapping defaultMapping = fallback;

        Map<String, List<DepreciationPosting>> groups = new LinkedHashMap<>();
        Map<String, String[]> labels = new HashMap<>();
        for (DepreciationPosting p : postings) {
            GLAccountMapping mapping = p.getCategory() == null ? defaultMapping
                    : byCategory.getOrDefault(p.getCategory().trim().toLowerCase(), defaultMapping);
            String expense = accountLabel(mapping == null ? null : mapping.getDepreciationExpenseAccount());
            String accumulated = accountLabel(mapping == null ? null : mapping.getAccumulatedDepreciationAccount());
            String key = expense + "|" + accumulated;
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(p);
            labels.put(key, new String[]{expense, accumulated});
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        groups.forEach((key, list) -> {
            Totals t = Totals.of(list);
            rows.add(row("expense", labels.get(key)[0], "accumulatedAccount", labels.get(key)[1], "assets", t.assets(),
                    "charge", money(t.charge()), "accumulated", money(t.accumulated()), "bookValue", money(t.bookValue())));
        });
        rows.sort(Comparator.comparing((Map<String, Object> m) -> (BigDecimal) m.get("charge")).reversed());
        Totals all = Totals.of(postings);
        List<Column> cols = List.of(new Column("expense", "Depreciation Expense Account", "text"),
                new Column("accumulatedAccount", "Accumulated Depreciation Account", "text"),
                new Column("assets", "Assets", "number"), new Column("charge", "Depreciation Charge", "money"),
                new Column("accumulated", "Accumulated Depreciation", "money"), new Column("bookValue", "Book Value", "money"));
        return table("Depreciation by GL Account", "Depreciation charged in " + period.trim() + ", by the GL accounts it posts to.",
                cols, rows,
                rows.isEmpty() ? null : row("expense", "Totals", "assets", all.assets(), "charge", money(all.charge()),
                        "accumulated", money(all.accumulated()), "bookValue", money(all.bookValue())),
                List.of("Accounts come from the category's GL mapping (or the global default). 'No GL mapping' means no mapping exists for that category."));
    }

    private String accountLabel(GLAccount account) {
        return account == null ? "No GL mapping" : account.getAccountCode() + " — " + account.getAccountName();
    }

    /** Cost against accumulated depreciation per asset, with the share of cost already depreciated. */
    public TabularReport costVsAccumulated(ReportScope scope, String category, String department) {
        ReportData data = reportService.generateValuationReport(scope, category, department);
        List<Map<String, Object>> rows = new ArrayList<>();
        BigDecimal cost = BigDecimal.ZERO, accumulated = BigDecimal.ZERO, nbv = BigDecimal.ZERO;
        for (ReportRow r : data.getRows()) {
            cost = cost.add(nz(r.acquisitionCost()));
            accumulated = accumulated.add(nz(r.accumulatedDepreciation()));
            nbv = nbv.add(nz(r.netBookValue()));
            rows.add(row("assetCode", r.assetCode(), "assetName", r.assetName(), "category", r.category() == null ? "" : r.category(),
                    "department", r.department() == null ? "" : r.department(), "cost", money(r.acquisitionCost()),
                    "accumulated", money(r.accumulatedDepreciation()), "nbv", money(r.netBookValue()),
                    "percent", percent(r.accumulatedDepreciation(), r.acquisitionCost())));
        }
        List<Column> cols = List.of(new Column("assetCode", "Asset Code", "text"), new Column("assetName", "Asset", "text"),
                new Column("category", "Category", "text"), new Column("department", "Department", "text"),
                new Column("cost", "Cost", "money"), new Column("accumulated", "Accumulated Depreciation", "money"),
                new Column("nbv", "Net Book Value", "money"), new Column("percent", "% Depreciated", "text"));
        return table("Cost vs Accumulated Depreciation", "Asset cost compared with accumulated depreciation and the share already depreciated.",
                cols, rows,
                rows.isEmpty() ? null : row("assetName", "Totals", "cost", money(cost), "accumulated", money(accumulated),
                        "nbv", money(nbv), "percent", percent(accumulated, cost)),
                null);
    }

    private String percent(BigDecimal part, BigDecimal whole) {
        if (whole == null || whole.signum() == 0) {
            return "—";
        }
        return nz(part).multiply(BigDecimal.valueOf(100)).divide(whole, 1, RoundingMode.HALF_UP) + "%";
    }
}
