package com.example.fams.reports;

import com.example.fams.assets.Asset;
import com.example.fams.assets.AssetRepository;
import com.example.fams.assets.CapitalAddition;
import com.example.fams.assets.CapitalAdditionRepository;
import com.example.fams.common.AppClock;
import com.example.fams.depreciation.DepreciationPosting;
import com.example.fams.depreciation.DepreciationPostingRepository;
import com.example.fams.gl.GLAccount;
import com.example.fams.gl.GLAccountMapping;
import com.example.fams.gl.GLAccountMappingRepository;
import com.example.fams.gl.GLAccountRepository;
import com.example.fams.gl.JournalEntry;
import com.example.fams.gl.JournalEntryRepository;
import com.example.fams.gl.JournalGenerationFailure;
import com.example.fams.gl.JournalGenerationFailureRepository;
import com.example.fams.gl.JournalLine;
import com.example.fams.gl.JournalLineRepository;
import com.example.fams.gl.JournalSourceType;
import com.example.fams.gl.JournalStatus;
import com.example.fams.valuation.AssetImpairment;
import com.example.fams.valuation.AssetImpairmentRepository;
import com.example.fams.valuation.AssetRevaluation;
import com.example.fams.valuation.AssetRevaluationRepository;
import com.example.fams.reports.TabularReport.Column;
import com.example.fams.valuation.ImpairmentType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Ledger-based reports: the fixed-asset subledger, journal listings, account activity,
 * reconciliation between the asset subledger and the GL, posting summaries, exceptions,
 * period movement and the trial balance.
 *
 * <p>These read journal lines, so they're only as complete as journal generation — assets
 * and transactions that predate GL generation have no journals and show up as
 * reconciliation differences. Visibility is not scoped by department: callers must be
 * restricted to finance-capable roles.
 */
@Service
@Transactional(readOnly = true)
public class GLReportService {

    private static final LocalDate MIN_DATE = LocalDate.of(1900, 1, 1);
    private static final LocalDate MAX_DATE = LocalDate.of(2999, 12, 31);
    private static final Long UNMAPPED = -1L;

    private static final List<JournalStatus> ACTIVE = List.of(JournalStatus.UNPOSTED, JournalStatus.POSTED);
    private static final List<JournalStatus> POSTED_ONLY = List.of(JournalStatus.POSTED);

    /** The GL account roles an asset-subledger balance can be reconciled against. */
    public enum ReconRole {
        COST("Asset Cost"),
        ACCUMULATED_DEPRECIATION("Accumulated Depreciation"),
        DEPRECIATION_EXPENSE("Depreciation Expense"),
        ACCUMULATED_IMPAIRMENT("Accumulated Impairment");

        private final String label;

