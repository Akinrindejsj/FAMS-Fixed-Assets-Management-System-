package com.example.fams.reports;

import com.example.fams.assets.Asset;
import com.example.fams.assets.AssetCheckout;
import com.example.fams.assets.AssetCheckoutRepository;
import com.example.fams.assets.CapitalAddition;
import com.example.fams.assets.CapitalAdditionRepository;
import com.example.fams.common.AppClock;
import com.example.fams.core.config.SecurityScopeService.ReportScope;
import com.example.fams.depreciation.DepreciationCalculationService;
import com.example.fams.depreciation.DepreciationParameters;
import com.example.fams.depreciation.DepreciationPosting;
import com.example.fams.depreciation.DepreciationService;
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
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Forecasting and analytical reports: depreciation forecasts, future net book value,
 * replacement and CAPEX forecasts, assets reaching end of life, maintenance cost forecast,
 * replacement cost exposure, age distribution and cost/benefit analysis.
 *
 * <p>Every forecast is an estimate built from stated assumptions, shown in each report's
 * notes. Depreciation is projected month by month using the same formula the depreciation
 * run applies, so a forecast and the runs that follow it agree. Results follow the caller's
 * report scope and can be narrowed by category and department.
 */
@Service
@Transactional(readOnly = true)
public class ForecastReportService {

    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MMM yyyy");

    private final ScopedAssetFinder assetFinder;
    private final DepreciationService depreciationService;
    private final DepreciationCalculationService calculationService;
    private final MaintenanceRecordRepository maintenanceRepository;
    private final CapitalAdditionRepository capitalAdditionRepository;
    private final AssetCheckoutRepository checkoutRepository;

