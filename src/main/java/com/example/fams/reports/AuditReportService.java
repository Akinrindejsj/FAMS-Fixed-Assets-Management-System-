package com.example.fams.reports;

import com.example.fams.assets.Asset;
import com.example.fams.assets.AssetRepository;
import com.example.fams.audit.AuditScanLog;
import com.example.fams.audit.AuditScanLogRepository;
import com.example.fams.audit.AuditService;
import com.example.fams.audit.AuditSession;
import com.example.fams.audit.AuditSessionExpectedAsset;
import com.example.fams.audit.AuditSessionExpectedAssetRepository;
import com.example.fams.audit.AuditSessionRepository;
import com.example.fams.audit.AuditSessionStatus;
import com.example.fams.audit.AuditVerificationResult;
import com.example.fams.audit.AuditVerificationResultRepository;
import com.example.fams.audit.AuditVerificationStatus;
import com.example.fams.common.AppClock;
import com.example.fams.reports.TabularReport.Column;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Physical verification reports built from audit sessions, their verification results,
 * the scan log and the expected-asset snapshot taken when each session started.
 *
 * <p>Sessions started before the snapshot existed have no stored population, so for those
 * the expected assets are recomputed from today's register and scope — counts for old
 * sessions can drift if assets have since moved or been disposed.
 */
@Service
@Transactional(readOnly = true)
public class AuditReportService {

    private static final LocalDate MIN_DATE = LocalDate.of(1900, 1, 1);
    private static final LocalDate MAX_DATE = LocalDate.of(2999, 12, 31);
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final AuditSessionRepository sessionRepository;
    private final AuditVerificationResultRepository resultRepository;
    private final AuditSessionExpectedAssetRepository expectedAssetRepository;
    private final AuditScanLogRepository scanLogRepository;
    private final AssetRepository assetRepository;
    private final AuditService auditService;

    public AuditReportService(AuditSessionRepository sessionRepository,
                              AuditVerificationResultRepository resultRepository,
                              AuditSessionExpectedAssetRepository expectedAssetRepository,
                              AuditScanLogRepository scanLogRepository,
                              AssetRepository assetRepository,
                              AuditService auditService) {
        this.sessionRepository = sessionRepository;
        this.resultRepository = resultRepository;
        this.expectedAssetRepository = expectedAssetRepository;
        this.scanLogRepository = scanLogRepository;
        this.assetRepository = assetRepository;
        this.auditService = auditService;
    }

    // ------------------------------------------------------------------ columns

    private static final Map<String, Column> COLUMNS = new LinkedHashMap<>();

    private static void col(String key, String label, String type) {
        COLUMNS.put(key, new Column(key, label, type));
    }

    static {
        col("session", "Audit Session", "text");
        col("assetCode", "Asset Code", "text");
        col("assetName", "Asset", "text");
        col("category", "Category", "text");
        col("expectedLocation", "Expected Location", "text");
        col("actualLocation", "Actual Location", "text");
        col("expectedCustodian", "Expected Custodian", "text");
        col("actualCustodian", "Custodian Found", "text");
        col("registerStatus", "Register Status", "text");
        col("result", "Result", "text");
        col("discrepancy", "Discrepancy", "text");
        col("condition", "Condition", "text");
        col("conditionNotes", "Condition Notes", "text");
        col("scanMethod", "Scan Method", "text");
        col("scannedCode", "Scanned Code", "text");
        col("verifiedBy", "Verified By", "text");
        col("verifiedAt", "Verified At", "date");
    }

    private List<Column> columns(String... keys) {
        List<Column> list = new ArrayList<>();
        for (String key : keys) {
            list.add(COLUMNS.get(key));
        }
        return list;
    }

    // ------------------------------------------------------------------ helpers

