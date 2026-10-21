package com.example.fams.reports;

import com.example.fams.assets.Asset;
import com.example.fams.assets.AssetRepository;
import com.example.fams.assets.CapitalAddition;
import com.example.fams.assets.CapitalAdditionRepository;
import com.example.fams.audit.AuditVerificationResult;
import com.example.fams.audit.AuditVerificationResultRepository;
import com.example.fams.common.AppClock;
import com.example.fams.depreciation.DepreciationAdjustment;
import com.example.fams.depreciation.DepreciationAdjustmentRepository;
import com.example.fams.depreciation.DepreciationPosting;
import com.example.fams.depreciation.DepreciationRun;
import com.example.fams.depreciation.DepreciationRunRepository;
import com.example.fams.depreciation.DepreciationService;
import com.example.fams.gl.JournalEntry;
import com.example.fams.gl.JournalEntryRepository;
import com.example.fams.gl.JournalGenerationFailure;
import com.example.fams.gl.JournalGenerationFailureRepository;
import com.example.fams.gl.JournalSourceType;
import com.example.fams.gl.JournalStatus;
import com.example.fams.governance.AssetChangeLog;
import com.example.fams.governance.AssetChangeLogRepository;
import com.example.fams.governance.UserActivityLog;
import com.example.fams.governance.UserActivityLogRepository;
import com.example.fams.lifecycle.ApprovalDecision;
import com.example.fams.lifecycle.AssetLifecycleApprovalAction;
import com.example.fams.lifecycle.AssetLifecycleApprovalActionRepository;
import com.example.fams.lifecycle.AssetLifecycleHistory;
import com.example.fams.lifecycle.AssetLifecycleHistoryRepository;
import com.example.fams.lifecycle.AssetLifecycleWorkflow;
import com.example.fams.lifecycle.AssetLifecycleWorkflowRepository;
import com.example.fams.lifecycle.LifecycleEventType;
import com.example.fams.lifecycle.LifecycleWorkflowType;
import com.example.fams.reports.TabularReport.Column;
import com.example.fams.valuation.AssetImpairment;
import com.example.fams.valuation.AssetImpairmentRepository;
import com.example.fams.valuation.AssetRevaluation;
import com.example.fams.valuation.AssetRevaluationRepository;
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
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Audit and governance reports: transaction trail, asset change history, user activity,
 * approvals, backdated entries, manual adjustments, overrides, segregation-of-duties
 * exceptions and period-close evidence.
 *
 * <p>Not department-scoped: callers must be restricted to admin, auditor, asset manager and
 * financial officer roles. Asset change history and user activity only exist from the point
 * their capture was installed. Depreciation runs, revaluations and impairments have no
 * approval workflow in the system; their "approval" evidence is the posting of the journal
 * they generate.
 */
@Service
@Transactional(readOnly = true)
public class GovernanceReportService {

    private static final LocalDate MIN_DATE = LocalDate.of(1900, 1, 1);
    private static final LocalDate MAX_DATE = LocalDate.of(2999, 12, 31);
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final int ROW_LIMIT = 5000;
    private static final BigDecimal TOLERANCE = BigDecimal.ONE;

    private final AssetRepository assetRepository;
    private final AssetLifecycleHistoryRepository historyRepository;
    private final AssetLifecycleApprovalActionRepository approvalRepository;
    private final AssetLifecycleWorkflowRepository workflowRepository;
    private final DepreciationRunRepository runRepository;
    private final DepreciationAdjustmentRepository adjustmentRepository;
    private final AssetRevaluationRepository revaluationRepository;
    private final AssetImpairmentRepository impairmentRepository;
    private final CapitalAdditionRepository capitalAdditionRepository;
    private final JournalEntryRepository journalRepository;
    private final JournalGenerationFailureRepository failureRepository;
    private final AuditVerificationResultRepository verificationRepository;
    private final AssetChangeLogRepository changeLogRepository;
    private final UserActivityLogRepository activityRepository;
    private final DepreciationService depreciationService;

    public GovernanceReportService(AssetRepository assetRepository,
                                   AssetLifecycleHistoryRepository historyRepository,
                                   AssetLifecycleApprovalActionRepository approvalRepository,
                                   AssetLifecycleWorkflowRepository workflowRepository,
                                   DepreciationRunRepository runRepository,
                                   DepreciationAdjustmentRepository adjustmentRepository,
                                   AssetRevaluationRepository revaluationRepository,
                                   AssetImpairmentRepository impairmentRepository,
                                   CapitalAdditionRepository capitalAdditionRepository,
                                   JournalEntryRepository journalRepository,
                                   JournalGenerationFailureRepository failureRepository,
                                   AuditVerificationResultRepository verificationRepository,
                                   AssetChangeLogRepository changeLogRepository,
                                   UserActivityLogRepository activityRepository,
                                   DepreciationService depreciationService) {
        this.assetRepository = assetRepository;
        this.historyRepository = historyRepository;
        this.approvalRepository = approvalRepository;
        this.workflowRepository = workflowRepository;
        this.runRepository = runRepository;
        this.adjustmentRepository = adjustmentRepository;
        this.revaluationRepository = revaluationRepository;
        this.impairmentRepository = impairmentRepository;
        this.capitalAdditionRepository = capitalAdditionRepository;
        this.journalRepository = journalRepository;
        this.failureRepository = failureRepository;
        this.verificationRepository = verificationRepository;
        this.changeLogRepository = changeLogRepository;
        this.activityRepository = activityRepository;
        this.depreciationService = depreciationService;
    }

    // ------------------------------------------------------------------ helpers

    private static Column col(String key, String label, String type) {
        return new Column(key, label, type);
    }