    public ForecastReportService(ScopedAssetFinder assetFinder,
                                 DepreciationService depreciationService,
                                 DepreciationCalculationService calculationService,
                                 MaintenanceRecordRepository maintenanceRepository,
                                 CapitalAdditionRepository capitalAdditionRepository,
                                 AssetCheckoutRepository checkoutRepository) {
        this.assetFinder = assetFinder;
        this.depreciationService = depreciationService;
        this.calculationService = calculationService;
        this.maintenanceRepository = maintenanceRepository;
        this.capitalAdditionRepository = capitalAdditionRepository;
        this.checkoutRepository = checkoutRepository;
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

    private Column col(String key, String label, String type) {
        return new Column(key, label, type);
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

    private boolean notDisposed(Asset a) {
        return a.getStatus() == null
                || !(a.getStatus().equalsIgnoreCase("Disposed") || a.getStatus().equalsIgnoreCase("Scrapped"));
    }

    private boolean active(Asset a) {
        return a.getStatus() == null
                || !(a.getStatus().equalsIgnoreCase("Disposed") || a.getStatus().equalsIgnoreCase("Retired"));
    }

    private int positive(Integer value, int fallback, int max) {
        return value != null && value > 0 ? Math.min(value, max) : fallback;
    }

    private double rateOf(Double rate) {
        return rate == null || rate < 0 ? 0 : Math.min(rate, 100) / 100.0;
    }

    private LocalDate startDate(Asset a) {
        return a.getCapitalisationDate() != null ? a.getCapitalisationDate() : a.getPurchaseDate();
    }

    /** Date the asset reaches the end of its useful life, or null if it has no usable policy or start date. */
    private LocalDate endOfLife(Asset a, DepreciationParameters p) {
        LocalDate start = startDate(a);
        if (p == null || p.getUsefulLifeYears() == null || p.getUsefulLifeYears() <= 0 || start == null) return null;
        return start.plusYears(p.getUsefulLifeYears());
    }

    private BigDecimal nbvNow(Asset a, Map<Long, DepreciationPosting> postings) {
        DepreciationPosting p = postings.get(a.getId());
        return p != null && p.getBookValue() != null ? p.getBookValue() : nz(a.getPurchaseCost());
    }

    private BigDecimal grow(BigDecimal amount, double rate, long years) {
        if (rate == 0 || years <= 0) return nz(amount);
        return nz(amount).multiply(BigDecimal.valueOf(Math.pow(1 + rate, years)));
    }

    private String notesRate(double rate) {
        return rate == 0 ? "No cost inflation is applied, so amounts are at original cost."
                : "Costs are inflated by " + String.format("%.1f", rate * 100) + "% a year.";
    }

    // ------------------------------------------------- depreciation projection

    private record Projection(BigDecimal[] charge, BigDecimal[] accumulated, BigDecimal[] bookValue, int[] depreciating,
                              BigDecimal openingAccumulated, BigDecimal openingBook, int included, int excluded) {
    }

    /**
     * Projects depreciation month by month, starting next month, using the depreciation run's
     * own formula (annual charge for year 1 on the current accumulated balance, divided by 12,
     * capped at cost less residual). Assets with no policy hold their current value.
     */
    private Projection project(List<Asset> assets, int months) {
        Map<Long, DepreciationPosting> postings = depreciationService.getLatestPostingsForAssets(
                assets.stream().map(Asset::getId).toList());
        Map<Long, DepreciationParameters> policies = depreciationService.resolveParametersForAssets(assets);

        BigDecimal[] charge = zeros(months), accumulated = zeros(months), book = zeros(months);
        int[] depreciating = new int[months];
        BigDecimal openingAccumulated = BigDecimal.ZERO, openingBook = BigDecimal.ZERO;
        int included = 0, excluded = 0;

        for (Asset a : assets) {
            BigDecimal cost = nz(a.getPurchaseCost());
            DepreciationPosting latest = postings.get(a.getId());
            BigDecimal acc0 = latest != null && latest.getClosingAccumulatedDepreciation() != null
                    ? latest.getClosingAccumulatedDepreciation() : BigDecimal.ZERO;
            DepreciationParameters p = policies.get(a.getId());
            openingAccumulated = openingAccumulated.add(acc0);
            openingBook = openingBook.add(nbvNow(a, postings));

            if (p == null || cost.signum() <= 0) {
                excluded++;
                BigDecimal held = nbvNow(a, postings);
                for (int m = 0; m < months; m++) {
                    accumulated[m] = accumulated[m].add(acc0);
                    book[m] = book[m].add(held);
                }
                continue;
            }
            included++;
            BigDecimal residual = nz(p.getResidualValue());
            BigDecimal limit = cost.subtract(residual);
            BigDecimal acc = acc0;
            for (int m = 0; m < months; m++) {
                BigDecimal annual = calculationService.calculateAnnualDepreciation(
                        cost, residual, p.getUsefulLifeYears(), p.getMethod(), 1, acc);
                BigDecimal monthly = calculationService.prorateCharge(annual, "monthly");
                if (acc.add(monthly).compareTo(limit) > 0) {
                    monthly = limit.subtract(acc).max(BigDecimal.ZERO);
                }
                acc = acc.add(monthly);
                charge[m] = charge[m].add(monthly);
                accumulated[m] = accumulated[m].add(acc);
                book[m] = book[m].add(cost.subtract(acc));
                if (monthly.signum() > 0) depreciating[m]++;
            }
        }
        return new Projection(charge, accumulated, book, depreciating, openingAccumulated, openingBook, included, excluded);
    }

    private BigDecimal[] zeros(int n) {
        BigDecimal[] values = new BigDecimal[n];
        java.util.Arrays.fill(values, BigDecimal.ZERO);
        return values;
    }

    private YearMonth firstMonth() {
        return YearMonth.from(AppClock.today()).plusMonths(1);
    }

    private List<String> projectionNotes(Projection p) {
        List<String> notes = new ArrayList<>();
        notes.add("Forecast starts next month and assumes depreciation is posted every month, with no disposals, additions, revaluations or impairments.");
        notes.add("It uses the same calculation as the depreciation run, from each asset's latest posted accumulated depreciation.");
        if (p.excluded() > 0) {
            notes.add(p.excluded() + " asset(s) have no depreciation policy or cost and are held at their current value.");
        }
        return notes;
    }

    /** Depreciation Forecast over {@code months} (12 shows each month; longer horizons show 12-month blocks). */
    public TabularReport depreciationForecast(ReportScope scope, String category, String department, int months) {
        List<Asset> assets = assetFinder.find(scope, category, department).stream().filter(this::notDisposed).toList();
        Projection p = project(assets, months);
        YearMonth first = firstMonth();
        List<Map<String, Object>> rows = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;

        if (months <= 12) {
            for (int m = 0; m < months; m++) {
                total = total.add(p.charge()[m]);
                rows.add(row("period", first.plusMonths(m).format(MONTH), "charge", money(p.charge()[m]),
                        "accumulated", money(p.accumulated()[m]), "bookValue", money(p.bookValue()[m]),
                        "assets", p.depreciating()[m]));
            }
        } else {
            for (int block = 0; block * 12 < months; block++) {
                int from = block * 12, to = Math.min(months, from + 12) - 1;
                BigDecimal charge = BigDecimal.ZERO;
                for (int m = from; m <= to; m++) charge = charge.add(p.charge()[m]);
                total = total.add(charge);
                rows.add(row("period", "Year " + (block + 1) + " (" + first.plusMonths(from).format(MONTH) + " – "
                                + first.plusMonths(to).format(MONTH) + ")", "charge", money(charge),
                        "accumulated", money(p.accumulated()[to]), "bookValue", money(p.bookValue()[to]),
                        "assets", p.depreciating()[to]));
            }
        }
        String horizon = months == 12 ? "12 Months" : (months / 12) + " Years";
        List<Column> cols = List.of(col("period", months <= 12 ? "Month" : "Period", "text"), col("charge", "Projected Depreciation", "money"),
                col("accumulated", "Accumulated Depreciation (end)", "money"), col("bookValue", "Net Book Value (end)", "money"),
                col("assets", months <= 12 ? "Assets Depreciating" : "Assets Depreciating (end of period)", "number"));
        return table("Depreciation Forecast — " + horizon, "Projected depreciation expense over the next " + horizon.toLowerCase() + ".",
                cols, rows, row("period", "Total", "charge", money(total)),
                summary("Assets modelled", p.included(), "Held at current value", p.excluded()), projectionNotes(p));
    }

    /** Future NBV Projection: projected accumulated depreciation and carrying amount at each of the next year-ends. */
    public TabularReport futureNbv(ReportScope scope, String category, String department, Integer years) {
        int horizonYears = positive(years, 5, 15);
        LocalDate today = AppClock.today();
        YearMonth first = firstMonth();
        int lastYear = today.getYear() + horizonYears - 1;
        int months = (int) ChronoUnit.MONTHS.between(first, YearMonth.of(lastYear, 12)) + 1;
        List<Asset> assets = assetFinder.find(scope, category, department).stream().filter(this::notDisposed).toList();
        Projection p = project(assets, Math.max(1, months));

        List<Map<String, Object>> rows = new ArrayList<>();
        rows.add(row("date", "Today", "accumulated", money(p.openingAccumulated()), "bookValue", money(p.openingBook()),
                "change", money(BigDecimal.ZERO)));
        for (int y = today.getYear(); y <= lastYear; y++) {
            int idx = (int) ChronoUnit.MONTHS.between(first, YearMonth.of(y, 12));
            BigDecimal acc = idx < 0 ? p.openingAccumulated() : p.accumulated()[idx];
            BigDecimal book = idx < 0 ? p.openingBook() : p.bookValue()[idx];
            rows.add(row("date", "31 Dec " + y, "accumulated", money(acc), "bookValue", money(book),
                    "change", money(book.subtract(p.openingBook()))));
        }
        List<Column> cols = List.of(col("date", "As At", "text"), col("accumulated", "Projected Accumulated Depreciation", "money"),
                col("bookValue", "Projected Net Book Value", "money"), col("change", "Change from Today", "money"));
        return table("Future NBV Projection", "Projected carrying amounts at future year-ends.", cols, rows, null,
                summary("Assets modelled", p.included(), "Held at current value", p.excluded()), projectionNotes(p));
    }

    // ------------------------------------------------------- lifecycle forecasts

    /** Assets Reaching End-of-Life: assets whose useful life ends within the next {@code years} (default 2). */
    public TabularReport endOfLife(ReportScope scope, String category, String department, Integer years) {
        int horizon = positive(years, 2, 30);
        LocalDate today = AppClock.today();
        LocalDate limit = today.plusYears(horizon);
        List<Asset> assets = assetFinder.find(scope, category, department).stream().filter(this::active).toList();
        Map<Long, DepreciationParameters> policies = depreciationService.resolveParametersForAssets(assets);
        Map<Long, DepreciationPosting> postings = depreciationService.getLatestPostingsForAssets(assets.stream().map(Asset::getId).toList());

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Asset a : assets) {
            LocalDate end = endOfLife(a, policies.get(a.getId()));
            if (end == null || end.isBefore(today) || end.isAfter(limit)) continue;
            rows.add(row("assetCode", text(a.getAssetCode()), "assetName", text(a.getName()), "category", text(a.getCategory()),
                    "department", text(a.getDepartment()), "end", end.toString(),
                    "months", ChronoUnit.MONTHS.between(today, end), "cost", money(a.getPurchaseCost()), "nbv", money(nbvNow(a, postings))));
        }
        rows.sort(Comparator.comparing((Map<String, Object> m) -> String.valueOf(m.get("end"))));
        List<Column> cols = List.of(col("assetCode", "Asset Code", "text"), col("assetName", "Asset", "text"), col("category", "Category", "text"),
                col("department", "Department", "text"), col("end", "End of Useful Life", "date"), col("months", "Months Remaining", "number"),
                col("cost", "Cost", "money"), col("nbv", "Net Book Value", "money"));
        return table("Assets Reaching End-of-Life", "Assets whose useful life ends within the next " + horizon + " year(s).", cols, rows, null,
                summary("Assets", rows.size(), "Horizon (years)", horizon),
                List.of("End of life is the capitalisation (or purchase) date plus the useful life in the asset's depreciation policy. "
                        + "Assets already past their useful life are in the Assets Beyond Useful Life report."));
    }

    /** CAPEX Forecast: replacement of assets reaching end of life, plus a trend-based allowance for new spending. */
    public TabularReport capexForecast(ReportScope scope, String category, String department, Integer years, Double ratePercent) {
        int horizon = positive(years, 5, 15);
        double rate = rateOf(ratePercent);
        LocalDate today = AppClock.today();
        int firstYear = today.getYear();

        List<Asset> all = assetFinder.find(scope, category, department);
        List<Asset> active = all.stream().filter(this::active).toList();
        Map<Long, DepreciationParameters> policies = depreciationService.resolveParametersForAssets(active);

        BigDecimal pastCost = BigDecimal.ZERO;
        int pastCount = 0, noPolicy = 0;
        Map<Integer, BigDecimal> costByYear = new TreeMap<>();
        Map<Integer, Integer> countByYear = new TreeMap<>();
        for (Asset a : active) {
            LocalDate end = endOfLife(a, policies.get(a.getId()));
            if (end == null) {
                noPolicy++;
                continue;
            }
            int purchaseYear = a.getPurchaseDate() != null ? a.getPurchaseDate().getYear() : startDate(a).getYear();
            if (end.isBefore(today)) {
                pastCount++;
                pastCost = pastCost.add(grow(a.getPurchaseCost(), rate, firstYear - purchaseYear));
            } else if (end.getYear() < firstYear + horizon) {
                costByYear.merge(end.getYear(), grow(a.getPurchaseCost(), rate, end.getYear() - purchaseYear), BigDecimal::add);
                countByYear.merge(end.getYear(), 1, Integer::sum);
            }
        }

        // Trend allowance: average yearly acquisition spend and capital additions over the last three full years.
        BigDecimal history = BigDecimal.ZERO;
        for (Asset a : all) {
            if (a.getPurchaseDate() != null && a.getPurchaseDate().getYear() >= firstYear - 3 && a.getPurchaseDate().getYear() < firstYear) {
                history = history.add(nz(a.getPurchaseCost()));
            }
        }
        java.util.Set<Long> allIds = new java.util.HashSet<>();
        all.forEach(a -> allIds.add(a.getId()));
        for (CapitalAddition c : capitalAdditionRepository.findAll()) {
            if (c.getAsset() != null && allIds.contains(c.getAsset().getId()) && c.getDate() != null
                    && c.getDate().getYear() >= firstYear - 3 && c.getDate().getYear() < firstYear) {
                history = history.add(nz(c.getAmount()));
            }
        }
        BigDecimal trendBase = history.divide(BigDecimal.valueOf(3), 2, RoundingMode.HALF_UP);

        List<Map<String, Object>> rows = new ArrayList<>();
        BigDecimal replacementTotal = pastCost, trendTotal = BigDecimal.ZERO;
        int countTotal = pastCount;
        rows.add(row("period", "Now — already past useful life", "assets", pastCount, "replacement", money(pastCost),
                "trend", null, "total", money(pastCost)));
        for (int y = firstYear; y < firstYear + horizon; y++) {
            BigDecimal replacement = costByYear.getOrDefault(y, BigDecimal.ZERO);
            BigDecimal trend = grow(trendBase, rate, y - firstYear);
            int n = countByYear.getOrDefault(y, 0);
            replacementTotal = replacementTotal.add(replacement);
            trendTotal = trendTotal.add(trend);
            countTotal += n;
            rows.add(row("period", String.valueOf(y), "assets", n, "replacement", money(replacement), "trend", money(trend),
                    "total", money(replacement.add(trend))));
        }
        List<Column> cols = List.of(col("period", "Year", "text"), col("assets", "Assets to Replace", "number"),
                col("replacement", "Replacement CAPEX", "money"), col("trend", "Trend-Based New CAPEX", "money"),
                col("total", "Total Projected CAPEX", "money"));
        List<String> notes = new ArrayList<>();
        notes.add("Replacement CAPEX is the original cost of each asset reaching the end of its useful life, replaced in that year. " + notesRate(rate));
        notes.add("Trend-based new CAPEX is the average yearly acquisition cost plus capital additions over the last three full years"
                + (history.signum() == 0 ? " (there is no history to average, so it is zero)." : "."));
        if (noPolicy > 0) notes.add(noPolicy + " active asset(s) have no usable useful life or start date and are not forecast for replacement.");
        return table("CAPEX Forecast", "Projected capital expenditure over the next " + horizon + " years.", cols, rows,
                row("period", "Totals", "assets", countTotal, "replacement", money(replacementTotal), "trend", money(trendTotal),
                        "total", money(replacementTotal.add(trendTotal))),
                summary("Horizon (years)", horizon, "Cost inflation", String.format("%.1f%%", rate * 100)), notes);
    }

    /** Replacement Cost Exposure: assets past, or within {@code years} (default 3) of, end of life, by category. */
    public TabularReport replacementExposure(ReportScope scope, String category, String department, Integer years, Double ratePercent) {
        int horizon = positive(years, 3, 30);
        double rate = rateOf(ratePercent);
        LocalDate today = AppClock.today();
        LocalDate limit = today.plusYears(horizon);
        List<Asset> assets = assetFinder.find(scope, category, department).stream().filter(this::active).toList();
        Map<Long, DepreciationParameters> policies = depreciationService.resolveParametersForAssets(assets);
        Map<Long, DepreciationPosting> postings = depreciationService.getLatestPostingsForAssets(assets.stream().map(Asset::getId).toList());

        Map<String, Object[]> byCategory = new TreeMap<>(String.CASE_INSENSITIVE_ORDER); // past, soon, cost, replacement, nbv
        for (Asset a : assets) {
            LocalDate end = endOfLife(a, policies.get(a.getId()));
            if (end == null || end.isAfter(limit)) continue;
            String key = a.getCategory() == null || a.getCategory().isBlank() ? "Uncategorised" : a.getCategory();
            Object[] v = byCategory.computeIfAbsent(key, k -> new Object[]{0, 0, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO});
            if (end.isBefore(today)) v[0] = (Integer) v[0] + 1; else v[1] = (Integer) v[1] + 1;
            long age = a.getPurchaseDate() == null ? 0 : Math.max(0, today.getYear() - a.getPurchaseDate().getYear());
            v[2] = ((BigDecimal) v[2]).add(nz(a.getPurchaseCost()));
            v[3] = ((BigDecimal) v[3]).add(grow(a.getPurchaseCost(), rate, age));
            v[4] = ((BigDecimal) v[4]).add(nbvNow(a, postings));
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        int past = 0, soon = 0;
        BigDecimal cost = BigDecimal.ZERO, replacement = BigDecimal.ZERO, nbv = BigDecimal.ZERO;
        for (Map.Entry<String, Object[]> e : byCategory.entrySet()) {
            Object[] v = e.getValue();
            past += (Integer) v[0];
            soon += (Integer) v[1];
            cost = cost.add((BigDecimal) v[2]);
            replacement = replacement.add((BigDecimal) v[3]);
            nbv = nbv.add((BigDecimal) v[4]);
            rows.add(row("category", e.getKey(), "past", v[0], "soon", v[1], "cost", money((BigDecimal) v[2]),
                    "replacement", money((BigDecimal) v[3]), "nbv", money((BigDecimal) v[4]),
                    "exposure", money(((BigDecimal) v[3]).subtract((BigDecimal) v[4]))));
        }
        rows.sort(Comparator.comparing((Map<String, Object> m) -> (BigDecimal) m.get("replacement")).reversed());
        List<Column> cols = List.of(col("category", "Category", "text"), col("past", "Past Useful Life", "number"),
                col("soon", "Within " + horizon + " Year(s)", "number"), col("cost", "Original Cost", "money"),
                col("replacement", "Estimated Replacement Cost", "money"), col("nbv", "Net Book Value", "money"),
                col("exposure", "Exposure Above Carrying Amount", "money"));
        return table("Replacement Cost Exposure", "Estimated cost of replacing assets that are ageing or past their useful life.", cols, rows,
                rows.isEmpty() ? null : row("category", "Totals", "past", past, "soon", soon, "cost", money(cost),
                        "replacement", money(replacement), "nbv", money(nbv), "exposure", money(replacement.subtract(nbv))),
                summary("Horizon (years)", horizon, "Cost inflation", String.format("%.1f%%", rate * 100)),
                List.of("Estimated replacement cost is the original cost inflated from the purchase year to today. " + notesRate(rate),
                        "The system has no asset criticality rating, so exposure covers all assets past or near the end of their life."));
    }

    // ----------------------------------------------------------- analytical

    /** Maintenance Cost Forecast: next 12 months projected from the trend of the last 12 complete months. */
    public TabularReport maintenanceForecast(ReportScope scope, String category, String department) {
        YearMonth thisMonth = YearMonth.from(AppClock.today());
        YearMonth historyStart = thisMonth.minusMonths(12);
        java.util.Set<Long> ids = new java.util.HashSet<>();
        assetFinder.find(scope, category, department).forEach(a -> ids.add(a.getId()));

        double[] totals = new double[12];
        double preventive = 0, corrective = 0;
        for (MaintenanceRecord r : maintenanceRepository.findByMaintenanceDateBetweenOrderByMaintenanceDateDesc(
                historyStart.atDay(1), thisMonth.minusMonths(1).atEndOfMonth())) {
            if (r.getAsset() == null || !ids.contains(r.getAsset().getId()) || r.getMaintenanceDate() == null) continue;
            int idx = (int) ChronoUnit.MONTHS.between(historyStart, YearMonth.from(r.getMaintenanceDate()));
            if (idx < 0 || idx > 11) continue;
            double cost = nz(r.getMaintenanceCost()).doubleValue();
            totals[idx] += cost;
            if (r.getType() == MaintenanceType.PREVENTIVE) preventive += cost; else corrective += cost;
        }
        int monthsWithCost = 0;
        for (double t : totals) if (t > 0) monthsWithCost++;

        List<Column> cols = List.of(col("month", "Month", "text"), col("forecast", "Forecast Total", "money"),
                col("preventive", "Preventive (est.)", "money"), col("corrective", "Corrective (est.)", "money"));
        if (monthsWithCost < 3) {
            return table("Maintenance Cost Forecast", "Forecast of maintenance expenditure for the next 12 months.", cols, new ArrayList<>(), null,
                    summary("History used", monthsWithCost + " month(s) with maintenance cost"),
                    List.of("There is not enough maintenance history to forecast. At least three of the last twelve months need recorded maintenance cost."));
        }

        double meanX = 5.5, meanY = 0;
        for (double t : totals) meanY += t / 12.0;
        double covariance = 0, variance = 0;
        for (int i = 0; i < 12; i++) {
            covariance += (i - meanX) * (totals[i] - meanY);
            variance += (i - meanX) * (i - meanX);
        }
        double slope = variance == 0 ? 0 : covariance / variance;
        double intercept = meanY - slope * meanX;
        double total = preventive + corrective;
        double preventiveShare = total == 0 ? 0.5 : preventive / total;

        List<Map<String, Object>> rows = new ArrayList<>();
        BigDecimal sum = BigDecimal.ZERO;
        for (int k = 0; k < 12; k++) {
            double forecast = Math.max(0, intercept + slope * (12 + k));
            BigDecimal f = BigDecimal.valueOf(forecast).setScale(2, RoundingMode.HALF_UP);
            sum = sum.add(f);
            rows.add(row("month", thisMonth.plusMonths(k).format(MONTH), "forecast", f,
                    "preventive", BigDecimal.valueOf(forecast * preventiveShare).setScale(2, RoundingMode.HALF_UP),
                    "corrective", BigDecimal.valueOf(forecast * (1 - preventiveShare)).setScale(2, RoundingMode.HALF_UP)));
        }
        return table("Maintenance Cost Forecast", "Forecast of maintenance expenditure for the next 12 months.", cols, rows,
                row("month", "Total", "forecast", money(sum)),
                summary("History used", "last 12 complete months (" + monthsWithCost + " with cost)",
                        "Average monthly cost", String.format("%,.2f", meanY),
                        "Trend per month", String.format("%+,.2f", slope)),
                List.of("A straight-line trend is fitted to the last twelve complete months and extended forward; it never goes below zero.",
                        "The preventive and corrective split follows their share of the historical cost. This is an indication, not a budget."));
    }

    /** Asset Age Distribution: assets, cost and carrying amount by age band, with summary statistics. */
    public TabularReport ageDistribution(ReportScope scope, String category, String department) {
        LocalDate today = AppClock.today();
        List<Asset> assets = assetFinder.find(scope, category, department).stream().filter(this::active).toList();
        Map<Long, DepreciationPosting> postings = depreciationService.getLatestPostingsForAssets(assets.stream().map(Asset::getId).toList());

        String[] labels = {"Under 1 year", "1 to under 3 years", "3 to under 5 years", "5 to under 10 years", "10 years and over", "No purchase date"};
        int[] counts = new int[6];
        BigDecimal[] costs = zeros(6), books = zeros(6);
        List<Double> ages = new ArrayList<>();
        for (Asset a : assets) {
            LocalDate start = startDate(a);
            int band;
            if (start == null) {
                band = 5;
            } else {
                double age = Math.max(0, ChronoUnit.DAYS.between(start, today) / 365.25);
                ages.add(age);
                band = age < 1 ? 0 : age < 3 ? 1 : age < 5 ? 2 : age < 10 ? 3 : 4;
            }
            counts[band]++;
            costs[band] = costs[band].add(nz(a.getPurchaseCost()));
            books[band] = books[band].add(nbvNow(a, postings));
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        BigDecimal cost = BigDecimal.ZERO, nbv = BigDecimal.ZERO;
        for (int i = 0; i < 6; i++) {
            cost = cost.add(costs[i]);
            nbv = nbv.add(books[i]);
            rows.add(row("band", labels[i], "count", counts[i], "share", assets.isEmpty() ? "—"
                            : String.format("%.1f%%", counts[i] * 100.0 / assets.size()),
                    "cost", money(costs[i]), "nbv", money(books[i])));
        }
        ages.sort(Comparator.naturalOrder());
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("Active assets", String.valueOf(assets.size()));
        if (!ages.isEmpty()) {
            double mean = ages.stream().mapToDouble(Double::doubleValue).average().orElse(0);
            double median = ages.size() % 2 == 1 ? ages.get(ages.size() / 2)
                    : (ages.get(ages.size() / 2 - 1) + ages.get(ages.size() / 2)) / 2.0;
            summary.put("Average age", String.format("%.1f years", mean));
            summary.put("Median age", String.format("%.1f years", median));
            summary.put("Oldest asset", String.format("%.1f years", ages.get(ages.size() - 1)));
        }
        List<Column> cols = List.of(col("band", "Age Band", "text"), col("count", "Assets", "number"), col("share", "% of Assets", "text"),
                col("cost", "Original Cost", "money"), col("nbv", "Net Book Value", "money"));
        return table("Asset Age Distribution", "Distribution of active assets by age.", cols, rows,
                row("band", "Totals", "count", assets.size(), "cost", money(cost), "nbv", money(nbv)), summary,
                List.of("Age runs from the capitalisation date, or the purchase date if the asset was not capitalised."));
    }

    /** Asset Cost/Benefit Analysis: total cost of ownership against the utilisation data the system holds, by category. */
    public TabularReport costBenefit(ReportScope scope, String category, String department) {
        List<Asset> assets = assetFinder.find(scope, category, department).stream().filter(this::active).toList();
        Map<Long, Asset> byId = new HashMap<>();
        assets.forEach(a -> byId.put(a.getId(), a));

        Map<String, Object[]> byCategory = new TreeMap<>(String.CASE_INSENSITIVE_ORDER); // assets, cost, maintenance, checkouts, inUse
        for (Asset a : assets) {
            Object[] v = byCategory.computeIfAbsent(categoryKey(a), k -> new Object[]{0, BigDecimal.ZERO, BigDecimal.ZERO, 0, 0});
            v[0] = (Integer) v[0] + 1;
            v[1] = ((BigDecimal) v[1]).add(nz(a.getPurchaseCost()));
            String status = a.getStatus() == null ? "" : a.getStatus().trim().toLowerCase();
            if (status.equals("assigned") || status.equals("checked out") || status.equals("in use")) v[4] = (Integer) v[4] + 1;
        }
        for (MaintenanceRecord r : maintenanceRepository.findByMaintenanceDateBetweenOrderByMaintenanceDateDesc(
                LocalDate.of(1900, 1, 1), LocalDate.of(2999, 12, 31))) {
            Asset a = r.getAsset() == null ? null : byId.get(r.getAsset().getId());
            if (a == null) continue;
            Object[] v = byCategory.get(categoryKey(a));
            v[2] = ((BigDecimal) v[2]).add(nz(r.getMaintenanceCost()));
        }
        for (AssetCheckout c : checkoutRepository.findAll()) {
            Asset a = c.getAsset() == null ? null : byId.get(c.getAsset().getId());
            if (a == null) continue;
            Object[] v = byCategory.get(categoryKey(a));
            v[3] = (Integer) v[3] + 1;
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map.Entry<String, Object[]> e : byCategory.entrySet()) {
            Object[] v = e.getValue();
            BigDecimal tco = ((BigDecimal) v[1]).add((BigDecimal) v[2]);
            int checkouts = (Integer) v[3];
            rows.add(row("category", e.getKey(), "assets", v[0], "cost", money((BigDecimal) v[1]), "maintenance", money((BigDecimal) v[2]),
                    "tco", money(tco), "checkouts", checkouts,
                    "perCheckout", checkouts == 0 ? null : tco.divide(BigDecimal.valueOf(checkouts), 2, RoundingMode.HALF_UP),
                    "inUse", String.format("%.1f%%", (Integer) v[4] * 100.0 / (Integer) v[0])));
        }
        rows.sort(Comparator.comparing((Map<String, Object> m) -> (BigDecimal) m.get("tco")).reversed());
        List<Column> cols = List.of(col("category", "Category", "text"), col("assets", "Active Assets", "number"),
                col("cost", "Acquisition Cost", "money"), col("maintenance", "Maintenance Cost to Date", "money"),
                col("tco", "Total Cost of Ownership", "money"), col("checkouts", "Checkouts (all time)", "number"),
                col("perCheckout", "Cost per Checkout", "money"), col("inUse", "Currently In Use", "text"));
        return table("Asset Cost/Benefit Analysis", "Cost of ownership compared with the utilisation data available, by category.", cols, rows, null, null,
                List.of("The system holds no revenue or productivity data, so benefit is shown only as checkouts and the share of assets in use.",
                        "Cost per checkout is blank for categories that have never been checked out. Total cost of ownership is acquisition cost plus maintenance cost to date."));
    }

    private String categoryKey(Asset a) {
        return a.getCategory() == null || a.getCategory().isBlank() ? "Uncategorised" : a.getCategory();
    }
}
