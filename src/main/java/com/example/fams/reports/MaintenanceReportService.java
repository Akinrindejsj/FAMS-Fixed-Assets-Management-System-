package com.example.fams.reports;

import com.example.fams.assets.Asset;
import com.example.fams.assets.AssetRepository;
import com.example.fams.common.AppClock;
import com.example.fams.core.config.SecurityScopeService.ReportScope;
import com.example.fams.maintenance.MaintenanceRecord;
import com.example.fams.maintenance.MaintenanceRecordRepository;
import com.example.fams.maintenance.MaintenanceSchedule;
import com.example.fams.maintenance.MaintenanceScheduleRepository;
import com.example.fams.maintenance.MaintenanceStatus;
import com.example.fams.maintenance.MaintenanceTask;
import com.example.fams.maintenance.MaintenanceTaskRepository;
import com.example.fams.maintenance.MaintenanceType;
import com.example.fams.maintenance.ServiceContract;
import com.example.fams.maintenance.ServiceContractRepository;
import com.example.fams.reports.TabularReport.Column;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Maintenance reports built from maintenance records, scheduled tasks, schedules and
 * service contracts. Results follow the caller's report scope: department heads and
 * employees only see maintenance for assets they're entitled to see.
 *
 * <p>Costs are summed from maintenance records only. Resolving a preventive task also
 * copies its cost onto the task, so including tasks would count it twice.
 */
@Service
@Transactional(readOnly = true)
public class MaintenanceReportService {

    private static final LocalDate MIN_DATE = LocalDate.of(1900, 1, 1);
    private static final LocalDate MAX_DATE = LocalDate.of(2999, 12, 31);
    private static final List<MaintenanceStatus> OPEN_STATUSES = List.of(
            MaintenanceStatus.SCHEDULED, MaintenanceStatus.DUE, MaintenanceStatus.OPEN,
            MaintenanceStatus.IN_PROGRESS, MaintenanceStatus.OVERDUE);

    private final MaintenanceRecordRepository recordRepository;
    private final MaintenanceTaskRepository taskRepository;
    private final MaintenanceScheduleRepository scheduleRepository;
    private final ServiceContractRepository contractRepository;
    private final AssetRepository assetRepository;