    private String nz(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    private String stamp(LocalDateTime time) {
        return time == null ? "" : time.format(STAMP);
    }

    private String day(LocalDate date) {
        return date == null ? "" : date.toString();
    }

    private LocalDateTime start(LocalDate from) {
        return (from == null ? MIN_DATE : from).atStartOfDay();
    }

    private LocalDateTime end(LocalDate to) {
        return (to == null ? MAX_DATE : to).atTime(LocalTime.MAX);
    }

    private boolean in(LocalDateTime time, LocalDate from, LocalDate to) {
        return time != null && !time.isBefore(start(from)) && !time.isAfter(end(to));
    }

    private boolean in(LocalDate date, LocalDate from, LocalDate to) {
        return date != null && !date.isBefore(from == null ? MIN_DATE : from) && !date.isAfter(to == null ? MAX_DATE : to);
    }

    private String assetCode(Asset asset) {
        return asset == null ? "" : nz(asset.getAssetCode());
    }

    private boolean sameUser(String a, String b) {
        return a != null && b != null && !a.isBlank() && a.trim().equalsIgnoreCase(b.trim());
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

    private long hoursBetween(LocalDateTime from, LocalDateTime to) {
        return from == null || to == null ? 0 : Math.max(0, ChronoUnit.HOURS.between(from, to));
    }

    private Map<Long, JournalEntry> journalBySource(JournalSourceType type) {
        Map<Long, JournalEntry> bySource = new HashMap<>();
        for (JournalEntry e : journalRepository.findAllByOrderByDateDesc()) {
            if (e.getSourceType() != type || e.getSourceReferenceId() == null) continue;
            JournalEntry current = bySource.get(e.getSourceReferenceId());
            // prefer a live (non-rejected) journal over a rejected one
            if (current == null || (current.getStatus() == JournalStatus.REJECTED && e.getStatus() != JournalStatus.REJECTED)) {
                bySource.put(e.getSourceReferenceId(), e);
            }
        }
        return bySource;
    }

    // ------------------------------------------------- 1. transaction trail

    private Map<String, Object> trailRow(LocalDateTime at, String source, String event, String asset, String reference,
                                         String user, String details) {
        return row("when", stamp(at), "source", source, "event", event, "asset", asset, "reference", reference,
                "user", nz(user), "details", nz(details));
    }

    /** Asset Transaction Audit Trail: every recorded asset, depreciation, valuation, GL and verification event. */
    public TabularReport transactionTrail(LocalDate from, LocalDate to) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (AssetLifecycleHistory h : historyRepository.findAll()) {
            if (!in(h.getEventAt(), from, to)) continue;
            String change = h.getFromValue() == null && h.getToValue() == null ? ""
                    : " (" + nz(h.getFromValue()) + " → " + nz(h.getToValue()) + ")";
            rows.add(trailRow(h.getEventAt(), "Lifecycle", h.getEventType().name() + ": " + nz(h.getTitle()),
                    assetCode(h.getAsset()), "", h.getActor(), nz(h.getDetails()) + change));
        }
        for (DepreciationRun r : runRepository.findAll()) {
            if (!in(r.getTriggeredAt(), from, to)) continue;
            rows.add(trailRow(r.getTriggeredAt(), "Depreciation", "Run for period " + r.getPeriod() + " (" + r.getStatus() + ")",
                    "", "Run #" + r.getId(), r.getTriggeredBy(),
                    r.getSuccessCount() + " succeeded, " + r.getFailureCount() + " failed of " + r.getProcessedCount()));
        }
        for (DepreciationAdjustment a : adjustmentRepository.findAll()) {
            if (!in(a.getCreatedAt(), from, to)) continue;
            rows.add(trailRow(a.getCreatedAt(), "Depreciation", "Adjustment (" + a.getType() + ")", assetCode(a.getAsset()),
                    "Period " + nz(a.getPeriod()), a.getRecordedBy(), money(a.getAmount()) + " — " + nz(a.getReason())));
        }
        for (AssetRevaluation r : revaluationRepository.findAll()) {
            if (!in(r.getCreatedAt(), from, to)) continue;
            rows.add(trailRow(r.getCreatedAt(), "Valuation", "Revaluation", assetCode(r.getAsset()), "Dated " + day(r.getDate()),
                    r.getRecordedBy(), "Gain/loss " + money(r.getGainLoss()) + " — " + nz(r.getReason())));
        }
        for (AssetImpairment i : impairmentRepository.findAll()) {
            if (!in(i.getCreatedAt(), from, to)) continue;
            rows.add(trailRow(i.getCreatedAt(), "Valuation", "Impairment (" + i.getType() + ")", assetCode(i.getAsset()),
                    "Dated " + day(i.getDate()), i.getRecordedBy(), money(i.getAmount()) + " — " + nz(i.getReason())));
        }
        for (CapitalAddition c : capitalAdditionRepository.findAll()) {
            if (!in(c.getCreatedAt(), from, to)) continue;
            rows.add(trailRow(c.getCreatedAt(), "Asset", "Capital addition (" + c.getType() + ")", assetCode(c.getAsset()),
                    "Dated " + day(c.getDate()), c.getRecordedBy(), money(c.getAmount()) + " — " + nz(c.getDescription())));
        }
        for (JournalEntry e : journalRepository.findAllByOrderByDateDesc()) {
            if (e.getPostedAt() != null && in(e.getPostedAt(), from, to) && e.getPostedBy() != null) {
                rows.add(trailRow(e.getPostedAt(), "General ledger", "Journal posted", "", e.getJournalNumber(), e.getPostedBy(),
                        e.getSourceType() + " — " + nz(e.getDescription())));
            }
            if (e.getRejectedAt() != null && in(e.getRejectedAt(), from, to)) {
                rows.add(trailRow(e.getRejectedAt(), "General ledger", "Journal rejected", "", e.getJournalNumber(), e.getRejectedBy(),
                        nz(e.getRejectionReason())));
            }
        }
        for (AuditVerificationResult v : verificationRepository.findInRangeFetched(start(from), end(to))) {
            rows.add(trailRow(v.getVerifiedAt(), "Verification", "Physical verification: " + v.getResultStatus(),
                    assetCode(v.getAsset()), v.getSession().getTitle(), v.getVerifiedBy(), nz(v.getDiscrepancyType())));
        }
        rows.sort(Comparator.comparing((Map<String, Object> m) -> String.valueOf(m.get("when"))).reversed());
        boolean truncated = rows.size() > ROW_LIMIT;
        List<Map<String, Object>> shown = truncated ? new ArrayList<>(rows.subList(0, ROW_LIMIT)) : rows;
        List<String> notes = new ArrayList<>();
        notes.add("Combines lifecycle history, depreciation, valuation, capital additions, GL posting and verification events. "
                + "Asset record edits are in the Asset Master Change History.");
        if (truncated) notes.add("Showing the latest " + ROW_LIMIT + " of " + rows.size() + " events — narrow the date range to see the rest.");
        List<Column> cols = List.of(col("when", "When", "date"), col("source", "Source", "text"), col("event", "Event", "text"),
                col("asset", "Asset", "text"), col("reference", "Reference", "text"), col("user", "User", "text"),
                col("details", "Details", "text"));
        return table("Asset Transaction Audit Trail", "Chronological history of asset transactions and workflow events.", cols,
                shown, summary("Events", rows.size()), notes);
    }

    // ------------------------------------------------ 2. master change history

    /** Asset Master Change History: each changed field with its before and after value. */
    public TabularReport masterChanges(LocalDate from, LocalDate to) {
        List<Map<String, Object>> rows = changeLogRepository.findInRange(start(from), end(to)).stream()
                .limit(ROW_LIMIT)
                .map(c -> row("when", stamp(c.getChangedAt()), "asset", nz(c.getAssetCode()), "action", c.getAction(),
                        "field", c.getFieldName(), "oldValue", nz(c.getOldValue()), "newValue", nz(c.getNewValue()),
                        "user", nz(c.getChangedBy())))
                .collect(Collectors.toList());
        List<Column> cols = List.of(col("when", "When", "date"), col("asset", "Asset", "text"), col("action", "Action", "text"),
                col("field", "Field", "text"), col("oldValue", "Before", "text"), col("newValue", "After", "text"),
                col("user", "Changed By", "text"));
        return table("Asset Master Change History", "Before-and-after history of changes to asset master information.", cols, rows,
                summary("Changes", rows.size()),
                List.of("Changes are recorded from when this capture was installed; earlier changes are not available.",
                        "Changes made by background processes (such as workflows completing) show the signed-in user who triggered them."));
    }

    // ------------------------------------------------------- 3. user activity

    /** User Activity Report: state-changing actions by signed-in users. */
    public TabularReport userActivity(LocalDate from, LocalDate to) {
        List<UserActivityLog> logs = activityRepository.findInRange(start(from), end(to));
        List<Map<String, Object>> rows = logs.stream().limit(ROW_LIMIT)
                .map(l -> row("when", stamp(l.getOccurredAt()), "user", l.getUsername(), "method", l.getHttpMethod(),
                        "path", l.getPath(), "outcome", l.getStatusCode() >= 400 ? "Failed (" + l.getStatusCode() + ")" : "OK (" + l.getStatusCode() + ")"))
                .collect(Collectors.toList());
        long users = logs.stream().map(UserActivityLog::getUsername).distinct().count();
        long failed = logs.stream().filter(l -> l.getStatusCode() >= 400).count();
        List<String> notes = new ArrayList<>();
        notes.add("Only actions that change data (POST, PUT, PATCH, DELETE) are recorded, from when this capture was installed. Page views are not logged.");
        if (logs.size() > ROW_LIMIT) notes.add("Showing the latest " + ROW_LIMIT + " of " + logs.size() + " actions — narrow the date range to see the rest.");
        List<Column> cols = List.of(col("when", "When", "date"), col("user", "User", "text"), col("method", "Method", "text"),
                col("path", "Action", "text"), col("outcome", "Outcome", "text"));
        return table("User Activity Report", "User actions performed within the fixed asset system.", cols, rows,
                summary("Actions", logs.size(), "Users", users, "Failed requests", failed), notes);
    }

    // ----------------------------------------------------------- 4/6/7 approvals

    private TabularReport approvals(String title, String description, LocalDate from, LocalDate to, LifecycleWorkflowType type) {
        List<Map<String, Object>> rows = new ArrayList<>();
        long approved = 0, rejected = 0;
        for (AssetLifecycleApprovalAction a : approvalRepository.findAll()) {
            AssetLifecycleWorkflow w = a.getWorkflow();
            if (w == null || (type != null && w.getType() != type) || !in(a.getActionAt(), from, to)) continue;
            if (a.getDecision() == ApprovalDecision.APPROVED) approved++; else rejected++;
            rows.add(row("when", stamp(a.getActionAt()), "type", w.getType().name(), "workflow", "#" + w.getId(),
                    "asset", assetCode(w.getAsset()), "requestedBy", nz(w.getRequestedBy()), "requestedAt", stamp(w.getRequestedAt()),
                    "step", nz(a.getTaskName()), "approver", nz(a.getActor()), "decision", nz(a.getDecision()),
                    "comment", nz(a.getComment()), "hours", hoursBetween(w.getRequestedAt(), a.getActionAt()),
                    "status", w.getStatus().name()));
        }
        rows.sort(Comparator.comparing((Map<String, Object> m) -> String.valueOf(m.get("when"))).reversed());
        List<Column> cols = List.of(col("when", "Decided", "date"), col("type", "Type", "text"), col("workflow", "Workflow", "text"),
                col("asset", "Asset", "text"), col("requestedBy", "Requested By", "text"), col("requestedAt", "Requested At", "date"),
                col("step", "Step", "text"), col("approver", "Decision By", "text"), col("decision", "Decision", "text"),
                col("comment", "Comment", "text"), col("hours", "Hours to Decide", "number"), col("status", "Workflow Status", "text"));
        return table(title, description, cols, rows, summary("Decisions", rows.size(), "Approved", approved, "Rejected", rejected), null);
    }

    public TabularReport approvalHistory(LocalDate from, LocalDate to) {
        return approvals("Approval History Report", "Approval decisions across asset workflows.", from, to, null);
    }

    public TabularReport disposalApprovals(LocalDate from, LocalDate to) {
        return approvals("Disposal Approval Report", "Approval trail for disposals.", from, to, LifecycleWorkflowType.DISPOSAL);
    }

    public TabularReport transferApprovals(LocalDate from, LocalDate to) {
        return approvals("Transfer Approval Report", "Approval trail for transfers.", from, to, LifecycleWorkflowType.TRANSFER);
    }

    // ------------------------------------------- 5/8 depreciation & revaluation

    /** Depreciation Approval Report: each run with the review (posting or rejection) of its journal. */
    public TabularReport depreciationApprovals(LocalDate from, LocalDate to) {
        Map<String, JournalEntry> journals = new HashMap<>();
        for (JournalEntry e : journalRepository.findAllByOrderByDateDesc()) {
            if (e.getSourceType() != JournalSourceType.DEPRECIATION_RUN || e.getPeriod() == null) continue;
            JournalEntry current = journals.get(e.getPeriod());
            if (current == null || (current.getStatus() == JournalStatus.REJECTED && e.getStatus() != JournalStatus.REJECTED)) {
                journals.put(e.getPeriod(), e);
            }
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (DepreciationRun r : runRepository.findAll()) {
            if (!in(r.getTriggeredAt(), from, to)) continue;
            JournalEntry j = journals.get(r.getPeriod());
            LocalDateTime reviewedAt = j == null ? null : (j.getStatus() == JournalStatus.REJECTED ? j.getRejectedAt() : j.getPostedAt());
            String reviewedBy = j == null ? "" : (j.getStatus() == JournalStatus.REJECTED ? nz(j.getRejectedBy()) : nz(j.getPostedBy()));
            rows.add(row("period", nz(r.getPeriod()), "runAt", stamp(r.getTriggeredAt()), "runBy", nz(r.getTriggeredBy()),
                    "runStatus", nz(r.getStatus()), "succeeded", r.getSuccessCount(), "failed", r.getFailureCount(),
                    "journal", j == null ? "None generated" : j.getJournalNumber(), "journalStatus", j == null ? "" : j.getStatus().name(),
                    "reviewedBy", reviewedBy, "reviewedAt", stamp(reviewedAt),
                    "hours", reviewedAt == null ? null : hoursBetween(r.getTriggeredAt(), reviewedAt)));
        }
        rows.sort(Comparator.comparing((Map<String, Object> m) -> String.valueOf(m.get("runAt"))).reversed());
        List<Column> cols = List.of(col("period", "Period", "text"), col("runAt", "Run At", "date"), col("runBy", "Run By", "text"),
                col("runStatus", "Run Status", "text"), col("succeeded", "Succeeded", "number"), col("failed", "Failed", "number"),
                col("journal", "Journal", "text"), col("journalStatus", "Journal Status", "text"),
                col("reviewedBy", "Reviewed By", "text"), col("reviewedAt", "Reviewed At", "date"), col("hours", "Hours to Review", "number"));
        return table("Depreciation Approval Report", "Review and approval history for depreciation runs.", cols, rows,
                summary("Runs", rows.size()),
                List.of("Depreciation runs have no approval step of their own. The review shown is the posting (or rejection) of the journal the run generated.",
                        "A period re-run shows the latest journal for that period."));
    }

    /** Revaluation Approval Report: each revaluation with the review of its journal. */
    public TabularReport revaluationApprovals(LocalDate from, LocalDate to) {
        Map<Long, JournalEntry> journals = journalBySource(JournalSourceType.REVALUATION);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (AssetRevaluation r : revaluationRepository.findAll()) {
            if (!in(r.getCreatedAt(), from, to)) continue;
            JournalEntry j = journals.get(r.getId());
            LocalDateTime reviewedAt = j == null ? null : (j.getStatus() == JournalStatus.REJECTED ? j.getRejectedAt() : j.getPostedAt());
            rows.add(row("date", day(r.getDate()), "asset", assetCode(r.getAsset()), "previous", money(r.getPreviousCarryingAmount()),
                    "revalued", money(r.getRevaluedAmount()), "gainLoss", money(r.getGainLoss()), "recordedBy", nz(r.getRecordedBy()),
                    "enteredAt", stamp(r.getCreatedAt()), "journal", j == null ? "None generated" : j.getJournalNumber(),
                    "journalStatus", j == null ? "" : j.getStatus().name(),
                    "reviewedBy", j == null ? "" : (j.getStatus() == JournalStatus.REJECTED ? nz(j.getRejectedBy()) : nz(j.getPostedBy())),
                    "reviewedAt", stamp(reviewedAt)));
        }
        rows.sort(Comparator.comparing((Map<String, Object> m) -> String.valueOf(m.get("enteredAt"))).reversed());
        List<Column> cols = List.of(col("date", "Revaluation Date", "date"), col("asset", "Asset", "text"),
                col("previous", "Previous Amount", "money"), col("revalued", "Revalued Amount", "money"),
                col("gainLoss", "Gain / (Loss)", "money"), col("recordedBy", "Recorded By", "text"),
                col("enteredAt", "Entered At", "date"), col("journal", "Journal", "text"),
                col("journalStatus", "Journal Status", "text"), col("reviewedBy", "Reviewed By", "text"),
                col("reviewedAt", "Reviewed At", "date"));
        return table("Revaluation Approval Report", "Approval trail for revaluations.", cols, rows, summary("Revaluations", rows.size()),
                List.of("Revaluations have no approval workflow. The review shown is the posting (or rejection) of the journal they generate."));
    }

    // --------------------------------------------- 9/10 creation, modification

    /** Asset Creation/Modification Report: assets created or changed in the period. */
    public TabularReport creationModification(LocalDate from, LocalDate to) {
        Map<Long, Asset> assets = assetRepository.findAll().stream().collect(Collectors.toMap(Asset::getId, a -> a, (x, y) -> x));
        Map<Long, List<AssetChangeLog>> changesByAsset = new LinkedHashMap<>();
        Map<Long, AssetChangeLog> created = new HashMap<>();
        for (AssetChangeLog c : changeLogRepository.findInRange(start(from), end(to))) {
            if (AssetChangeLog.CREATED.equals(c.getAction())) created.putIfAbsent(c.getAssetId(), c);
            else changesByAsset.computeIfAbsent(c.getAssetId(), k -> new ArrayList<>()).add(c);
        }
        Set<Long> ids = new LinkedHashSet<>();
        for (Asset a : assets.values()) {
            if (in(a.getCreatedAt(), from, to)) ids.add(a.getId());
        }
        ids.addAll(changesByAsset.keySet());

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Long id : ids) {
            Asset a = assets.get(id);
            if (a == null) continue;
            List<AssetChangeLog> changes = changesByAsset.getOrDefault(id, List.of());
            boolean createdInPeriod = in(a.getCreatedAt(), from, to);
            AssetChangeLog last = changes.stream().max(Comparator.comparing(AssetChangeLog::getChangedAt)).orElse(null);
            rows.add(row("assetCode", nz(a.getAssetCode()), "assetName", nz(a.getName()), "category", nz(a.getCategory()),
                    "created", createdInPeriod ? stamp(a.getCreatedAt()) : "", "createdBy", created.containsKey(id) ? nz(created.get(id).getChangedBy()) : "",
                    "changes", changes.size(),
                    "fields", changes.stream().map(AssetChangeLog::getFieldName).distinct().collect(Collectors.joining(", ")),
                    "lastBy", last == null ? "" : nz(last.getChangedBy()), "lastAt", last == null ? "" : stamp(last.getChangedAt())));
        }
        rows.sort(Comparator.comparing((Map<String, Object> m) -> String.valueOf(m.get("assetCode"))));
        List<Column> cols = List.of(col("assetCode", "Asset Code", "text"), col("assetName", "Asset", "text"), col("category", "Category", "text"),
                col("created", "Created", "date"), col("createdBy", "Created By", "text"), col("changes", "Field Changes", "number"),
                col("fields", "Fields Changed", "text"), col("lastBy", "Last Changed By", "text"), col("lastAt", "Last Changed At", "date"));
        return table("Asset Creation/Modification Report", "Assets created or materially modified within the period.", cols, rows,
                summary("Assets", rows.size()),
                List.of("Creator and change details are available only for activity recorded since change capture was installed; "
                        + "earlier assets appear with their creation time but no creator."));
    }

    /** Deleted/Deactivated Asset Report: assets that are disposed or retired. */
    public TabularReport deactivatedAssets(LocalDate from, LocalDate to) {
        Map<Long, AssetLifecycleHistory> disposalEvents = new HashMap<>();
        for (AssetLifecycleHistory h : historyRepository.findAll()) {
            if (h.getEventType() != LifecycleEventType.DISPOSAL || h.getAsset() == null) continue;
            AssetLifecycleHistory current = disposalEvents.get(h.getAsset().getId());
            if (current == null || h.getEventAt().isAfter(current.getEventAt())) disposalEvents.put(h.getAsset().getId(), h);
        }
        Map<Long, AssetChangeLog> statusChanges = new HashMap<>();
        for (AssetChangeLog c : changeLogRepository.findInRange(start(null), end(null))) {
            if (!"status".equals(c.getFieldName()) || c.getNewValue() == null) continue;
            String v = c.getNewValue().toLowerCase();
            if (!v.equals("disposed") && !v.equals("retired")) continue;
            statusChanges.putIfAbsent(c.getAssetId(), c); // newest first
        }
        Map<Long, AssetLifecycleWorkflow> disposals = new HashMap<>();
        for (AssetLifecycleWorkflow w : workflowRepository.findAllByOrderByRequestedAtDesc()) {
            if (w.getType() == LifecycleWorkflowType.DISPOSAL && w.getAsset() != null) disposals.putIfAbsent(w.getAsset().getId(), w);
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        for (Asset a : assetRepository.findAll()) {
            String status = a.getStatus() == null ? "" : a.getStatus().trim().toLowerCase();
            if (!status.equals("disposed") && !status.equals("retired")) continue;
            LocalDateTime when;
            String by, basis;
            AssetLifecycleHistory event = disposalEvents.get(a.getId());
            AssetChangeLog change = statusChanges.get(a.getId());
            if (event != null) {
                when = event.getEventAt();
                by = nz(event.getActor());
                basis = "Lifecycle event";
            } else if (change != null) {
                when = change.getChangedAt();
                by = nz(change.getChangedBy());
                basis = "Change log";
            } else {
                when = a.getUpdatedAt();
                by = "";
                basis = "Last update (estimated)";
            }
            if (!in(when, from, to)) continue;
            AssetLifecycleWorkflow w = disposals.get(a.getId());
            rows.add(row("assetCode", nz(a.getAssetCode()), "assetName", nz(a.getName()), "category", nz(a.getCategory()),
                    "status", nz(a.getStatus()), "when", stamp(when), "by", by,
                    "method", w == null ? "" : nz(w.getDisposalMethod()), "reason", w == null ? "" : nz(w.getReason()), "basis", basis));
        }
        rows.sort(Comparator.comparing((Map<String, Object> m) -> String.valueOf(m.get("when"))).reversed());
        List<Column> cols = List.of(col("assetCode", "Asset Code", "text"), col("assetName", "Asset", "text"), col("category", "Category", "text"),
                col("status", "Status", "text"), col("when", "When", "date"), col("by", "By", "text"),
                col("method", "Disposal Method", "text"), col("reason", "Reason", "text"), col("basis", "Date Basis", "text"));
        return table("Deleted/Deactivated Asset Report", "Assets that have been disposed of, retired or otherwise deactivated.", cols, rows,
                summary("Assets", rows.size()),
                List.of("The system does not delete assets; deactivation means a Disposed or Retired status.",
                        "Where no disposal event or change log exists, the date is the asset's last update and is an estimate."));
    }

    // -------------------------------------------------- 11 backdated

    private Map<String, Object> backdatedRow(String source, String reference, String asset, LocalDate effective,
                                             LocalDateTime enteredAt, String by, String detail) {
        return row("source", source, "reference", reference, "asset", asset, "effective", day(effective),
                "entered", day(enteredAt.toLocalDate()), "days", ChronoUnit.DAYS.between(effective, enteredAt.toLocalDate()),
                "by", nz(by), "detail", nz(detail));
    }

    /** Backdated Transaction Report: entries whose effective date is at least {@code minDays} before they were entered (default 1). */
    public TabularReport backdated(LocalDate from, LocalDate to, Integer minDays) {
        int threshold = minDays != null && minDays > 0 ? minDays : 1;
        List<Map<String, Object>> rows = new ArrayList<>();
        for (AssetLifecycleWorkflow w : workflowRepository.findAllByOrderByRequestedAtDesc()) {
            if (w.getRequestedEffectiveDate() == null || w.getRequestedAt() == null || !in(w.getRequestedAt(), from, to)) continue;
            if (ChronoUnit.DAYS.between(w.getRequestedEffectiveDate(), w.getRequestedAt().toLocalDate()) < threshold) continue;
            rows.add(backdatedRow(w.getType().name().charAt(0) + w.getType().name().substring(1).toLowerCase() + " workflow", "#" + w.getId(),
                    assetCode(w.getAsset()), w.getRequestedEffectiveDate(), w.getRequestedAt(), w.getRequestedBy(), nz(w.getReason())));
        }
        for (CapitalAddition c : capitalAdditionRepository.findAll()) {
            if (c.getDate() != null && c.getCreatedAt() != null && in(c.getCreatedAt(), from, to)
                    && ChronoUnit.DAYS.between(c.getDate(), c.getCreatedAt().toLocalDate()) >= threshold) {
                rows.add(backdatedRow("Capital addition", "#" + c.getId(), assetCode(c.getAsset()), c.getDate(), c.getCreatedAt(),
                        c.getRecordedBy(), money(c.getAmount()) + " — " + nz(c.getDescription())));
            }
        }
        for (AssetRevaluation r : revaluationRepository.findAll()) {
            if (r.getDate() != null && r.getCreatedAt() != null && in(r.getCreatedAt(), from, to)
                    && ChronoUnit.DAYS.between(r.getDate(), r.getCreatedAt().toLocalDate()) >= threshold) {
                rows.add(backdatedRow("Revaluation", "#" + r.getId(), assetCode(r.getAsset()), r.getDate(), r.getCreatedAt(),
                        r.getRecordedBy(), "Gain/loss " + money(r.getGainLoss())));
            }
        }
        for (AssetImpairment i : impairmentRepository.findAll()) {
            if (i.getDate() != null && i.getCreatedAt() != null && in(i.getCreatedAt(), from, to)
                    && ChronoUnit.DAYS.between(i.getDate(), i.getCreatedAt().toLocalDate()) >= threshold) {
                rows.add(backdatedRow("Impairment", "#" + i.getId(), assetCode(i.getAsset()), i.getDate(), i.getCreatedAt(),
                        i.getRecordedBy(), money(i.getAmount()) + " — " + i.getType()));
            }
        }
        for (DepreciationAdjustment a : adjustmentRepository.findAll()) {
            if (a.getDate() != null && a.getCreatedAt() != null && in(a.getCreatedAt(), from, to)
                    && ChronoUnit.DAYS.between(a.getDate(), a.getCreatedAt().toLocalDate()) >= threshold) {
                rows.add(backdatedRow("Depreciation adjustment", "#" + a.getId(), assetCode(a.getAsset()), a.getDate(), a.getCreatedAt(),
                        a.getRecordedBy(), money(a.getAmount()) + " — " + nz(a.getReason())));
            }
        }
        rows.sort(Comparator.comparing((Map<String, Object> m) -> (Long) m.get("days")).reversed());
        List<Column> cols = List.of(col("source", "Transaction", "text"), col("reference", "Reference", "text"), col("asset", "Asset", "text"),
                col("effective", "Effective Date", "date"), col("entered", "Entered On", "date"), col("days", "Days Back", "number"),
                col("by", "Entered By", "text"), col("detail", "Detail", "text"));
        return table("Backdated Transaction Report", "Transactions entered with effective dates earlier than their entry dates.", cols, rows,
                summary("Backdated entries", rows.size(), "Minimum days back", threshold),
                List.of("Generated journals are not listed: they take the date of the underlying transaction, and journals created by the historical backfill are dated in the past by design."));
    }

    // ------------------------------------------------ 12 manual, 13 overrides

    /** Manual Adjustment Report: depreciation adjustments, revaluations, impairments and capital additions entered by hand. */
    public TabularReport manualAdjustments(LocalDate from, LocalDate to) {
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, Integer> counts = new TreeMap<>();
        for (DepreciationAdjustment a : adjustmentRepository.findAll()) {
            if (!in(a.getCreatedAt(), from, to)) continue;
            rows.add(adjustmentRow("Depreciation adjustment (" + a.getType() + ")", a.getCreatedAt(), a.getAsset(), a.getDate(),
                    a.getAmount(), a.getReason(), a.getRecordedBy()));
            counts.merge("Depreciation adjustments", 1, Integer::sum);
        }
        for (AssetRevaluation r : revaluationRepository.findAll()) {
            if (!in(r.getCreatedAt(), from, to)) continue;
            rows.add(adjustmentRow("Revaluation", r.getCreatedAt(), r.getAsset(), r.getDate(), r.getGainLoss(), r.getReason(), r.getRecordedBy()));
            counts.merge("Revaluations", 1, Integer::sum);
        }
        for (AssetImpairment i : impairmentRepository.findAll()) {
            if (!in(i.getCreatedAt(), from, to)) continue;
            rows.add(adjustmentRow("Impairment (" + i.getType() + ")", i.getCreatedAt(), i.getAsset(), i.getDate(), i.getAmount(),
                    i.getReason(), i.getRecordedBy()));
            counts.merge("Impairments", 1, Integer::sum);
        }
        for (CapitalAddition c : capitalAdditionRepository.findAll()) {
            if (!in(c.getCreatedAt(), from, to)) continue;
            rows.add(adjustmentRow("Capital addition (" + c.getType() + ")", c.getCreatedAt(), c.getAsset(), c.getDate(), c.getAmount(),
                    c.getDescription(), c.getRecordedBy()));
            counts.merge("Capital additions", 1, Integer::sum);
        }
        rows.sort(Comparator.comparing((Map<String, Object> m) -> String.valueOf(m.get("entered"))).reversed());
        List<Column> cols = List.of(col("entered", "Entered At", "date"), col("type", "Adjustment", "text"), col("asset", "Asset", "text"),
                col("effective", "Effective Date", "date"), col("amount", "Amount", "money"), col("reason", "Reason", "text"),
                col("by", "Recorded By", "text"));
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("Adjustments", String.valueOf(rows.size()));
        counts.forEach((k, v) -> summary.put(k, String.valueOf(v)));
        return table("Manual Adjustment Report", "Manual accounting and asset adjustments.", cols, rows, summary, null);
    }

    private Map<String, Object> adjustmentRow(String type, LocalDateTime entered, Asset asset, LocalDate effective,
                                              BigDecimal amount, String reason, String by) {
        return row("entered", stamp(entered), "type", type, "asset", assetCode(asset), "effective", day(effective),
                "amount", money(amount), "reason", nz(reason), "by", nz(by));
    }

    /** Override Report: depreciation adjustments, and disposals where the entered book values differ from the system's. */
    public TabularReport overrides(LocalDate from, LocalDate to) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (DepreciationAdjustment a : adjustmentRepository.findAll()) {
            if (!in(a.getCreatedAt(), from, to)) continue;
            rows.add(row("type", "Calculated depreciation overridden", "reference", "Adjustment #" + a.getId(), "asset", assetCode(a.getAsset()),
                    "detail", a.getType() + " — " + nz(a.getReason()), "entered", money(a.getAmount()), "system", null, "difference", null,
                    "by", nz(a.getRecordedBy()), "when", stamp(a.getCreatedAt())));
        }
        List<AssetLifecycleWorkflow> disposals = workflowRepository.findAllByOrderByRequestedAtDesc().stream()
                .filter(w -> w.getType() == LifecycleWorkflowType.DISPOSAL && w.getAsset() != null && in(w.getRequestedAt(), from, to))
                .toList();
        Map<Long, DepreciationPosting> latest = depreciationService.getLatestPostingsForAssets(
                disposals.stream().map(w -> w.getAsset().getId()).distinct().toList());
        for (AssetLifecycleWorkflow w : disposals) {
            DepreciationPosting p = latest.get(w.getAsset().getId());
            if (p == null) continue;
            addBookValueOverride(rows, w, "Accumulated depreciation", w.getAccumulatedDepreciation(), p.getClosingAccumulatedDepreciation());
            addBookValueOverride(rows, w, "Net book value", w.getNetBookValue(), p.getBookValue());
        }
        rows.sort(Comparator.comparing((Map<String, Object> m) -> String.valueOf(m.get("when"))).reversed());
        List<Column> cols = List.of(col("type", "Override", "text"), col("reference", "Reference", "text"), col("asset", "Asset", "text"),
                col("detail", "Detail", "text"), col("entered", "Entered Value", "money"), col("system", "System Value", "money"),
                col("difference", "Difference", "money"), col("by", "By", "text"), col("when", "When", "date"));
        return table("Override Report", "Transactions where calculated values were overridden.", cols, rows,
                summary("Overrides", rows.size()),
                List.of("Disposals are compared with the asset's latest depreciation posting; differences of 1.00 or less are ignored.",
                        "The system has no other control overrides (such as skipped approvals) to report."));
    }

    private void addBookValueOverride(List<Map<String, Object>> rows, AssetLifecycleWorkflow w, String field,
                                      BigDecimal entered, BigDecimal system) {
        if (entered == null || system == null || entered.subtract(system).abs().compareTo(TOLERANCE) <= 0) return;
        rows.add(row("type", "Disposal book value differs from system", "reference", "Disposal #" + w.getId(), "asset", assetCode(w.getAsset()),
                "detail", field, "entered", money(entered), "system", money(system), "difference", money(entered.subtract(system)),
                "by", nz(w.getRequestedBy()), "when", stamp(w.getRequestedAt())));
    }

    // ------------------------------------------------- 14 segregation of duties

    /** Segregation-of-Duties Exception Report: one person initiating and approving or posting the same thing. */
    public TabularReport segregationOfDuties(LocalDate from, LocalDate to) {
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<Long, List<AssetLifecycleApprovalAction>> byWorkflow = new LinkedHashMap<>();
        for (AssetLifecycleApprovalAction a : approvalRepository.findAll()) {
            if (a.getWorkflow() != null) byWorkflow.computeIfAbsent(a.getWorkflow().getId(), k -> new ArrayList<>()).add(a);
        }
        for (List<AssetLifecycleApprovalAction> actions : byWorkflow.values()) {
            AssetLifecycleWorkflow w = actions.get(0).getWorkflow();
            Map<String, Integer> approvals = new HashMap<>();
            for (AssetLifecycleApprovalAction a : actions) {
                if (a.getDecision() != ApprovalDecision.APPROVED || !in(a.getActionAt(), from, to)) continue;
                if (sameUser(a.getActor(), w.getRequestedBy())) {
                    rows.add(sodRow("High", "Requester approved their own " + w.getType().name().toLowerCase() + " request",
                            "Workflow #" + w.getId(), assetCode(w.getAsset()), a.getActor(), a.getActionAt(),
                            "Step: " + nz(a.getTaskName())));
                }
                approvals.merge(a.getActor() == null ? "" : a.getActor().trim().toLowerCase(), 1, Integer::sum);
            }
            approvals.forEach((actor, count) -> {
                if (!actor.isEmpty() && count > 1) {
                    rows.add(sodRow("Medium", "Same person approved " + count + " steps of one workflow", "Workflow #" + w.getId(),
                            assetCode(w.getAsset()), actor, w.getUpdatedAt(), "Multi-level approval was not independent"));
                }
            });
        }
        addPostingConflicts(rows, from, to);
        rows.sort(Comparator.comparing((Map<String, Object> m) -> "High".equals(m.get("severity")) ? 0 : 1)
                .thenComparing(m -> String.valueOf(m.get("when")), Comparator.reverseOrder()));
        List<Column> cols = List.of(col("severity", "Severity", "text"), col("conflict", "Conflict", "text"), col("reference", "Reference", "text"),
                col("asset", "Asset", "text"), col("user", "User", "text"), col("when", "When", "date"), col("detail", "Detail", "text"));
        return table("Segregation-of-Duties Exception Report", "Potential conflicts where initiation, approval or posting controls are not independent.",
                cols, rows, summary("Exceptions", rows.size()),
                List.of("Users are compared by their sign-in name, so the same person recorded under two different names would not be caught.",
                        "Checks: requester approving own request; one person approving several steps; one person recording a transaction and posting its journal."));
    }

    private void addPostingConflicts(List<Map<String, Object>> rows, LocalDate from, LocalDate to) {
        Map<Long, JournalEntry> revaluationJournals = journalBySource(JournalSourceType.REVALUATION);
        for (AssetRevaluation r : revaluationRepository.findAll()) {
            conflict(rows, revaluationJournals.get(r.getId()), r.getRecordedBy(), "Recorded and posted the revaluation journal",
                    "Revaluation #" + r.getId(), r.getAsset(), from, to);
        }
        Map<Long, JournalEntry> impairmentJournals = journalBySource(JournalSourceType.IMPAIRMENT);
        for (AssetImpairment i : impairmentRepository.findAll()) {
            conflict(rows, impairmentJournals.get(i.getId()), i.getRecordedBy(), "Recorded and posted the impairment journal",
                    "Impairment #" + i.getId(), i.getAsset(), from, to);
        }
        Map<Long, JournalEntry> capitalJournals = journalBySource(JournalSourceType.CAPITAL_ADDITION);
        for (CapitalAddition c : capitalAdditionRepository.findAll()) {
            conflict(rows, capitalJournals.get(c.getId()), c.getRecordedBy(), "Recorded and posted the capital addition journal",
                    "Capital addition #" + c.getId(), c.getAsset(), from, to);
        }
        Map<Long, JournalEntry> runJournals = journalBySource(JournalSourceType.DEPRECIATION_RUN);
        for (DepreciationRun run : runRepository.findAll()) {
            conflict(rows, runJournals.get(run.getId()), run.getTriggeredBy(), "Ran depreciation and posted its journal",
                    "Depreciation run #" + run.getId() + " (" + run.getPeriod() + ")", null, from, to);
        }
    }

    private void conflict(List<Map<String, Object>> rows, JournalEntry journal, String recordedBy, String conflict,
                          String reference, Asset asset, LocalDate from, LocalDate to) {
        if (journal == null || journal.getStatus() != JournalStatus.POSTED || !in(journal.getPostedAt(), from, to)) return;
        if (!sameUser(journal.getPostedBy(), recordedBy)) return;
        rows.add(sodRow("High", conflict, reference, assetCode(asset), recordedBy, journal.getPostedAt(),
                "Journal " + journal.getJournalNumber()));
    }

    private Map<String, Object> sodRow(String severity, String conflict, String reference, String asset, String user,
                                       LocalDateTime when, String detail) {
        return row("severity", severity, "conflict", conflict, "reference", reference, "asset", asset, "user", nz(user),
                "when", stamp(when), "detail", detail);
    }

    // ---------------------------------------------------------- 15 period close

    /** Period Close Audit Report: per depreciation period, the run, journal review and exceptions behind it. */
    public TabularReport periodClose(LocalDate from, LocalDate to) {
        Map<String, List<DepreciationRun>> runsByPeriod = new TreeMap<>(Comparator.reverseOrder());
        for (DepreciationRun r : runRepository.findAll()) {
            if (r.getPeriod() == null) continue;
            if ((from != null || to != null) && !in(r.getPeriodEndDate(), from, to)) continue;
            runsByPeriod.computeIfAbsent(r.getPeriod(), k -> new ArrayList<>()).add(r);
        }
        Map<String, List<JournalEntry>> journalsByPeriod = new HashMap<>();
        for (JournalEntry e : journalRepository.findAllByOrderByDateDesc()) {
            if (e.getSourceType() == JournalSourceType.DEPRECIATION_RUN && e.getPeriod() != null) {
                journalsByPeriod.computeIfAbsent(e.getPeriod(), k -> new ArrayList<>()).add(e);
            }
        }
        Map<String, Long> failuresByPeriod = failureRepository.findAll().stream()
                .filter(f -> f.getPeriod() != null)
                .collect(Collectors.groupingBy(JournalGenerationFailure::getPeriod, Collectors.counting()));

        List<Map<String, Object>> rows = new ArrayList<>();
        int attention = 0;
        for (Map.Entry<String, List<DepreciationRun>> e : runsByPeriod.entrySet()) {
            String period = e.getKey();
            List<DepreciationRun> runs = e.getValue().stream()
                    .sorted(Comparator.comparing(DepreciationRun::getTriggeredAt, Comparator.nullsLast(Comparator.naturalOrder())))
                    .toList();
            DepreciationRun last = runs.get(runs.size() - 1);
            List<JournalEntry> journals = journalsByPeriod.getOrDefault(period, List.of());
            JournalEntry live = journals.stream().filter(j -> j.getStatus() != JournalStatus.REJECTED).findFirst().orElse(null);

            List<String> issues = new ArrayList<>();
            if (last.getFailureCount() > 0) issues.add(last.getFailureCount() + " asset(s) failed in the latest run");
            if (live == null) {
                issues.add(journals.isEmpty() ? "No journal generated" : "Journal rejected and not replaced");
            } else if (live.getStatus() == JournalStatus.UNPOSTED) {
                issues.add("Journal not yet posted");
            } else if (live.getPostedAt() != null && last.getTriggeredAt() != null && last.getTriggeredAt().isAfter(live.getPostedAt())) {
                issues.add("Period re-run after its journal was posted");
            }
            long failures = failuresByPeriod.getOrDefault(period, 0L);
            if (failures > 0) issues.add(failures + " journal generation failure(s)");
            if (!issues.isEmpty()) attention++;

            rows.add(row("period", period, "periodEnd", day(last.getPeriodEndDate()), "runs", runs.size(),
                    "lastRun", stamp(last.getTriggeredAt()) + (last.getTriggeredBy() == null ? "" : " by " + last.getTriggeredBy()),
                    "succeeded", last.getSuccessCount(), "failed", last.getFailureCount(),
                    "journal", live == null ? "—" : live.getJournalNumber(), "journalStatus", live == null ? "" : live.getStatus().name(),
                    "postedBy", live == null ? "" : nz(live.getPostedBy()), "postedAt", live == null ? "" : stamp(live.getPostedAt()),
                    "status", issues.isEmpty() ? "Complete" : "Needs attention", "issues", String.join("; ", issues)));
        }
        List<Column> cols = List.of(col("period", "Period", "text"), col("periodEnd", "Period End", "date"), col("runs", "Runs", "number"),
                col("lastRun", "Latest Run", "text"), col("succeeded", "Succeeded", "number"), col("failed", "Failed", "number"),
                col("journal", "Journal", "text"), col("journalStatus", "Journal Status", "text"), col("postedBy", "Posted By", "text"),
                col("postedAt", "Posted At", "date"), col("status", "Status", "text"), col("issues", "Exceptions", "text"));
        return table("Period Close Audit Report", "Evidence and exceptions for fixed asset accounting periods.", cols, rows,
                summary("Periods", rows.size(), "Needing attention", attention),
                List.of("The system has no formal period close or lock. A period is treated as complete when its depreciation run succeeded, "
                        + "its journal is posted, and nothing was re-run or failed afterwards."));
    }
}
