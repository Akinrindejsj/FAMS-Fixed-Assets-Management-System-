package com.example.fams.gl;

import com.example.fams.assets.Asset;
import com.example.fams.assets.AssetRepository;
import com.example.fams.assets.CapitalAddition;
import com.example.fams.assets.CapitalAdditionRepository;
import com.example.fams.depreciation.DepreciationPosting;
import com.example.fams.depreciation.DepreciationPostingRepository;
import com.example.fams.depreciation.DepreciationRun;
import com.example.fams.depreciation.DepreciationRunRepository;
import com.example.fams.lifecycle.AssetLifecycleWorkflow;
import com.example.fams.lifecycle.AssetLifecycleWorkflowRepository;
import com.example.fams.lifecycle.LifecycleWorkflowStatus;
import com.example.fams.lifecycle.LifecycleWorkflowType;
import com.example.fams.valuation.AssetImpairment;
import com.example.fams.valuation.AssetImpairmentRepository;
import com.example.fams.valuation.AssetRevaluation;
import com.example.fams.valuation.AssetRevaluationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * One-off generation of journals for transactions that predate GL journal generation:
 * acquisitions, capital additions, depreciation runs, revaluations, impairments and
 * completed disposals that have no journal yet.
 *
 * <p>Safe to run repeatedly: anything that already has a non-rejected journal is skipped.
 * Each journal is created in its own transaction, so one failure doesn't undo the others.
 * Sources are processed oldest-first, in the order acquisition, capital addition,
 * depreciation, revaluation, impairment, disposal — disposal journals read the ledger
 * balances the earlier journals established.
 *
 * <p>Limitation: opening accumulated depreciation carried on an asset's first depreciation
 * posting (for assets brought in already part-depreciated) has no offsetting account in the
 * mapping, so it is not journaled and will show as a reconciliation difference.
 */
@Service
public class GLBackfillService {

    private static final Logger log = LoggerFactory.getLogger(GLBackfillService.class);
    private static final List<JournalStatus> ACTIVE = List.of(JournalStatus.UNPOSTED, JournalStatus.POSTED);

    /** What a backfill would generate, by source type. */
    public record BackfillPlan(int acquisitions, int capitalAdditions, int depreciationPeriods,
                               int revaluations, int impairments, int disposals) {
        public int total() {
            return acquisitions + capitalAdditions + depreciationPeriods + revaluations + impairments + disposals;
        }
    }

    /** What a backfill run did. */
    public record BackfillResult(Map<String, Integer> created, int posted, int failuresRecorded,
                                 int errors, List<String> errorMessages) {
    }

    private final AssetRepository assetRepository;
    private final CapitalAdditionRepository capitalAdditionRepository;
    private final AssetRevaluationRepository revaluationRepository;
    private final AssetImpairmentRepository impairmentRepository;
    private final DepreciationPostingRepository postingRepository;
    private final DepreciationRunRepository runRepository;
    private final AssetLifecycleWorkflowRepository workflowRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final JournalGenerationFailureRepository failureRepository;
    private final GLAccountingService glAccountingService;
    private final PlatformTransactionManager transactionManager;

    private final AtomicBoolean running = new AtomicBoolean(false);

    public GLBackfillService(AssetRepository assetRepository,
                             CapitalAdditionRepository capitalAdditionRepository,
                             AssetRevaluationRepository revaluationRepository,
                             AssetImpairmentRepository impairmentRepository,
                             DepreciationPostingRepository postingRepository,
                             DepreciationRunRepository runRepository,
                             AssetLifecycleWorkflowRepository workflowRepository,
                             JournalEntryRepository journalEntryRepository,
                             JournalGenerationFailureRepository failureRepository,
                             GLAccountingService glAccountingService,
                             PlatformTransactionManager transactionManager) {
        this.assetRepository = assetRepository;
        this.capitalAdditionRepository = capitalAdditionRepository;
        this.revaluationRepository = revaluationRepository;
        this.impairmentRepository = impairmentRepository;
        this.postingRepository = postingRepository;
        this.runRepository = runRepository;
        this.workflowRepository = workflowRepository;
        this.journalEntryRepository = journalEntryRepository;
        this.failureRepository = failureRepository;
        this.glAccountingService = glAccountingService;
        this.transactionManager = transactionManager;
    }

    // ------------------------------------------------------------ finding work