        ReconRole(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    private final JournalEntryRepository entryRepository;
    private final JournalLineRepository lineRepository;
    private final GLAccountRepository accountRepository;
    private final GLAccountMappingRepository mappingRepository;
    private final JournalGenerationFailureRepository failureRepository;
    private final AssetRepository assetRepository;
    private final DepreciationPostingRepository postingRepository;
    private final CapitalAdditionRepository capitalAdditionRepository;
    private final AssetRevaluationRepository revaluationRepository;
    private final AssetImpairmentRepository impairmentRepository;

    public GLReportService(JournalEntryRepository entryRepository,
                           JournalLineRepository lineRepository,
                           GLAccountRepository accountRepository,
                           GLAccountMappingRepository mappingRepository,
                           JournalGenerationFailureRepository failureRepository,
                           AssetRepository assetRepository,
                           DepreciationPostingRepository postingRepository,
                           CapitalAdditionRepository capitalAdditionRepository,
                           AssetRevaluationRepository revaluationRepository,
                           AssetImpairmentRepository impairmentRepository) {
        this.entryRepository = entryRepository;
        this.lineRepository = lineRepository;
        this.accountRepository = accountRepository;
        this.mappingRepository = mappingRepository;
        this.failureRepository = failureRepository;
        this.assetRepository = assetRepository;
        this.postingRepository = postingRepository;
        this.capitalAdditionRepository = capitalAdditionRepository;
        this.revaluationRepository = revaluationRepository;
        this.impairmentRepository = impairmentRepository;
    }

    // ------------------------------------------------------------------ helpers

    private LocalDate fromOrMin(LocalDate date) {
        return date == null ? MIN_DATE : date;
    }

    private LocalDate toOrMax(LocalDate date) {
        return date == null ? MAX_DATE : date;
    }

    /** Aggregate query columns can come back as any numeric type; normalise to BigDecimal. */
    private BigDecimal dec(Object value) {
        if (value == null) return BigDecimal.ZERO;
        return value instanceof BigDecimal b ? b : new BigDecimal(value.toString());
    }

    private BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    private String nz(String value) {
        return value == null ? "" : value;
    }

    /** {@code null}/blank → the defaults, {@code ALL} → every status, otherwise the named status. */
    public List<JournalStatus> parseStatuses(String status, List<JournalStatus> defaults) {
        if (status == null || status.isBlank()) {
            return defaults;
        }
        if ("ALL".equalsIgnoreCase(status.trim())) {
            return List.of(JournalStatus.values());
        }
        try {
            return List.of(JournalStatus.valueOf(status.trim().toUpperCase()));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unknown journal status: " + status);
        }
    }

    private JournalLineRow toLineRow(JournalLine line) {
        JournalEntry entry = line.getJournalEntry();
        Asset asset = line.getAsset();
        return new JournalLineRow(
                entry.getJournalNumber(),
                entry.getDate(),
                entry.getSourceType().name(),
                entry.getStatus().name(),
                line.getAccount().getAccountCode(),
                line.getAccount().getAccountName(),
                asset == null ? "" : nz(asset.getAssetCode()),
                asset == null ? "" : nz(asset.getName()),
                money(line.getDebit()),
                money(line.getCredit()),
                nz(line.getDescription()));
    }

    private JournalLineReportData lineReport(List<JournalLine> lines) {
        JournalLineReportData data = new JournalLineReportData();
        List<JournalLineRow> rows = lines.stream().map(this::toLineRow).toList();
        data.setRows(rows);
        data.setTotalRecords(rows.size());
        data.setTotalDebit(money(rows.stream().map(JournalLineRow::debit).reduce(BigDecimal.ZERO, BigDecimal::add)));
        data.setTotalCredit(money(rows.stream().map(JournalLineRow::credit).reduce(BigDecimal.ZERO, BigDecimal::add)));
        data.setEmpty(rows.isEmpty());
        return data;
    }

    // ------------------------------------------------------------ journal reports

    /** Fixed Asset Subledger: every non-rejected journal line booked against an asset. */
    public JournalLineReportData subledger(LocalDate from, LocalDate to, Long accountId) {
        List<JournalLine> lines = lineRepository.findForReport(fromOrMin(from), toOrMax(to), ACTIVE).stream()
                .filter(l -> l.getAsset() != null)
                .filter(l -> accountId == null || accountId.equals(l.getAccount().getId()))
                .sorted(Comparator.comparing((JournalLine l) -> nz(l.getAsset().getAssetCode())))
                .toList();
        return lineReport(lines);
    }

    /** Journal entry headers; used for the all-journals, unposted and posted journal reports. */
    public JournalEntryReportData journals(LocalDate from, LocalDate to, List<JournalStatus> statuses) {
        List<JournalEntryRow> rows = entryRepository.findWithLinesBetween(fromOrMin(from), toOrMax(to)).stream()
                .filter(e -> statuses.contains(e.getStatus()))
                .map(e -> new JournalEntryRow(
                        e.getJournalNumber(),
                        e.getDate(),
                        nz(e.getPeriod()),
                        e.getSourceType().name(),
                        e.getStatus().name(),
                        nz(e.getDescription()),
                        money(e.getLines().stream().map(JournalLine::getDebit).reduce(BigDecimal.ZERO, BigDecimal::add)),
                        money(e.getLines().stream().map(JournalLine::getCredit).reduce(BigDecimal.ZERO, BigDecimal::add)),
                        nz(e.getPostedBy()),
                        nz(e.getRejectionReason())))
                .toList();
        JournalEntryReportData data = new JournalEntryReportData();
        data.setRows(rows);
        data.setTotalRecords(rows.size());
        data.setEmpty(rows.isEmpty());
        return data;
    }

    /** GL Posting Detail: individual journal lines in the given statuses. */
    public JournalLineReportData postingDetail(LocalDate from, LocalDate to, List<JournalStatus> statuses, Long accountId) {
        List<JournalLine> lines = lineRepository.findForReport(fromOrMin(from), toOrMax(to), statuses).stream()
                .filter(l -> accountId == null || accountId.equals(l.getAccount().getId()))
                .toList();
        return lineReport(lines);
    }

    /** GL Posting Summary: total debits and credits per account. */
    public AccountSummaryReportData postingSummary(LocalDate from, LocalDate to, List<JournalStatus> statuses) {
        Map<Long, GLAccount> accounts = accountsById();
        List<AccountSummaryRow> rows = new ArrayList<>();
        for (Object[] r : lineRepository.sumByAccount(fromOrMin(from), toOrMax(to), statuses)) {
            GLAccount account = accounts.get((Long) r[0]);
            if (account == null) continue;
            BigDecimal debit = money(dec(r[1]));
            BigDecimal credit = money(dec(r[2]));
            rows.add(new AccountSummaryRow(account.getAccountCode(), account.getAccountName(),
                    account.getAccountType() == null ? "" : account.getAccountType().name(),
                    debit, credit, debit.subtract(credit)));
        }
        rows.sort(Comparator.comparing(AccountSummaryRow::accountCode));
        AccountSummaryReportData data = new AccountSummaryReportData();
        data.setRows(rows);
        data.setEmpty(rows.isEmpty());
        return data;
    }

    /** Asset Account Activity: posted movement on one account with opening and closing balances (debit minus credit). */
    public AccountActivityData accountActivity(Long accountId, LocalDate from, LocalDate to) {
        if (accountId == null) {
            throw new IllegalArgumentException("Select a GL account.");
        }
        GLAccount account = accountRepository.findById(accountId)
                .orElseThrow(() -> new IllegalArgumentException("GL account not found: " + accountId));
        LocalDate start = from != null ? from : AppClock.today().withDayOfYear(1);
        LocalDate end = to != null ? to : AppClock.today();

        BigDecimal opening = BigDecimal.ZERO;
        for (Object[] r : lineRepository.sumByAccount(MIN_DATE, start.minusDays(1), POSTED_ONLY)) {
            if (accountId.equals(r[0])) {
                opening = (dec(r[1])).subtract(dec(r[2]));
            }
        }
        List<JournalLineRow> rows = lineRepository.findForReport(start, end, POSTED_ONLY).stream()
                .filter(l -> accountId.equals(l.getAccount().getId()))
                .map(this::toLineRow)
                .toList();
        BigDecimal debit = rows.stream().map(JournalLineRow::debit).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal credit = rows.stream().map(JournalLineRow::credit).reduce(BigDecimal.ZERO, BigDecimal::add);

        AccountActivityData data = new AccountActivityData();
        data.setAccountCode(account.getAccountCode());
        data.setAccountName(account.getAccountName());
        data.setFromDate(start);
        data.setToDate(end);
        data.setOpeningBalance(money(opening));
        data.setRows(rows);
        data.setPeriodDebit(money(debit));
        data.setPeriodCredit(money(credit));
        data.setClosingBalance(money(opening.add(debit).subtract(credit)));
        data.setEmpty(rows.isEmpty() && opening.signum() == 0);
        return data;
    }

    /** Trial Balance by asset account: net posted balance per account as at a date. */
    public TrialBalanceData trialBalance(LocalDate asAt, List<JournalStatus> statuses) {
        LocalDate end = asAt != null ? asAt : AppClock.today();
        Map<Long, GLAccount> accounts = accountsById();
        List<TrialBalanceRow> rows = new ArrayList<>();
        for (Object[] r : lineRepository.sumByAccount(MIN_DATE, end, statuses)) {
            GLAccount account = accounts.get((Long) r[0]);
            if (account == null) continue;
            BigDecimal net = (dec(r[1])).subtract(dec(r[2]));
            if (net.signum() == 0) continue;
            rows.add(new TrialBalanceRow(account.getAccountCode(), account.getAccountName(),
                    account.getAccountType() == null ? "" : account.getAccountType().name(),
                    net.signum() > 0 ? money(net) : BigDecimal.ZERO.setScale(2),
                    net.signum() < 0 ? money(net.negate()) : BigDecimal.ZERO.setScale(2)));
        }
        rows.sort(Comparator.comparing(TrialBalanceRow::accountCode));
        TrialBalanceData data = new TrialBalanceData();
        data.setGeneratedDate(end);
        data.setRows(rows);
        data.setTotalDebit(money(rows.stream().map(TrialBalanceRow::debitBalance).reduce(BigDecimal.ZERO, BigDecimal::add)));
        data.setTotalCredit(money(rows.stream().map(TrialBalanceRow::creditBalance).reduce(BigDecimal.ZERO, BigDecimal::add)));
        data.setEmpty(rows.isEmpty());
        return data;
    }

    /** Journal Exception Report: rejected or unbalanced journals, plus journals that could not be generated. */
    public JournalExceptionReportData journalExceptions(LocalDate from, LocalDate to) {
        List<JournalExceptionRow> rows = new ArrayList<>();

        for (JournalEntry entry : entryRepository.findWithLinesBetween(fromOrMin(from), toOrMax(to))) {
            BigDecimal debit = entry.getLines().stream().map(JournalLine::getDebit).reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal credit = entry.getLines().stream().map(JournalLine::getCredit).reduce(BigDecimal.ZERO, BigDecimal::add);
            String asset = entry.getLines().stream().map(JournalLine::getAsset).filter(a -> a != null)
                    .map(Asset::getAssetCode).findFirst().orElse("");
            if (entry.getStatus() == JournalStatus.REJECTED) {
                String reason = nz(entry.getRejectionReason());
                if (entry.getRejectedBy() != null) {
                    reason += " (rejected by " + entry.getRejectedBy() + ")";
                }
                rows.add(new JournalExceptionRow("Rejected journal", entry.getJournalNumber(), entry.getDate(),
                        entry.getSourceType().name(), asset, reason));
            } else if (money(debit).compareTo(money(credit)) != 0) {
                rows.add(new JournalExceptionRow("Unbalanced journal", entry.getJournalNumber(), entry.getDate(),
                        entry.getSourceType().name(), asset,
                        "Debits " + money(debit) + " do not equal credits " + money(credit)));
            }
        }

        List<JournalGenerationFailure> failures = failureRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(
                fromOrMin(from).atStartOfDay(), toOrMax(to).atTime(LocalTime.MAX));
        for (JournalGenerationFailure f : failures) {
            rows.add(new JournalExceptionRow("Journal not generated",
                    f.getSourceType().name() + (f.getSourceReferenceId() == null ? "" : " #" + f.getSourceReferenceId()),
                    f.getCreatedAt().toLocalDate(), f.getSourceType().name(), nz(f.getAssetCode()), f.getReason()));
        }

        rows.sort(Comparator.comparing(JournalExceptionRow::date).reversed());
        return new JournalExceptionReportData(AppClock.today(), rows, rows.isEmpty());
    }

    // ---------------------------------------------------------- period movement

    /**
     * Period Movement Report: opening net book value, the movement from each kind of
     * transaction, and closing net book value, all derived from posted journal lines on the
     * cost, accumulated depreciation and accumulated impairment accounts.
     */
    public PeriodMovementData periodMovement(LocalDate from, LocalDate to) {
        LocalDate start = from != null ? from : AppClock.today().withDayOfYear(1);
        LocalDate end = to != null ? to : AppClock.today();

        Set<Long> nbvAccounts = new HashSet<>();
        for (GLAccountMapping m : mappingRepository.findAll()) {
            addId(nbvAccounts, m.getAssetCostAccount());
            addId(nbvAccounts, m.getAccumulatedDepreciationAccount());
            addId(nbvAccounts, m.getAccumulatedImpairmentAccount());
        }

        BigDecimal opening = BigDecimal.ZERO;
        for (Object[] r : lineRepository.sumByAccount(MIN_DATE, start.minusDays(1), POSTED_ONLY)) {
            if (nbvAccounts.contains((Long) r[0])) {
                opening = opening.add(dec(r[1])).subtract(dec(r[2]));
            }
        }

        Map<JournalSourceType, BigDecimal> bySource = new EnumMap<>(JournalSourceType.class);
        for (JournalLine line : lineRepository.findForReport(start, end, POSTED_ONLY)) {
            if (!nbvAccounts.contains(line.getAccount().getId())) continue;
            bySource.merge(line.getJournalEntry().getSourceType(),
                    line.getDebit().subtract(line.getCredit()), BigDecimal::add);
        }

        PeriodMovementData data = new PeriodMovementData();
        data.setFromDate(start);
        data.setToDate(end);
        data.setOpeningNetBookValue(money(opening));
        data.setAdditions(money(bySource.get(JournalSourceType.ACQUISITION)));
        data.setCapitalAdditions(money(bySource.get(JournalSourceType.CAPITAL_ADDITION)));
        data.setDepreciation(money(bySource.get(JournalSourceType.DEPRECIATION_RUN)));
        data.setRevaluationMovement(money(bySource.get(JournalSourceType.REVALUATION)));
        data.setImpairmentMovement(money(bySource.get(JournalSourceType.IMPAIRMENT)));
        data.setDisposals(money(bySource.get(JournalSourceType.DISPOSAL)));
        data.setClosingNetBookValue(money(opening
                .add(data.getAdditions()).add(data.getCapitalAdditions()).add(data.getDepreciation())
                .add(data.getRevaluationMovement()).add(data.getImpairmentMovement()).add(data.getDisposals())));
        return data;
    }

    /**
     * Carrying Amount Movement by asset category: opening carrying amount, additions, capital
     * additions, depreciation, revaluation, impairment, disposals and closing carrying amount,
     * from posted journal lines on the cost, accumulated depreciation and accumulated
     * impairment accounts. Complete from when journal generation started.
     */
    public TabularReport carryingAmountMovementByCategory(LocalDate from, LocalDate to) {
        LocalDate start = from != null ? from : AppClock.today().withDayOfYear(1);
        LocalDate end = to != null ? to : AppClock.today();

        Set<Long> nbvAccounts = new HashSet<>();
        for (GLAccountMapping m : mappingRepository.findAll()) {
            addId(nbvAccounts, m.getAssetCostAccount());
            addId(nbvAccounts, m.getAccumulatedDepreciationAccount());
            addId(nbvAccounts, m.getAccumulatedImpairmentAccount());
        }

        Map<String, BigDecimal> opening = new LinkedHashMap<>();
        Map<String, Map<JournalSourceType, BigDecimal>> movement = new LinkedHashMap<>();
        Set<String> categories = new LinkedHashSet<>();

        for (JournalLine line : lineRepository.findForReport(MIN_DATE, start.minusDays(1), POSTED_ONLY)) {
            if (!nbvAccounts.contains(line.getAccount().getId())) continue;
            String category = categoryOf(line);
            categories.add(category);
            opening.merge(category, line.getDebit().subtract(line.getCredit()), BigDecimal::add);
        }
        for (JournalLine line : lineRepository.findForReport(start, end, POSTED_ONLY)) {
            if (!nbvAccounts.contains(line.getAccount().getId())) continue;
            String category = categoryOf(line);
            categories.add(category);
            movement.computeIfAbsent(category, k -> new EnumMap<>(JournalSourceType.class))
                    .merge(line.getJournalEntry().getSourceType(), line.getDebit().subtract(line.getCredit()), BigDecimal::add);
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        BigDecimal[] sums = new BigDecimal[8];
        java.util.Arrays.fill(sums, BigDecimal.ZERO);
        for (String category : categories) {
            Map<JournalSourceType, BigDecimal> m = movement.getOrDefault(category, new EnumMap<>(JournalSourceType.class));
            BigDecimal open = opening.getOrDefault(category, BigDecimal.ZERO);
            BigDecimal[] values = {
                    open,
                    m.getOrDefault(JournalSourceType.ACQUISITION, BigDecimal.ZERO),
                    m.getOrDefault(JournalSourceType.CAPITAL_ADDITION, BigDecimal.ZERO),
                    m.getOrDefault(JournalSourceType.DEPRECIATION_RUN, BigDecimal.ZERO),
                    m.getOrDefault(JournalSourceType.REVALUATION, BigDecimal.ZERO),
                    m.getOrDefault(JournalSourceType.IMPAIRMENT, BigDecimal.ZERO),
                    m.getOrDefault(JournalSourceType.DISPOSAL, BigDecimal.ZERO),
                    BigDecimal.ZERO};
            values[7] = values[0].add(values[1]).add(values[2]).add(values[3]).add(values[4]).add(values[5]).add(values[6]);
            for (int i = 0; i < values.length; i++) {
                sums[i] = sums[i].add(values[i]);
            }
            rows.add(movementRow(category, values));
        }
        rows.sort(java.util.Comparator.comparing((Map<String, Object> r) -> String.valueOf(r.get("category"))));

        List<Column> columns = List.of(new Column("category", "Category", "text"),
                new Column("opening", "Opening Carrying Amount", "money"), new Column("additions", "Additions", "money"),
                new Column("capital", "Capital Additions", "money"), new Column("depreciation", "Depreciation", "money"),
                new Column("revaluation", "Revaluation", "money"), new Column("impairment", "Impairment", "money"),
                new Column("disposals", "Disposals", "money"), new Column("closing", "Closing Carrying Amount", "money"));
        return new TabularReport("Carrying Amount Movement",
                "Movement in carrying amount by asset category from " + start + " to " + end + ".",
                AppClock.today(), columns, rows, rows.isEmpty() ? Map.<String, Object>of() : movementRow("Totals", sums),
                Map.<String, Object>of(),
                List.of("Derived from posted journal lines, so it is complete from when journal generation started.",
                        "Decreases (depreciation, impairment, disposals) are shown as negatives."),
                rows.isEmpty());
    }

    private String categoryOf(JournalLine line) {
        Asset asset = line.getAsset();
        return asset == null || asset.getCategory() == null || asset.getCategory().isBlank()
                ? "Uncategorised" : asset.getCategory();
    }

    private Map<String, Object> movementRow(String category, BigDecimal[] v) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("category", category);
        row.put("opening", money(v[0]));
        row.put("additions", money(v[1]));
        row.put("capital", money(v[2]));
        row.put("depreciation", money(v[3]));
        row.put("revaluation", money(v[4]));
        row.put("impairment", money(v[5]));
        row.put("disposals", money(v[6]));
        row.put("closing", money(v[7]));
        return row;
    }

    private void addId(Set<Long> ids, GLAccount account) {
        if (account != null) {
            ids.add(account.getId());
        }
    }

    // ----------------------------------------------------------- reconciliation

    /**
     * Compares what the asset, depreciation and valuation data says each GL account should
     * hold against what the ledger holds, per account role. Disposed assets are excluded
     * because the disposal journal clears them from the ledger. {@code period} narrows the
     * depreciation expense comparison to one depreciation period.
     */
    public ReconciliationReportData reconcile(Set<ReconRole> roles, String period) {
        List<Asset> liveAssets = assetRepository.findAll().stream()
                .filter(a -> !"Disposed".equalsIgnoreCase(a.getStatus()))
                .toList();
        Set<Long> liveIds = liveAssets.stream().map(Asset::getId).collect(Collectors.toSet());

        Map<String, GLAccountMapping> byCategory = new HashMap<>();
        GLAccountMapping defaultMapping = null;
        Set<Long> roleAccountsAll = new HashSet<>();
        Map<ReconRole, Set<Long>> roleAccounts = new EnumMap<>(ReconRole.class);
        for (ReconRole role : ReconRole.values()) {
            roleAccounts.put(role, new HashSet<>());
        }
        for (GLAccountMapping m : mappingRepository.findAll()) {
            if (m.getCategory() == null || m.getCategory().isBlank()) {
                defaultMapping = m;
            } else {
                byCategory.put(m.getCategory().trim().toLowerCase(), m);
            }
            for (ReconRole role : ReconRole.values()) {
                addId(roleAccounts.get(role), roleAccount(m, role));
            }
        }
        roleAccounts.values().forEach(roleAccountsAll::addAll);

        Map<Long, GLAccount> accounts = accountsById();
        List<JournalLine> lines = lineRepository.findForReport(MIN_DATE, MAX_DATE, ACTIVE);

        List<ReconciliationRow> rows = new ArrayList<>();
        for (ReconRole role : roles) {
            Map<Long, BigDecimal> subledger = new LinkedHashMap<>();
            switch (role) {
                case COST -> {
                    Map<Long, BigDecimal> extra = new HashMap<>();
                    for (CapitalAddition c : capitalAdditionRepository.findAll()) {
                        extra.merge(c.getAsset().getId(), c.getAmount(), BigDecimal::add);
                    }
                    for (AssetRevaluation r : revaluationRepository.findAll()) {
                        extra.merge(r.getAsset().getId(), r.getGainLoss() == null ? BigDecimal.ZERO : r.getGainLoss(), BigDecimal::add);
                    }
                    for (Asset a : liveAssets) {
                        BigDecimal cost = (a.getPurchaseCost() == null ? BigDecimal.ZERO : a.getPurchaseCost())
                                .add(extra.getOrDefault(a.getId(), BigDecimal.ZERO));
                        accumulate(subledger, mappingFor(a.getCategory(), byCategory, defaultMapping), role, cost);
                    }
                }
                case ACCUMULATED_DEPRECIATION -> {
                    Map<Long, DepreciationPosting> latest = liveIds.isEmpty() ? Map.of()
                            : postingRepository.findLatestByAssetIdIn(liveIds).stream()
                                    .collect(Collectors.toMap(DepreciationPosting::getAssetId, p -> p, (a, b) -> a));
                    for (Asset a : liveAssets) {
                        DepreciationPosting p = latest.get(a.getId());
                        if (p != null && p.getClosingAccumulatedDepreciation() != null) {
                            accumulate(subledger, mappingFor(a.getCategory(), byCategory, defaultMapping), role,
                                    p.getClosingAccumulatedDepreciation());
                        }
                    }
                }
                case DEPRECIATION_EXPENSE -> {
                    List<DepreciationPosting> postings = period == null || period.isBlank()
                            ? postingRepository.findAll()
                            : postingRepository.findByDepreciationPeriodOrderByAssetCode(period.trim());
                    for (DepreciationPosting p : postings) {
                        if (p.getDepreciationCharge() != null) {
                            accumulate(subledger, mappingFor(p.getCategory(), byCategory, defaultMapping), role,
                                    p.getDepreciationCharge());
                        }
                    }
                }
                case ACCUMULATED_IMPAIRMENT -> {
                    Map<Long, BigDecimal> net = new HashMap<>();
                    for (AssetImpairment i : impairmentRepository.findAll()) {
                        BigDecimal signed = i.getType() == ImpairmentType.IMPAIRMENT_LOSS ? i.getAmount() : i.getAmount().negate();
                        net.merge(i.getAsset().getId(), signed, BigDecimal::add);
                    }
                    for (Asset a : liveAssets) {
                        BigDecimal value = net.get(a.getId());
                        if (value != null) {
                            accumulate(subledger, mappingFor(a.getCategory(), byCategory, defaultMapping), role, value);
                        }
                    }
                }
            }

            boolean debitNormal = role == ReconRole.COST || role == ReconRole.DEPRECIATION_EXPENSE;
            Map<Long, BigDecimal> posted = new HashMap<>();
            Map<Long, BigDecimal> unposted = new HashMap<>();
            Set<Long> accountsForRole = roleAccounts.get(role);
            for (JournalLine line : lines) {
                Long accountId = line.getAccount().getId();
                if (!accountsForRole.contains(accountId)) continue;
                JournalEntry entry = line.getJournalEntry();
                if (role == ReconRole.DEPRECIATION_EXPENSE && period != null && !period.isBlank()
                        && !(entry.getSourceType() == JournalSourceType.DEPRECIATION_RUN && period.trim().equals(entry.getPeriod()))) {
                    continue;
                }
                BigDecimal net = line.getDebit().subtract(line.getCredit());
                if (!debitNormal) net = net.negate();
                (entry.getStatus() == JournalStatus.POSTED ? posted : unposted).merge(accountId, net, BigDecimal::add);
            }

            Set<Long> keys = new LinkedHashSet<>(subledger.keySet());
            keys.addAll(posted.keySet());
            keys.addAll(unposted.keySet());
            for (Long key : keys) {
                BigDecimal sub = money(subledger.get(key));
                BigDecimal gl = money(posted.get(key));
                BigDecimal pending = money(unposted.get(key));
                BigDecimal difference = sub.subtract(gl);
                GLAccount account = accounts.get(key);
                rows.add(new ReconciliationRow(role.label(),
                        account != null ? account.getAccountCode() : "UNMAPPED",
                        account != null ? account.getAccountName() : "No GL mapping for these assets' category",
                        sub, gl, pending, difference, difference.subtract(pending)));
            }
        }

        ReconciliationReportData data = new ReconciliationReportData();
        data.setRows(rows);
        data.setEmpty(rows.isEmpty());
        return data;
    }

    private GLAccount roleAccount(GLAccountMapping mapping, ReconRole role) {
        return switch (role) {
            case COST -> mapping.getAssetCostAccount();
            case ACCUMULATED_DEPRECIATION -> mapping.getAccumulatedDepreciationAccount();
            case DEPRECIATION_EXPENSE -> mapping.getDepreciationExpenseAccount();
            case ACCUMULATED_IMPAIRMENT -> mapping.getAccumulatedImpairmentAccount();
        };
    }

    private GLAccountMapping mappingFor(String category, Map<String, GLAccountMapping> byCategory, GLAccountMapping fallback) {
        if (category != null && !category.isBlank()) {
            GLAccountMapping specific = byCategory.get(category.trim().toLowerCase());
            if (specific != null) return specific;
        }
        return fallback;
    }

    private void accumulate(Map<Long, BigDecimal> target, GLAccountMapping mapping, ReconRole role, BigDecimal amount) {
        GLAccount account = mapping == null ? null : roleAccount(mapping, role);
        target.merge(account == null ? UNMAPPED : account.getId(), amount, BigDecimal::add);
    }

    private Map<Long, GLAccount> accountsById() {
        return accountRepository.findAll().stream().collect(Collectors.toMap(GLAccount::getId, a -> a));
    }
}