    public MaintenanceReportService(MaintenanceRecordRepository recordRepository,
                                    MaintenanceTaskRepository taskRepository,
                                    MaintenanceScheduleRepository scheduleRepository,
                                    ServiceContractRepository contractRepository,
                                    AssetRepository assetRepository) {
        this.recordRepository = recordRepository;
        this.taskRepository = taskRepository;
        this.scheduleRepository = scheduleRepository;
        this.contractRepository = contractRepository;
        this.assetRepository = assetRepository;
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
        col("location", "Location", "text");
        col("type", "Type", "text");
        col("issue", "Description", "text");
        col("provider", "Service Provider", "text");
        col("maintenanceDate", "Date", "date");
        col("resolutionDate", "Resolved", "date");
        col("status", "Status", "text");
        col("cost", "Cost", "money");
        col("serviceType", "Service", "text");
        col("dueDate", "Due Date", "date");
        col("responsible", "Responsible", "text");
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

    private BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    private String iso(LocalDate date) {
        return date == null ? "" : date.toString();
    }

    private LocalDate from(LocalDate date) {
        return date == null ? MIN_DATE : date;
    }

    private LocalDate to(LocalDate date) {
        return date == null ? MAX_DATE : date;
    }

    /** Asset ids the caller may see, or {@code null} when the scope is unrestricted. */
    private Set<Long> allowedAssetIds(ReportScope scope) {
        if (scope == null || scope.isGlobal()) {
            return null;
        }
        if (scope.isEmptyRestriction()) {
            return Set.of();
        }
        List<Asset> assets;
        if (scope.isDepartment()) {
            assets = assetRepository.findByDepartmentInIgnoreCaseOrderByCreatedAtDesc(
                    scope.departments().stream().map(String::toLowerCase).collect(Collectors.toList()));
        } else {
            assets = assetRepository.findByCustodianInIgnoreCaseOrderByCreatedAtDesc(scope.custodians());
        }
        return assets.stream().map(Asset::getId).collect(Collectors.toSet());
    }

    private boolean visible(Set<Long> allowed, Asset asset) {
        return allowed == null || (asset != null && allowed.contains(asset.getId()));
    }

    private List<MaintenanceRecord> records(ReportScope scope, LocalDate from, LocalDate to) {
        Set<Long> allowed = allowedAssetIds(scope);
        return recordRepository.findByMaintenanceDateBetweenOrderByMaintenanceDateDesc(from(from), to(to)).stream()
                .filter(r -> visible(allowed, r.getAsset()))
                .toList();
    }

    private Map<String, Object> row(Object... keyValues) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            row.put((String) keyValues[i], keyValues[i + 1]);
        }
        return row;
    }

    private TabularReport table(String title, String description, List<Column> columns, List<Map<String, Object>> rows,
                                Map<String, Object> totals, Map<String, Object> summary, List<String> notes) {
        return new TabularReport(title, description, AppClock.today(), columns, rows,
                totals == null ? Map.<String, Object>of() : totals,
                summary == null ? Map.<String, Object>of() : summary,
                notes == null ? List.<String>of() : notes, rows.isEmpty());
    }

    private Map<String, Object> summary(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], String.valueOf(keyValues[i + 1]));
        }
        return map;
    }

    private BigDecimal costOf(MaintenanceRecord r) {
        return r.getMaintenanceCost() == null ? BigDecimal.ZERO : r.getMaintenanceCost();
    }

    private Map<String, Object> recordRow(MaintenanceRecord r) {
        Asset a = r.getAsset();
        return row("assetCode", a.getAssetCode(), "assetName", a.getName(), "category", nz(a.getCategory()),
                "type", r.getType().name(), "issue", nz(r.getIssueDescription()), "provider", nz(r.getServiceProvider()),
                "maintenanceDate", iso(r.getMaintenanceDate()), "resolutionDate", iso(r.getResolutionDate()),
                "status", r.getStatus().name(), "cost", money(r.getMaintenanceCost()));
    }

    // ------------------------------------------------------------------ reports

    /** Maintenance History: every maintenance and repair record, grouped by asset. */
    public TabularReport history(ReportScope scope, LocalDate from, LocalDate to) {
        List<MaintenanceRecord> records = records(scope, from, to).stream()
                .sorted(Comparator.comparing((MaintenanceRecord r) -> nz(r.getAsset().getAssetCode()))
                        .thenComparing(MaintenanceRecord::getMaintenanceDate, Comparator.reverseOrder()))
                .toList();
        List<Map<String, Object>> rows = records.stream().map(this::recordRow).toList();
        BigDecimal total = records.stream().map(this::costOf).reduce(BigDecimal.ZERO, BigDecimal::add);
        return table("Asset Maintenance History", "Complete maintenance and repair history by asset.",
                columns("assetCode", "assetName", "category", "type", "issue", "provider", "maintenanceDate",
                        "resolutionDate", "status", "cost"),
                rows, rows.isEmpty() ? null : row("issue", "Total", "cost", money(total)),
                summary("Events", rows.size(), "Total cost", total.setScale(2, RoundingMode.HALF_UP)), null);
    }

    /** Scheduled Maintenance: all preventive schedules. */
    public TabularReport scheduled(ReportScope scope) {
        Set<Long> allowed = allowedAssetIds(scope);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (MaintenanceSchedule s : scheduleRepository.findAllByOrderByNextDueDateAsc()) {
            if (s.getAsset() == null ? allowed != null : !visible(allowed, s.getAsset())) continue;
            rows.add(row("assetCode", s.getAsset() == null ? "—" : s.getAsset().getAssetCode(),
                    "assetName", s.getAsset() == null ? "All assets in category " + nz(s.getAssetCategory()) : s.getAsset().getName(),
                    "serviceType", s.getServiceType(), "frequency", s.getFrequency().name(),
                    "startDate", iso(s.getStartDate()), "dueDate", iso(s.getNextDueDate()),
                    "responsible", s.getResponsibleParty() + " (" + s.getResponsibleRole() + ")",
                    "status", s.getStatus().name()));
        }
        List<Column> cols = List.of(COLUMNS.get("assetCode"), COLUMNS.get("assetName"), COLUMNS.get("serviceType"),
                new Column("frequency", "Frequency", "text"), new Column("startDate", "Start Date", "date"),
                new Column("dueDate", "Next Due", "date"), COLUMNS.get("responsible"), COLUMNS.get("status"));
        return table("Scheduled Maintenance Report", "Planned preventive maintenance activities.", cols, rows, null,
                summary("Schedules", rows.size()), null);
    }

    private List<Map<String, Object>> dueRows(ReportScope scope, LocalDate lower, LocalDate upper, boolean overdueOnly) {
        Set<Long> allowed = allowedAssetIds(scope);
        LocalDate today = AppClock.today();
        List<Map<String, Object>> rows = new ArrayList<>();
        Set<String> openTaskKeys = new HashSet<>();

        for (MaintenanceTask t : taskRepository.findAllByOrderByDueDateAsc()) {
            if (!OPEN_STATUSES.contains(t.getStatus())) continue;
            if (t.getSchedule() != null) openTaskKeys.add(t.getSchedule().getId() + "|" + t.getDueDate());
            if (!visible(allowed, t.getAsset())) continue;
            if (t.getDueDate().isBefore(lower) || t.getDueDate().isAfter(upper)) continue;
            rows.add(dueRow("Task", t.getAsset(), t.getServiceType(), t.getDueDate(), t.getResponsibleParty(),
                    t.getStatus().name(), today));
        }
        for (MaintenanceSchedule s : scheduleRepository.findAllByOrderByNextDueDateAsc()) {
            if (s.getAsset() == null ? allowed != null : !visible(allowed, s.getAsset())) continue;
            if (s.getNextDueDate().isBefore(lower) || s.getNextDueDate().isAfter(upper)) continue;
            if (openTaskKeys.contains(s.getId() + "|" + s.getNextDueDate())) continue;
            if (overdueOnly && !s.getNextDueDate().isBefore(today)) continue;
            rows.add(dueRow("Schedule", s.getAsset(), s.getServiceType(), s.getNextDueDate(), s.getResponsibleParty(),
                    s.getStatus().name(), today));
        }
        rows.sort(Comparator.comparing(m -> String.valueOf(m.get("dueDate"))));
        return rows;
    }

    private Map<String, Object> dueRow(String source, Asset asset, String serviceType, LocalDate due,
                                       String responsible, String status, LocalDate today) {
        return row("source", source, "assetCode", asset == null ? "—" : asset.getAssetCode(),
                "assetName", asset == null ? "Category-wide schedule" : asset.getName(),
                "category", asset == null ? "" : nz(asset.getCategory()),
                "serviceType", serviceType, "dueDate", due.toString(), "responsible", responsible, "status", status,
                "days", ChronoUnit.DAYS.between(today, due));
    }

    /** Maintenance Due: open tasks and upcoming schedules due within the period (defaults to the next 30 days). */
    public TabularReport due(ReportScope scope, LocalDate from, LocalDate to) {
        LocalDate lower = from != null ? from : AppClock.today();
        LocalDate upper = to != null ? to : lower.plusDays(30);
        List<Map<String, Object>> rows = dueRows(scope, lower, upper, false);
        List<Column> cols = List.of(new Column("source", "Source", "text"), COLUMNS.get("assetCode"), COLUMNS.get("assetName"),
                COLUMNS.get("category"), COLUMNS.get("serviceType"), COLUMNS.get("dueDate"),
                new Column("days", "Days Until Due", "number"), COLUMNS.get("responsible"), COLUMNS.get("status"));
        return table("Maintenance Due Report", "Maintenance due between " + lower + " and " + upper + ".", cols, rows, null,
                summary("Items due", rows.size()), null);
    }

    /** Overdue Maintenance: open tasks, and schedules whose next due date has passed. */
    public TabularReport overdue(ReportScope scope) {
        LocalDate today = AppClock.today();
        List<Map<String, Object>> rows = dueRows(scope, MIN_DATE, today.minusDays(1), true);
        rows.forEach(m -> m.put("days", -((Long) m.get("days"))));
        List<Column> cols = List.of(new Column("source", "Source", "text"), COLUMNS.get("assetCode"), COLUMNS.get("assetName"),
                COLUMNS.get("category"), COLUMNS.get("serviceType"), COLUMNS.get("dueDate"),
                new Column("days", "Days Overdue", "number"), COLUMNS.get("responsible"), COLUMNS.get("status"));
        rows.sort(Comparator.comparing((Map<String, Object> m) -> (Long) m.get("days")).reversed());
        return table("Overdue Maintenance Report", "Maintenance that has passed its due date and is not completed.", cols, rows,
                null, summary("Overdue items", rows.size()),
                List.of("Overdue is worked out from the due date; the OVERDUE status itself is not stored by the system."));
    }

    private TabularReport costBy(String title, String description, String groupLabel, ReportScope scope, LocalDate from,
                                 LocalDate to, Function<MaintenanceRecord, String> key, boolean byAsset) {
        Map<String, List<MaintenanceRecord>> groups = records(scope, from, to).stream()
                .collect(Collectors.groupingBy(key, LinkedHashMap::new, Collectors.toList()));
        List<Column> cols = new ArrayList<>();
        cols.add(new Column("group", groupLabel, "text"));
        if (byAsset) cols.add(COLUMNS.get("category"));
        cols.add(new Column("preventive", "Preventive Cost", "money"));
        cols.add(new Column("corrective", "Corrective Cost", "money"));
        cols.add(new Column("total", "Total Cost", "money"));
        cols.add(new Column("events", "Events", "number"));

        List<Map<String, Object>> rows = new ArrayList<>();
        BigDecimal preventiveTotal = BigDecimal.ZERO, correctiveTotal = BigDecimal.ZERO;
        long events = 0;
        for (Map.Entry<String, List<MaintenanceRecord>> e : groups.entrySet()) {
            BigDecimal preventive = e.getValue().stream().filter(r -> r.getType() == MaintenanceType.PREVENTIVE)
                    .map(this::costOf).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal corrective = e.getValue().stream().filter(r -> r.getType() == MaintenanceType.CORRECTIVE)
                    .map(this::costOf).reduce(BigDecimal.ZERO, BigDecimal::add);
            preventiveTotal = preventiveTotal.add(preventive);
            correctiveTotal = correctiveTotal.add(corrective);
            events += e.getValue().size();
            Map<String, Object> r = row("group", e.getKey());
            if (byAsset) r.put("category", nz(e.getValue().get(0).getAsset().getCategory()));
            r.put("preventive", money(preventive));
            r.put("corrective", money(corrective));
            r.put("total", money(preventive.add(corrective)));
            r.put("events", e.getValue().size());
            rows.add(r);
        }
        rows.sort(Comparator.comparing((Map<String, Object> m) -> (BigDecimal) m.get("total")).reversed());
        Map<String, Object> totals = rows.isEmpty() ? null : row("group", "Totals", "preventive", money(preventiveTotal),
                "corrective", money(correctiveTotal), "total", money(preventiveTotal.add(correctiveTotal)), "events", events);
        return table(title, description, cols, rows, totals, null, null);
    }

    public TabularReport costByAsset(ReportScope scope, LocalDate from, LocalDate to) {
        return costBy("Maintenance Cost by Asset", "Total maintenance expenditure attributable to each asset.", "Asset",
                scope, from, to, r -> r.getAsset().getAssetCode() + " — " + r.getAsset().getName(), true);
    }

    public TabularReport costByCategory(ReportScope scope, LocalDate from, LocalDate to) {
        return costBy("Maintenance Cost by Category", "Maintenance expenditure by asset class.", "Category",
                scope, from, to, r -> r.getAsset().getCategory() == null ? "Uncategorised" : r.getAsset().getCategory(), false);
    }

    public TabularReport costByLocation(ReportScope scope, LocalDate from, LocalDate to) {
        return costBy("Maintenance Cost by Location", "Maintenance expenditure by branch/site.", "Branch",
                scope, from, to, r -> r.getAsset().getBranch() == null || r.getAsset().getBranch().isBlank()
                        ? "Unspecified" : r.getAsset().getBranch(), false);
    }

    /** Breakdown/Repair History: corrective maintenance events. */
    public TabularReport breakdownHistory(ReportScope scope, LocalDate from, LocalDate to) {
        List<MaintenanceRecord> corrective = records(scope, from, to).stream()
                .filter(r -> r.getType() == MaintenanceType.CORRECTIVE).toList();
        List<Map<String, Object>> rows = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (MaintenanceRecord r : corrective) {
            Asset a = r.getAsset();
            LocalDate logged = r.getCreatedAt().toLocalDate();
            total = total.add(costOf(r));
            rows.add(row("assetCode", a.getAssetCode(), "assetName", a.getName(), "category", nz(a.getCategory()),
                    "logged", logged.toString(), "issue", nz(r.getIssueDescription()), "provider", nz(r.getServiceProvider()),
                    "resolutionDate", iso(r.getResolutionDate()),
                    "days", r.getResolutionDate() == null ? null : Math.max(0, ChronoUnit.DAYS.between(logged, r.getResolutionDate())),
                    "status", r.getStatus().name(), "cost", money(r.getMaintenanceCost())));
        }
        List<Column> cols = List.of(COLUMNS.get("assetCode"), COLUMNS.get("assetName"), COLUMNS.get("category"),
                new Column("logged", "Logged On", "date"), COLUMNS.get("issue"), COLUMNS.get("provider"),
                COLUMNS.get("resolutionDate"), new Column("days", "Days to Resolve", "number"), COLUMNS.get("status"),
                COLUMNS.get("cost"));
        return table("Breakdown / Repair History", "History of failures, breakdowns and corrective repairs.", cols, rows,
                rows.isEmpty() ? null : row("issue", "Total", "cost", money(total)),
                summary("Breakdowns", rows.size(), "Total repair cost", total.setScale(2, RoundingMode.HALF_UP)),
                List.of("Days to resolve is measured from when the event was logged, which for events entered after the fact is the entry date."));
    }

    /**
     * Asset Downtime: corrective events with how long the asset was unavailable. Uses the
     * recorded unavailable-from / available-again dates where given, otherwise estimates
     * from when the event was logged to when it was resolved (or today, if still open).
     */
    public TabularReport downtime(ReportScope scope, LocalDate from, LocalDate to) {
        LocalDate today = AppClock.today();
        List<Map<String, Object>> rows = new ArrayList<>();
        long recordedDays = 0, estimatedDays = 0;
        for (MaintenanceRecord r : records(scope, from, to)) {
            if (r.getType() != MaintenanceType.CORRECTIVE && r.getDowntimeStart() == null) continue;
            boolean recorded = r.getDowntimeStart() != null;
            LocalDate start = recorded ? r.getDowntimeStart() : r.getCreatedAt().toLocalDate();
            LocalDate end = r.getDowntimeEnd() != null ? r.getDowntimeEnd()
                    : (r.getResolutionDate() != null ? r.getResolutionDate() : today);
            long days = Math.max(0, ChronoUnit.DAYS.between(start, end));
            if (recorded) recordedDays += days; else estimatedDays += days;
            Asset a = r.getAsset();
            rows.add(row("assetCode", a.getAssetCode(), "assetName", a.getName(), "category", nz(a.getCategory()),
                    "issue", nz(r.getIssueDescription()), "start", start.toString(),
                    "end", r.getDowntimeEnd() == null && r.getResolutionDate() == null ? "Ongoing" : end.toString(),
                    "days", days, "basis", recorded ? "Recorded" : "Estimated", "status", r.getStatus().name()));
        }
        List<Column> cols = List.of(COLUMNS.get("assetCode"), COLUMNS.get("assetName"), COLUMNS.get("category"),
                COLUMNS.get("issue"), new Column("start", "Unavailable From", "date"),
                new Column("end", "Available Again", "date"), new Column("days", "Days Down", "number"),
                new Column("basis", "Basis", "text"), COLUMNS.get("status"));
        return table("Asset Downtime Report", "Periods assets were unavailable due to maintenance or failure.", cols, rows, null,
                summary("Events", rows.size(), "Recorded downtime (days)", recordedDays, "Estimated downtime (days)", estimatedDays),
                List.of("Estimated rows measure from when the event was logged to its resolution, not actual unavailability. "
                        + "Enter 'Unavailable From' and 'Available Again' when logging corrective work for exact figures."));
    }

    /** Service Contract Expiry: agreements with days to expiry, soonest first. */
    public TabularReport contractExpiry(ReportScope scope, LocalDate from, LocalDate to) {
        Set<Long> allowed = allowedAssetIds(scope);
        LocalDate today = AppClock.today();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ServiceContract c : contractRepository.findAllByOrderByEndDateAsc()) {
            if (c.getAsset() == null ? allowed != null : !visible(allowed, c.getAsset())) continue;
            if (c.getEndDate().isBefore(from(from)) || c.getEndDate().isAfter(to(to))) continue;
            long days = ChronoUnit.DAYS.between(today, c.getEndDate());
            String status = days < 0 ? "Expired" : days <= 30 ? "Expires within 30 days"
                    : days <= 90 ? "Expires within 90 days" : "Active";
            rows.add(row("contract", c.getContractNumber(), "vendor", c.getVendor(),
                    "asset", c.getAsset() == null ? "Not asset-specific" : c.getAsset().getAssetCode() + " — " + c.getAsset().getName(),
                    "coverage", nz(c.getCoverage()), "start", c.getStartDate().toString(), "end", c.getEndDate().toString(),
                    "days", days, "status", status, "annualCost", c.getAnnualCost() == null ? null : money(c.getAnnualCost())));
        }
        List<Column> cols = List.of(new Column("contract", "Contract", "text"), new Column("vendor", "Vendor", "text"),
                new Column("asset", "Asset", "text"), new Column("coverage", "Coverage", "text"),
                new Column("start", "Start", "date"), new Column("end", "End", "date"),
                new Column("days", "Days to Expiry", "number"), COLUMNS.get("status"),
                new Column("annualCost", "Annual Cost", "money"));
        return table("Service Contract Expiry Report", "Maintenance and service agreements approaching or past expiry.", cols,
                rows, null, summary("Contracts", rows.size()),
                List.of("Negative days mean the contract has already expired."));
    }

    /**
     * High-Maintenance-Cost Assets: assets whose total maintenance cost is at least
     * {@code multiple} times the average for their category (assets with maintenance cost only).
     */
    public TabularReport highCostAssets(ReportScope scope, LocalDate from, LocalDate to, double multiple) {
        Map<Asset, BigDecimal> totals = new LinkedHashMap<>();
        Map<Asset, Integer> events = new LinkedHashMap<>();
        for (MaintenanceRecord r : records(scope, from, to)) {
            if (r.getMaintenanceCost() == null || r.getMaintenanceCost().signum() == 0) continue;
            totals.merge(r.getAsset(), r.getMaintenanceCost(), BigDecimal::add);
            events.merge(r.getAsset(), 1, Integer::sum);
        }
        Map<String, BigDecimal> averageByCategory = new LinkedHashMap<>();
        totals.entrySet().stream()
                .collect(Collectors.groupingBy(e -> nz(e.getKey().getCategory()), Collectors.toList()))
                .forEach((category, entries) -> averageByCategory.put(category, entries.stream().map(Map.Entry::getValue)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(entries.size()), 2, RoundingMode.HALF_UP)));

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map.Entry<Asset, BigDecimal> e : totals.entrySet()) {
            BigDecimal average = averageByCategory.get(nz(e.getKey().getCategory()));
            if (average.signum() == 0) continue;
            double ratio = e.getValue().doubleValue() / average.doubleValue();
            if (ratio < multiple) continue;
            rows.add(row("assetCode", e.getKey().getAssetCode(), "assetName", e.getKey().getName(),
                    "category", nz(e.getKey().getCategory()), "total", money(e.getValue()),
                    "events", events.get(e.getKey()), "average", money(average),
                    "ratio", String.format("%.1f×", ratio)));
        }
        rows.sort(Comparator.comparing((Map<String, Object> m) -> (BigDecimal) m.get("total")).reversed());
        List<Column> cols = List.of(COLUMNS.get("assetCode"), COLUMNS.get("assetName"), COLUMNS.get("category"),
                new Column("total", "Total Maintenance Cost", "money"), new Column("events", "Events", "number"),
                new Column("average", "Category Average", "money"), new Column("ratio", "Times Average", "text"));
        return table("High-Maintenance-Cost Assets", "Assets whose maintenance cost is " + multiple
                        + "× or more of their category average.", cols, rows, null,
                summary("Assets flagged", rows.size()),
                List.of("The average is taken across assets in the same category that have maintenance cost in the period."));
    }
}