    private record Work(List<Long> acquisitions, List<Long> capitalAdditions, List<String> depreciationPeriods,
                        List<Long> revaluations, List<Long> impairments, List<Long> disposals) {
    }

    private Set<Long> journaled(JournalSourceType type) {
        return new HashSet<>(journalEntryRepository.findSourceReferenceIds(type, ACTIVE));
    }

    private LocalDate periodEnd(String period) {
        List<DepreciationRun> runs = runRepository.findByPeriodOrderByTriggeredAtDesc(period);
        if (!runs.isEmpty() && runs.get(0).getPeriodEndDate() != null) {
            return runs.get(0).getPeriodEndDate();
        }
        try {
            return YearMonth.parse(period).atEndOfMonth();
        } catch (RuntimeException ex) {
            return LocalDate.now();
        }
    }

    private Work findWork() {
        Set<Long> acquired = journaled(JournalSourceType.ACQUISITION);
        List<Long> acquisitions = assetRepository.findAll().stream()
                .filter(a -> a.getPurchaseCost() != null && a.getPurchaseCost().signum() > 0)
                .filter(a -> !acquired.contains(a.getId()))
                .sorted(Comparator.comparing(Asset::getPurchaseDate, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(Asset::getId).toList();

        Set<Long> capitalised = journaled(JournalSourceType.CAPITAL_ADDITION);
        List<Long> capitalAdditions = capitalAdditionRepository.findAll().stream()
                .filter(c -> !capitalised.contains(c.getId()))
                .sorted(Comparator.comparing(CapitalAddition::getDate, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(CapitalAddition::getId).toList();

        Set<String> journaledPeriods = new HashSet<>(journalEntryRepository.findPeriods(JournalSourceType.DEPRECIATION_RUN, ACTIVE));
        Map<String, LocalDate> periodEnds = new LinkedHashMap<>();
        postingRepository.findAll().stream()
                .filter(p -> p.getDepreciationPeriod() != null && p.getDepreciationCharge() != null
                        && p.getDepreciationCharge().compareTo(BigDecimal.ZERO) > 0)
                .map(DepreciationPosting::getDepreciationPeriod)
                .distinct()
                .filter(period -> !journaledPeriods.contains(period))
                .forEach(period -> periodEnds.put(period, periodEnd(period)));
        List<String> periods = periodEnds.entrySet().stream()
                .sorted(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .toList();

        Set<Long> revalued = journaled(JournalSourceType.REVALUATION);
        List<Long> revaluations = revaluationRepository.findAll().stream()
                .filter(r -> r.getGainLoss() != null && r.getGainLoss().signum() != 0)
                .filter(r -> !revalued.contains(r.getId()))
                .sorted(Comparator.comparing(AssetRevaluation::getDate, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(AssetRevaluation::getId).toList();

        Set<Long> impaired = journaled(JournalSourceType.IMPAIRMENT);
        List<Long> impairments = impairmentRepository.findAll().stream()
                .filter(i -> i.getAmount() != null && i.getAmount().signum() > 0)
                .filter(i -> !impaired.contains(i.getId()))
                .sorted(Comparator.comparing(AssetImpairment::getDate, Comparator.nullsLast(Comparator.naturalOrder())))
                .map(AssetImpairment::getId).toList();

        Set<Long> disposed = journaled(JournalSourceType.DISPOSAL);
        List<Long> disposals = workflowRepository.findAllByOrderByRequestedAtDesc().stream()
                .filter(w -> w.getType() == LifecycleWorkflowType.DISPOSAL && w.getStatus() == LifecycleWorkflowStatus.COMPLETED)
                .filter(w -> !disposed.contains(w.getId()))
                .sorted(Comparator.comparing(AssetLifecycleWorkflow::getRequestedEffectiveDate,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .map(AssetLifecycleWorkflow::getId).toList();

        return new Work(acquisitions, capitalAdditions, periods, revaluations, impairments, disposals);
    }

    /** Counts what a backfill would generate, without generating anything. */
    public BackfillPlan preview() {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        tx.setReadOnly(true);
        return tx.execute(status -> {
            Work work = findWork();
            return new BackfillPlan(work.acquisitions().size(), work.capitalAdditions().size(),
                    work.depreciationPeriods().size(), work.revaluations().size(), work.impairments().size(),
                    work.disposals().size());
        });
    }

    // --------------------------------------------------------------- running

    /**
     * Generates every missing journal. When {@code autoPost} is set the new journals are
     * posted straight away (as {@code postedBy}); otherwise they are left unposted for review.
     *
     * @throws IllegalStateException if a backfill is already running
     */
    public BackfillResult run(boolean autoPost, String postedBy) {
        if (!running.compareAndSet(false, true)) {
            throw new IllegalStateException("A backfill is already running.");
        }
        try {
            TransactionTemplate readTx = new TransactionTemplate(transactionManager);
            readTx.setReadOnly(true);
            Work work = readTx.execute(status -> findWork());

            long failuresBefore = failureRepository.count();
            Map<String, Integer> created = new LinkedHashMap<>();
            List<String> errors = new ArrayList<>();
            int[] posted = {0};
            int[] errorCount = {0};

            created.put("Acquisitions", each(work.acquisitions(), "acquisition", errors, errorCount, posted, autoPost, postedBy,
                    id -> glAccountingService.generateForAcquisition(assetRepository.findById(id).orElseThrow())));
            created.put("Capital additions", each(work.capitalAdditions(), "capital addition", errors, errorCount, posted, autoPost, postedBy,
                    id -> glAccountingService.generateForCapitalAddition(capitalAdditionRepository.findById(id).orElseThrow())));

            int depreciation = 0;
            for (String period : work.depreciationPeriods()) {
                depreciation += one("depreciation period " + period, errors, errorCount, posted, autoPost, postedBy, () -> {
                    List<DepreciationPosting> postings = postingRepository.findByDepreciationPeriodOrderByAssetCode(period);
                    List<DepreciationRun> runs = runRepository.findByPeriodOrderByTriggeredAtDesc(period);
                    Long runId = runs.isEmpty() ? null : runs.get(0).getId();
                    return glAccountingService.generateForDepreciationRun(runId, period, periodEnd(period), postings);
                });
            }
            created.put("Depreciation runs", depreciation);

            created.put("Revaluations", each(work.revaluations(), "revaluation", errors, errorCount, posted, autoPost, postedBy,
                    id -> glAccountingService.generateForRevaluation(revaluationRepository.findById(id).orElseThrow())));
            created.put("Impairments", each(work.impairments(), "impairment", errors, errorCount, posted, autoPost, postedBy,
                    id -> glAccountingService.generateForImpairment(impairmentRepository.findById(id).orElseThrow())));
            created.put("Disposals", each(work.disposals(), "disposal", errors, errorCount, posted, autoPost, postedBy,
                    id -> glAccountingService.generateForDisposal(workflowRepository.findById(id).orElseThrow())));

            int failuresRecorded = (int) (failureRepository.count() - failuresBefore);
            log.info("GL backfill finished: {} created, {} posted, {} failures recorded, {} errors",
                    created, posted[0], failuresRecorded, errorCount[0]);
            return new BackfillResult(created, posted[0], failuresRecorded, errorCount[0],
                    errors.stream().limit(20).toList());
        } finally {
            running.set(false);
        }
    }

    private int each(List<Long> ids, String label, List<String> errors, int[] errorCount, int[] posted,
                     boolean autoPost, String postedBy, java.util.function.Function<Long, Optional<JournalEntry>> generator) {
        int count = 0;
        for (Long id : ids) {
            count += one(label + " #" + id, errors, errorCount, posted, autoPost, postedBy, () -> generator.apply(id));
        }
        return count;
    }

    /** Runs one generation in its own transaction. Returns 1 when a journal was created. */
    private int one(String label, List<String> errors, int[] errorCount, int[] posted, boolean autoPost, String postedBy,
                    java.util.function.Supplier<Optional<JournalEntry>> generation) {
        try {
            Boolean created = new TransactionTemplate(transactionManager).execute(status -> {
                Optional<JournalEntry> entry = generation.get();
                if (entry.isPresent() && autoPost) {
                    glAccountingService.post(entry.get().getId(), postedBy);
                    posted[0]++;
                }
                return entry.isPresent();
            });
            return Boolean.TRUE.equals(created) ? 1 : 0;
        } catch (RuntimeException ex) {
            errorCount[0]++;
            errors.add(label + ": " + ex.getMessage());
            log.warn("GL backfill failed for {}: {}", label, ex.getMessage());
            return 0;
        }
    }
}