    private String nz(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String stamp(LocalDateTime time) {
        return time == null ? "" : time.format(STAMP);
    }

    private String location(Asset asset) {
        return java.util.stream.Stream.of(asset.getBranch(), asset.getDepartment())
                .filter(Objects::nonNull).filter(v -> !v.isBlank()).collect(Collectors.joining(" / "));
    }

    private Map<String, Object> resultRow(AuditVerificationResult r) {
        Asset asset = r.getAsset();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("session", r.getSession().getTitle());
        row.put("assetCode", asset.getAssetCode());
        row.put("assetName", asset.getName());
        row.put("category", nz(asset.getCategory()));
        row.put("expectedLocation", nz(r.getExpectedLocation()));
        row.put("actualLocation", nz(r.getActualLocation()));
        row.put("expectedCustodian", nz(r.getExpectedCustodian()));
        row.put("actualCustodian", nz(r.getActualCustodian()));
        row.put("registerStatus", nz(r.getRegisterStatus()));
        row.put("result", r.getResultStatus().name());
        row.put("discrepancy", nz(r.getDiscrepancyType()));
        row.put("condition", r.getConditionRating() == null ? "" : r.getConditionRating().name());
        row.put("conditionNotes", nz(r.getConditionNotes()));
        row.put("scanMethod", r.getScanMethod() == null ? "" : r.getScanMethod().name());
        row.put("scannedCode", nz(r.getScannedCode()));
        row.put("verifiedBy", nz(r.getVerifiedBy()));
        row.put("verifiedAt", stamp(r.getVerifiedAt()));
        return row;
    }

    private TabularReport table(String title, String description, List<Column> columns, List<Map<String, Object>> rows,
                                Map<String, Object> summary, List<String> notes) {
        return new TabularReport(title, description, AppClock.today(), columns, rows, Map.<String, Object>of(),
                summary == null ? Map.<String, Object>of() : summary, notes == null ? List.<String>of() : notes, rows.isEmpty());
    }

    private Map<String, Object> summary(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], String.valueOf(keyValues[i + 1]));
        }
        return map;
    }

    /** Verification results for one session, or for every session within a date range. */
    private List<AuditVerificationResult> results(Long sessionId, LocalDate from, LocalDate to) {
        if (sessionId != null) {
            return resultRepository.findBySessionFetched(sessionId);
        }
        LocalDate start = from != null ? from : MIN_DATE;
        LocalDate end = to != null ? to : MAX_DATE;
        return resultRepository.findInRangeFetched(start.atStartOfDay(), end.atTime(LocalTime.MAX));
    }

    /** The given session, or the active one, or the most recent. */
    private AuditSession sessionOrDefault(Long sessionId) {
        if (sessionId != null) {
            return sessionRepository.findById(sessionId)
                    .orElseThrow(() -> new IllegalArgumentException("Audit session not found."));
        }
        List<AuditSession> active = sessionRepository.findByStatusOrderByStartedAtDesc(AuditSessionStatus.ACTIVE);
        if (!active.isEmpty()) return active.get(0);
        return sessionRepository.findAllByOrderByStartedAtDesc().stream().findFirst()
                .orElseThrow(() -> new IllegalArgumentException("There are no audit sessions yet."));
    }

    private boolean hasSnapshot(AuditSession session) {
        return expectedAssetRepository.existsBySessionId(session.getId());
    }

    /** Expected assets for a session: the stored snapshot, else recomputed from the register. */
    private List<AuditSessionExpectedAsset> expectedAssets(AuditSession session) {
        if (hasSnapshot(session)) {
            return expectedAssetRepository.findBySession(session.getId());
        }
        List<AuditSessionExpectedAsset> live = new ArrayList<>();
        for (Asset asset : assetRepository.findAll()) {
            if ("Disposed".equalsIgnoreCase(asset.getStatus()) || !auditService.isInScope(session, asset)) continue;
            AuditSessionExpectedAsset e = new AuditSessionExpectedAsset();
            e.setSession(session);
            e.setAsset(asset);
            e.setExpectedLocation(location(asset));
            e.setExpectedCustodian(asset.getCustodian());
            live.add(e);
        }
        return live;
    }

    public List<Map<String, Object>> sessionOptions() {
        return sessionRepository.findAllByOrderByStartedAtDesc().stream().map(s -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", s.getId());
            m.put("label", s.getTitle() + " (" + s.getStartedAt().toLocalDate() + ", " + s.getStatus().name().toLowerCase() + ")");
            return m;
        }).toList();
    }

    // ------------------------------------------------- result-based reports

    private TabularReport resultReport(String title, String description, List<String> keys, Long sessionId,
                                       LocalDate from, LocalDate to, Predicate<AuditVerificationResult> filter,
                                       List<String> notes) {
        List<Map<String, Object>> rows = results(sessionId, from, to).stream().filter(filter)
                .map(this::resultRow).toList();
        return table(title, description, columns(keys.toArray(new String[0])), rows,
                summary("Records", rows.size()), notes);
    }

    public TabularReport verificationReport(Long sessionId, LocalDate from, LocalDate to) {
        return resultReport("Physical Verification Report", "Every asset verification recorded in the selected audits.",
                List.of("session", "assetCode", "assetName", "expectedLocation", "actualLocation", "registerStatus",
                        "result", "discrepancy", "condition", "verifiedBy", "verifiedAt"),
                sessionId, from, to, r -> true, null);
    }

    public TabularReport verifiedAssets(Long sessionId, LocalDate from, LocalDate to) {
        return resultReport("Verified Assets Report", "Assets found where expected, with no discrepancy.",
                List.of("session", "assetCode", "assetName", "expectedLocation", "actualLocation", "verifiedBy", "verifiedAt"),
                sessionId, from, to,
                r -> r.getResultStatus() == AuditVerificationStatus.FOUND && r.getDiscrepancyType() == null, null);
    }

    public TabularReport missingAssets(Long sessionId, LocalDate from, LocalDate to) {
        return resultReport("Missing Assets Report", "Assets that could not be found during verification.",
                List.of("session", "assetCode", "assetName", "category", "expectedLocation", "expectedCustodian",
                        "registerStatus", "conditionNotes", "verifiedBy", "verifiedAt"),
                sessionId, from, to, r -> r.getResultStatus() == AuditVerificationStatus.MISSING, null);
    }

    public TabularReport wrongLocation(Long sessionId, LocalDate from, LocalDate to) {
        return resultReport("Wrong Location Report", "Assets found somewhere other than their recorded location.",
                List.of("session", "assetCode", "assetName", "expectedLocation", "actualLocation", "verifiedBy", "verifiedAt"),
                sessionId, from, to, r -> r.getResultStatus() == AuditVerificationStatus.LOCATION_MISMATCH, null);
    }

    public TabularReport custodianMismatch(Long sessionId, LocalDate from, LocalDate to) {
        return resultReport("Custodian Mismatch Report", "Assets held by someone other than the custodian in the register.",
                List.of("session", "assetCode", "assetName", "expectedCustodian", "actualCustodian", "expectedLocation",
                        "verifiedBy", "verifiedAt"),
                sessionId, from, to, AuditVerificationResult::isCustodianMismatch,
                List.of("Only verifications where a custodian was entered are compared. Results recorded before custodian capture are not included."));
    }

    public TabularReport unexpectedAssets(Long sessionId, LocalDate from, LocalDate to) {
        return resultReport("Unexpected Assets Report", "Assets verified in an audit whose scope did not include them.",
                List.of("session", "assetCode", "assetName", "category", "expectedLocation", "actualLocation",
                        "registerStatus", "verifiedBy", "verifiedAt"),
                sessionId, from, to, r -> Boolean.TRUE.equals(r.getUnexpectedAsset()),
                List.of("Results recorded before this was tracked are not included."));
    }

    public TabularReport damagedAssets(Long sessionId, LocalDate from, LocalDate to) {
        return resultReport("Damaged Assets Report", "Assets reported as damaged during verification.",
                List.of("session", "assetCode", "assetName", "expectedLocation", "condition", "conditionNotes",
                        "verifiedBy", "verifiedAt"),
                sessionId, from, to, r -> r.getResultStatus() == AuditVerificationStatus.DAMAGED, null);
    }

    public TabularReport exceptionReport(Long sessionId, LocalDate from, LocalDate to) {
        return resultReport("Verification Exception Report", "Every verification with a discrepancy of any kind.",
                List.of("session", "assetCode", "assetName", "discrepancy", "expectedLocation", "actualLocation",
                        "expectedCustodian", "actualCustodian", "conditionNotes", "verifiedBy", "verifiedAt"),
                sessionId, from, to, r -> r.getDiscrepancyType() != null, null);
    }

    public TabularReport conditionReport(Long sessionId, LocalDate from, LocalDate to) {
        List<AuditVerificationResult> matching = results(sessionId, from, to).stream()
                .filter(r -> r.getConditionRating() != null || (r.getConditionNotes() != null && !r.getConditionNotes().isBlank()))
                .toList();
        Map<String, Long> byRating = matching.stream().collect(Collectors.groupingBy(
                r -> r.getConditionRating() == null ? "NOT RATED" : r.getConditionRating().name(), Collectors.counting()));
        List<Map<String, Object>> rows = matching.stream().map(this::resultRow).toList();
        return table("Asset Condition Report", "Condition ratings and notes recorded during verification.",
                columns("session", "assetCode", "assetName", "result", "condition", "conditionNotes", "verifiedBy", "verifiedAt"),
                rows,
                summary("Good", byRating.getOrDefault("GOOD", 0L), "Fair", byRating.getOrDefault("FAIR", 0L),
                        "Poor", byRating.getOrDefault("POOR", 0L), "Not rated (notes only)", byRating.getOrDefault("NOT RATED", 0L)),
                null);
    }

    // ----------------------------------------------- population-based reports

    public TabularReport unverifiedAssets(Long sessionId) {
        AuditSession session = sessionOrDefault(sessionId);
        Set<Long> verified = resultRepository.findBySessionFetched(session.getId()).stream()
                .map(r -> r.getAsset().getId()).collect(Collectors.toSet());
        List<AuditSessionExpectedAsset> expected = expectedAssets(session);
        List<Map<String, Object>> rows = expected.stream()
                .filter(e -> !verified.contains(e.getAsset().getId()))
                .sorted(Comparator.comparing((AuditSessionExpectedAsset e) -> nz(e.getAsset().getAssetCode())))
                .map(e -> {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("assetCode", e.getAsset().getAssetCode());
                    row.put("assetName", e.getAsset().getName());
                    row.put("category", nz(e.getAsset().getCategory()));
                    row.put("expectedLocation", nz(e.getExpectedLocation()));
                    row.put("expectedCustodian", nz(e.getExpectedCustodian()));
                    row.put("registerStatus", nz(e.getAsset().getStatus()));
                    return row;
                }).toList();
        List<String> notes = new ArrayList<>();
        if (!hasSnapshot(session)) {
            notes.add("This audit started before expected assets were recorded, so the list is recomputed from the current register.");
        }
        return table("Unverified Assets Report", "Assets expected in the audit that have not been verified.",
                columns("assetCode", "assetName", "category", "expectedLocation", "expectedCustodian", "registerStatus"),
                rows, summary("Audit", session.getTitle(), "Expected", expected.size(), "Verified", expected.size() - rows.size(),
                        "Unverified", rows.size()), notes);
    }

    private record SessionCounts(AuditSession session, int expected, int verifiedInScope, int found, int missing, int damaged,
                                 int locationMismatch, int custodianMismatch, int unverified, int unexpected,
                                 boolean snapshot) {
    }

    private SessionCounts counts(AuditSession session) {
        Set<Long> expectedIds = expectedAssets(session).stream().map(e -> e.getAsset().getId()).collect(Collectors.toSet());
        int found = 0, missing = 0, damaged = 0, location = 0, custodian = 0, unexpected = 0, verifiedInScope = 0;
        for (AuditVerificationResult r : resultRepository.findBySessionFetched(session.getId())) {
            if (!expectedIds.contains(r.getAsset().getId())) {
                unexpected++;
                continue;
            }
            verifiedInScope++;
            switch (r.getResultStatus()) {
                case MISSING -> missing++;
                case DAMAGED -> damaged++;
                case LOCATION_MISMATCH -> location++;
                case FOUND -> {
                    if (r.isCustodianMismatch()) custodian++;
                    else found++;
                }
            }
        }
        return new SessionCounts(session, expectedIds.size(), verifiedInScope, found, missing, damaged, location, custodian,
                expectedIds.size() - verifiedInScope, unexpected, hasSnapshot(session));
    }

    private List<AuditSession> sessionsFor(Long sessionId, LocalDate from, LocalDate to) {
        if (sessionId != null) {
            return List.of(sessionOrDefault(sessionId));
        }
        LocalDate start = from != null ? from : MIN_DATE;
        LocalDate end = to != null ? to : MAX_DATE;
        return sessionRepository.findAllByOrderByStartedAtDesc().stream()
                .filter(s -> !s.getStartedAt().toLocalDate().isBefore(start) && !s.getStartedAt().toLocalDate().isAfter(end))
                .toList();
    }

    private static final String SNAPSHOT_NOTE =
            "Audits that started before expected assets were recorded use the current register, so their counts may have drifted.";

    public TabularReport assetCount(Long sessionId, LocalDate from, LocalDate to) {
        List<Column> cols = List.of(COLUMNS.get("session"),
                new Column("scope", "Scope", "text"), new Column("status", "Status", "text"),
                new Column("expected", "Expected", "number"), new Column("counted", "Counted (in scope)", "number"),
                new Column("variance", "Variance", "number"), new Column("unexpected", "Unexpected Found", "number"));
        List<Map<String, Object>> rows = new ArrayList<>();
        boolean legacy = false;
        for (AuditSession s : sessionsFor(sessionId, from, to)) {
            SessionCounts c = counts(s);
            legacy |= !c.snapshot();
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("session", s.getTitle());
            row.put("scope", s.getScopeLocation() == null ? "All assets" : s.getScopeLocation());
            row.put("status", s.getStatus().name());
            row.put("expected", c.expected());
            row.put("counted", c.verifiedInScope());
            row.put("variance", c.verifiedInScope() - c.expected());
            row.put("unexpected", c.unexpected());
            rows.add(row);
        }
        return table("Asset Count Report", "Expected assets against assets counted in each audit.", cols, rows, null,
                legacy ? List.of(SNAPSHOT_NOTE) : null);
    }

    public TabularReport reconciliation(Long sessionId, LocalDate from, LocalDate to) {
        List<Column> cols = List.of(COLUMNS.get("session"),
                new Column("expected", "Expected", "number"), new Column("found", "Found (clean)", "number"),
                new Column("missing", "Missing", "number"), new Column("damaged", "Damaged", "number"),
                new Column("locationMismatch", "Wrong Location", "number"),
                new Column("custodianMismatch", "Custodian Mismatch", "number"),
                new Column("unverified", "Not Verified", "number"), new Column("unexpected", "Unexpected Found", "number"),
                new Column("rate", "Verified %", "text"));
        List<Map<String, Object>> rows = new ArrayList<>();
        boolean legacy = false;
        for (AuditSession s : sessionsFor(sessionId, from, to)) {
            SessionCounts c = counts(s);
            legacy |= !c.snapshot();
            String rate = c.expected() == 0 ? "—" : BigDecimal.valueOf(c.verifiedInScope() * 100.0 / c.expected())
                    .setScale(1, RoundingMode.HALF_UP) + "%";
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("session", s.getTitle());
            row.put("expected", c.expected());
            row.put("found", c.found());
            row.put("missing", c.missing());
            row.put("damaged", c.damaged());
            row.put("locationMismatch", c.locationMismatch());
            row.put("custodianMismatch", c.custodianMismatch());
            row.put("unverified", c.unverified());
            row.put("unexpected", c.unexpected());
            row.put("rate", rate);
            rows.add(row);
        }
        List<String> notes = new ArrayList<>();
        notes.add("Expected = found + missing + damaged + wrong location + custodian mismatch + not verified.");
        if (legacy) notes.add(SNAPSHOT_NOTE);
        return table("Physical Verification Reconciliation", "Expected assets reconciled to verification outcomes per audit.",
                cols, rows, null, notes);
    }

    // ------------------------------------------------------ scan and history

    public TabularReport scanLog(Long sessionId, LocalDate from, LocalDate to) {
        List<AuditScanLog> logs = sessionId != null
                ? scanLogRepository.findBySession(sessionId)
                : scanLogRepository.findInRange((from != null ? from : MIN_DATE).atStartOfDay(),
                        (to != null ? to : MAX_DATE).atTime(LocalTime.MAX));
        List<Column> cols = List.of(new Column("scannedAt", "Scanned At", "date"), COLUMNS.get("session"),
                COLUMNS.get("assetCode"), COLUMNS.get("assetName"), COLUMNS.get("scannedCode"),
                new Column("method", "Method", "text"), COLUMNS.get("result"), new Column("scannedBy", "Scanned By", "text"));
        List<Map<String, Object>> rows = logs.stream().map(l -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("scannedAt", stamp(l.getScannedAt()));
            row.put("session", l.getSession().getTitle());
            row.put("assetCode", l.getAsset().getAssetCode());
            row.put("assetName", l.getAsset().getName());
            row.put("scannedCode", l.getScannedCode());
            row.put("method", l.getScanMethod().name());
            row.put("result", l.getResultStatus().name());
            row.put("scannedBy", nz(l.getScannedBy()));
            return row;
        }).toList();
        return table("QR / Barcode Scan Report", "Every verification submission, including re-scans.", cols, rows,
                summary("Scans", rows.size()),
                List.of("Scans made before the scan log existed are not listed."));
    }

    public TabularReport lastVerification() {
        Map<Long, AuditVerificationResult> latest = new HashMap<>();
        for (AuditVerificationResult r : resultRepository.findInRangeFetched(MIN_DATE.atStartOfDay(), MAX_DATE.atTime(LocalTime.MAX))) {
            latest.putIfAbsent(r.getAsset().getId(), r); // newest first
        }
        LocalDate today = AppClock.today();
        List<Column> cols = List.of(COLUMNS.get("assetCode"), COLUMNS.get("assetName"), COLUMNS.get("category"),
                new Column("location", "Recorded Location", "text"), new Column("custodian", "Custodian", "text"),
                new Column("lastVerified", "Last Verified", "date"), COLUMNS.get("result"),
                COLUMNS.get("session"), new Column("daysSince", "Days Since", "number"));
        List<Map<String, Object>> rows = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (Asset asset : assetRepository.findAll()) {
            if ("Disposed".equalsIgnoreCase(asset.getStatus()) || !seen.add(asset.getId())) continue;
            AuditVerificationResult r = latest.get(asset.getId());
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("assetCode", asset.getAssetCode());
            row.put("assetName", asset.getName());
            row.put("category", nz(asset.getCategory()));
            row.put("location", location(asset));
            row.put("custodian", nz(asset.getCustodian()));
            row.put("lastVerified", r == null ? "Never verified" : stamp(r.getVerifiedAt()));
            row.put("result", r == null ? "" : r.getResultStatus().name());
            row.put("session", r == null ? "" : r.getSession().getTitle());
            row.put("daysSince", r == null ? null : ChronoUnit.DAYS.between(r.getVerifiedAt().toLocalDate(), today));
            rows.add(row);
        }
        // Never-verified assets first, then oldest verification first.
        rows.sort(Comparator.comparing((Map<String, Object> m) -> m.get("daysSince") != null)
                .thenComparing(m -> m.get("daysSince") == null ? 0L : -((Long) m.get("daysSince")))
                .thenComparing(m -> String.valueOf(m.get("assetCode"))));
        long never = rows.stream().filter(m -> m.get("daysSince") == null).count();
        return table("Last Verification Date Report", "When each active asset was last physically verified.", cols, rows,
                summary("Active assets", rows.size(), "Never verified", never), null);
    }
}
