package com.example.fams.gl;

import com.example.fams.assets.Asset;
import com.example.fams.assets.AssetRepository;
import com.example.fams.assets.CapitalAddition;
import com.example.fams.depreciation.DepreciationPosting;
import com.example.fams.lifecycle.AssetLifecycleWorkflow;
import com.example.fams.valuation.AssetImpairment;
import com.example.fams.valuation.AssetRevaluation;
import com.example.fams.valuation.ImpairmentType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Generates double-entry journal entries from asset transactions (acquisition,
 * depreciation runs, capital additions, revaluations, impairments, disposals) and manages
 * their posting workflow.
 *
 * <p>Generation is called right after the underlying transaction is saved. When a journal
 * can't be produced — no account mapping for the asset's category, a mapping with a missing
 * account, or an unbalanced entry — the underlying transaction is not blocked; instead the
 * gap is stored as a {@link JournalGenerationFailure} so the Journal Exception Report can
 * show it.
 */
@Service
public class GLAccountingService {

    private static final Logger log = LoggerFactory.getLogger(GLAccountingService.class);

    /** Statuses that count towards ledger balances (rejected entries never do). */
    private static final List<JournalStatus> ACTIVE = List.of(JournalStatus.UNPOSTED, JournalStatus.POSTED);

    private final GLAccountMappingRepository mappingRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final JournalLineRepository journalLineRepository;
    private final JournalGenerationFailureRepository failureRepository;
    private final AssetRepository assetRepository;

    public GLAccountingService(GLAccountMappingRepository mappingRepository,
                               JournalEntryRepository journalEntryRepository,
                               JournalLineRepository journalLineRepository,
                               JournalGenerationFailureRepository failureRepository,
                               AssetRepository assetRepository) {
        this.mappingRepository = mappingRepository;
        this.journalEntryRepository = journalEntryRepository;
        this.journalLineRepository = journalLineRepository;
        this.failureRepository = failureRepository;
        this.assetRepository = assetRepository;
    }

    /** Asset-category mapping if one exists, else the global default (category == null), else empty. */
    @Transactional(readOnly = true)
    public Optional<GLAccountMapping> resolveMapping(String category) {
        if (category != null && !category.isBlank()) {
            Optional<GLAccountMapping> specific = mappingRepository.findByCategoryIgnoreCase(category);
            if (specific.isPresent()) {
                return specific;
            }
        }
        return mappingRepository.findByCategoryIsNull();
    }

    private void recordFailure(JournalSourceType sourceType, Long sourceReferenceId, String assetCode,
                               String period, String reason) {
        log.warn("Journal not generated ({} #{}, asset {}): {}", sourceType, sourceReferenceId, assetCode, reason);
        JournalGenerationFailure failure = new JournalGenerationFailure();
        failure.setSourceType(sourceType);
        failure.setSourceReferenceId(sourceReferenceId);
        failure.setAssetCode(assetCode);
        failure.setPeriod(period);
        failure.setReason(reason);
        failureRepository.save(failure);
    }

    private Optional<GLAccountMapping> mappingOrRecordFailure(String category, JournalSourceType sourceType,
                                                              Long sourceReferenceId, String assetCode) {
        Optional<GLAccountMapping> mapping = resolveMapping(category);
        if (mapping.isEmpty()) {
            recordFailure(sourceType, sourceReferenceId, assetCode, null,
                    "No GL account mapping for category '" + (category == null ? "" : category)
                            + "' and no global default is configured");
        }
        return mapping;
    }

    private boolean alreadyGenerated(JournalSourceType sourceType, Long sourceReferenceId) {
        return sourceReferenceId != null
                && journalEntryRepository.existsBySourceTypeAndSourceReferenceIdAndStatusIn(sourceType, sourceReferenceId, ACTIVE);
    }

    private BigDecimal totalDebit(JournalEntry entry) {
        return entry.getLines().stream().map(JournalLine::getDebit).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private BigDecimal totalCredit(JournalEntry entry) {
        return entry.getLines().stream().map(JournalLine::getCredit).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private boolean isBalanced(JournalEntry entry) {
        return totalDebit(entry).setScale(2, RoundingMode.HALF_UP)
                .compareTo(totalCredit(entry).setScale(2, RoundingMode.HALF_UP)) == 0;
    }

    private JournalLine line(GLAccount account, Asset asset, BigDecimal debit, BigDecimal credit, String description) {
        JournalLine line = new JournalLine();
        line.setAccount(account);
        line.setAsset(asset);
        line.setDebit(debit == null ? BigDecimal.ZERO : debit);
        line.setCredit(credit == null ? BigDecimal.ZERO : credit);
        line.setDescription(description);
        return line;
    }

    /** Debit for a positive amount, credit for a negative one, nothing for zero. */
    private void addDebit(JournalEntry entry, GLAccount account, Asset asset, BigDecimal amount, String description) {
        if (amount == null || amount.signum() == 0) return;
        entry.addLine(amount.signum() > 0
                ? line(account, asset, amount, null, description)
                : line(account, asset, null, amount.negate(), description));
    }

    /** Credit for a positive amount, debit for a negative one, nothing for zero. */
    private void addCredit(JournalEntry entry, GLAccount account, Asset asset, BigDecimal amount, String description) {
        addDebit(entry, account, asset, amount == null ? null : amount.negate(), description);
    }

    /**
     * Saves the entry if it has lines, every account resolved and it balances; otherwise
     * records a {@link JournalGenerationFailure} and returns empty. The journal number is
     * derived from the generated id so concurrent saves can't collide.
     */
    private Optional<JournalEntry> saveIfValid(JournalEntry entry, String assetCode) {
        if (entry.getLines().isEmpty()) {
            return Optional.empty();
        }
        String missing = entry.getLines().stream()
                .filter(l -> l.getAccount() == null)
                .map(JournalLine::getDescription)
                .distinct()
                .collect(Collectors.joining(", "));
        if (!missing.isEmpty()) {
            recordFailure(entry.getSourceType(), entry.getSourceReferenceId(), assetCode, entry.getPeriod(),
                    "The category mapping has no GL account for: " + missing);
            return Optional.empty();
        }
        if (!isBalanced(entry)) {
            recordFailure(entry.getSourceType(), entry.getSourceReferenceId(), assetCode, entry.getPeriod(),
                    "Entry is not balanced (debits " + totalDebit(entry) + ", credits " + totalCredit(entry) + ")");
            return Optional.empty();
        }
        entry.setJournalNumber("TMP-" + UUID.randomUUID().toString().replace("-", ""));
        JournalEntry saved = journalEntryRepository.save(entry);
        saved.setJournalNumber(String.format("JE-%06d", saved.getId()));
        return Optional.of(journalEntryRepository.save(saved));
    }

    @Transactional
    public Optional<JournalEntry> generateForAcquisition(Asset asset) {
        if (alreadyGenerated(JournalSourceType.ACQUISITION, asset.getId())) return Optional.empty();
        BigDecimal cost = asset.getPurchaseCost() == null ? BigDecimal.ZERO : asset.getPurchaseCost();
        if (cost.compareTo(BigDecimal.ZERO) <= 0) return Optional.empty();
        Optional<GLAccountMapping> mappingOpt = mappingOrRecordFailure(asset.getCategory(),
                JournalSourceType.ACQUISITION, asset.getId(), asset.getAssetCode());
        if (mappingOpt.isEmpty()) return Optional.empty();
        GLAccountMapping mapping = mappingOpt.get();

        JournalEntry entry = new JournalEntry();
        entry.setDate(asset.getPurchaseDate() != null ? asset.getPurchaseDate() : LocalDate.now());
        entry.setSourceType(JournalSourceType.ACQUISITION);
        entry.setSourceReferenceId(asset.getId());
        entry.setDescription("Acquisition of " + asset.getAssetCode() + " — " + asset.getName());
        entry.addLine(line(mapping.getAssetCostAccount(), asset, cost, null, "Asset cost"));
        entry.addLine(line(mapping.getCashPayablesAccount(), asset, null, cost, "Cash / payables clearing"));
        return saveIfValid(entry, asset.getAssetCode());
    }

    /**
     * One journal per depreciation run. Assets whose category has no usable mapping are
     * recorded as failures while the rest of the run is still journaled. If the period was
     * re-run, an unposted journal from the earlier run is rejected as superseded; if the
     * earlier journal was already posted the GL is left alone and a failure is recorded.
     */
    @Transactional
    public Optional<JournalEntry> generateForDepreciationRun(Long depreciationRunId, String period, LocalDate entryDate,
                                                               List<DepreciationPosting> postings) {
        if (postings == null || postings.isEmpty()) return Optional.empty();

        List<JournalEntry> prior = journalEntryRepository.findByPeriodOrderByDateDesc(period).stream()
                .filter(e -> e.getSourceType() == JournalSourceType.DEPRECIATION_RUN && e.getStatus() != JournalStatus.REJECTED)
                .toList();
        if (prior.stream().anyMatch(e -> e.getStatus() == JournalStatus.POSTED)) {
            recordFailure(JournalSourceType.DEPRECIATION_RUN, depreciationRunId, null, period,
                    "Period " + period + " was re-run after its depreciation journal was already posted; "
                            + "the GL was not updated and needs a manual correction");
            return Optional.empty();
        }
        for (JournalEntry superseded : prior) {
            rejectInternal(superseded, "Superseded by a re-run of period " + period, "system");
        }

        Set<Long> assetIds = postings.stream().map(DepreciationPosting::getAssetId)
                .filter(id -> id != null).collect(Collectors.toSet());
        Map<Long, Asset> assetsById = assetRepository.findAllById(assetIds).stream()
                .collect(Collectors.toMap(Asset::getId, a -> a));

        JournalEntry entry = new JournalEntry();
        entry.setDate(entryDate != null ? entryDate : LocalDate.now());
        entry.setPeriod(period);
        entry.setSourceType(JournalSourceType.DEPRECIATION_RUN);
        entry.setSourceReferenceId(depreciationRunId);
        entry.setDescription("Depreciation run for period " + period);

        for (DepreciationPosting posting : postings) {
            if (posting.getDepreciationCharge() == null || posting.getDepreciationCharge().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            Optional<GLAccountMapping> mappingOpt = resolveMapping(posting.getCategory());
            if (mappingOpt.isEmpty()) {
                recordFailure(JournalSourceType.DEPRECIATION_RUN, depreciationRunId, posting.getAssetCode(), period,
                        "No GL account mapping for category '" + posting.getCategory() + "' and no global default");
                continue;
            }
            GLAccountMapping mapping = mappingOpt.get();
            if (mapping.getDepreciationExpenseAccount() == null || mapping.getAccumulatedDepreciationAccount() == null) {
                recordFailure(JournalSourceType.DEPRECIATION_RUN, depreciationRunId, posting.getAssetCode(), period,
                        "The mapping for '" + posting.getCategory()
                                + "' is missing the depreciation expense or accumulated depreciation account");
                continue;
            }
            Asset asset = assetsById.get(posting.getAssetId());
            entry.addLine(line(mapping.getDepreciationExpenseAccount(), asset, posting.getDepreciationCharge(), null,
                    posting.getAssetCode() + " depreciation charge"));
            entry.addLine(line(mapping.getAccumulatedDepreciationAccount(), asset, null, posting.getDepreciationCharge(),
                    posting.getAssetCode() + " accumulated depreciation"));
        }
        return saveIfValid(entry, null);
    }

    @Transactional
    public Optional<JournalEntry> generateForCapitalAddition(CapitalAddition addition) {
        if (alreadyGenerated(JournalSourceType.CAPITAL_ADDITION, addition.getId())) return Optional.empty();
        Asset asset = addition.getAsset();
        Optional<GLAccountMapping> mappingOpt = mappingOrRecordFailure(asset.getCategory(),
                JournalSourceType.CAPITAL_ADDITION, addition.getId(), asset.getAssetCode());
        if (mappingOpt.isEmpty()) return Optional.empty();
        GLAccountMapping mapping = mappingOpt.get();

        JournalEntry entry = new JournalEntry();
        entry.setDate(addition.getDate());
        entry.setSourceType(JournalSourceType.CAPITAL_ADDITION);
        entry.setSourceReferenceId(addition.getId());
        entry.setDescription("Capital addition (" + addition.getType() + ") for " + asset.getAssetCode());
        entry.addLine(line(mapping.getAssetCostAccount(), asset, addition.getAmount(), null, "Asset cost"));
        entry.addLine(line(mapping.getCashPayablesAccount(), asset, null, addition.getAmount(), "Cash / payables clearing"));
        return saveIfValid(entry, asset.getAssetCode());
    }

    @Transactional
    public Optional<JournalEntry> generateForRevaluation(AssetRevaluation revaluation) {
        if (alreadyGenerated(JournalSourceType.REVALUATION, revaluation.getId())) return Optional.empty();
        BigDecimal gainLoss = revaluation.getGainLoss();
        if (gainLoss == null || gainLoss.compareTo(BigDecimal.ZERO) == 0) return Optional.empty();
        Asset asset = revaluation.getAsset();
        Optional<GLAccountMapping> mappingOpt = mappingOrRecordFailure(asset.getCategory(),
                JournalSourceType.REVALUATION, revaluation.getId(), asset.getAssetCode());
        if (mappingOpt.isEmpty()) return Optional.empty();
        GLAccountMapping mapping = mappingOpt.get();

        JournalEntry entry = new JournalEntry();
        entry.setDate(revaluation.getDate());
        entry.setSourceType(JournalSourceType.REVALUATION);
        entry.setSourceReferenceId(revaluation.getId());
        entry.setDescription("Revaluation of " + asset.getAssetCode());

        if (gainLoss.compareTo(BigDecimal.ZERO) > 0) {
            entry.addLine(line(mapping.getAssetCostAccount(), asset, gainLoss, null, "Revaluation write-up"));
            entry.addLine(line(mapping.getRevaluationSurplusAccount(), asset, null, gainLoss, "Revaluation surplus"));
        } else {
            BigDecimal loss = gainLoss.abs();
            entry.addLine(line(mapping.getRevaluationLossAccount(), asset, loss, null, "Revaluation loss"));
            entry.addLine(line(mapping.getAssetCostAccount(), asset, null, loss, "Revaluation write-down"));
        }
        return saveIfValid(entry, asset.getAssetCode());
    }

    @Transactional
    public Optional<JournalEntry> generateForImpairment(AssetImpairment impairment) {
        if (alreadyGenerated(JournalSourceType.IMPAIRMENT, impairment.getId())) return Optional.empty();
        BigDecimal amount = impairment.getAmount();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) return Optional.empty();
        Asset asset = impairment.getAsset();
        Optional<GLAccountMapping> mappingOpt = mappingOrRecordFailure(asset.getCategory(),
                JournalSourceType.IMPAIRMENT, impairment.getId(), asset.getAssetCode());
        if (mappingOpt.isEmpty()) return Optional.empty();
        GLAccountMapping mapping = mappingOpt.get();

        JournalEntry entry = new JournalEntry();
        entry.setDate(impairment.getDate());
        entry.setSourceType(JournalSourceType.IMPAIRMENT);
        entry.setSourceReferenceId(impairment.getId());
        entry.setDescription((impairment.getType() == ImpairmentType.REVERSAL ? "Impairment reversal for " : "Impairment loss for ")
                + asset.getAssetCode());

        if (impairment.getType() == ImpairmentType.IMPAIRMENT_LOSS) {
            entry.addLine(line(mapping.getImpairmentLossAccount(), asset, amount, null, "Impairment loss"));
            entry.addLine(line(mapping.getAccumulatedImpairmentAccount(), asset, null, amount, "Accumulated impairment"));
        } else {
            entry.addLine(line(mapping.getAccumulatedImpairmentAccount(), asset, amount, null, "Accumulated impairment"));
            entry.addLine(line(mapping.getImpairmentLossAccount(), asset, null, amount, "Impairment reversal"));
        }
        return saveIfValid(entry, asset.getAssetCode());
    }

    /**
     * Disposal: removes the asset's cost, accumulated depreciation and accumulated
     * impairment as they stand in the ledger (so capital additions and revaluations booked
     * to the cost account are cleared too), books the proceeds, and balances with a gain
     * or loss. If the ledger has no cost history for the asset (it predates GL
     * generation), the asset's purchase cost and the workflow's accumulated depreciation
     * are used instead.
     *
     * <p>The gain/loss here comes from the ledger amounts, so it can differ from the
     * workflow's {@code financialImpact} when the user-entered book value on the disposal
     * form doesn't match the ledger.
     */
    @Transactional
    public Optional<JournalEntry> generateForDisposal(AssetLifecycleWorkflow workflow) {
        if (alreadyGenerated(JournalSourceType.DISPOSAL, workflow.getId())) return Optional.empty();
        Asset asset = workflow.getAsset();
        Optional<GLAccountMapping> mappingOpt = mappingOrRecordFailure(asset.getCategory(),
                JournalSourceType.DISPOSAL, workflow.getId(), asset.getAssetCode());
        if (mappingOpt.isEmpty()) return Optional.empty();
        GLAccountMapping mapping = mappingOpt.get();

        Map<Long, BigDecimal> net = ledgerNetByAccount(asset.getId());
        BigDecimal cost = netOf(net, mapping.getAssetCostAccount());
        BigDecimal accumulatedDepreciation = netOf(net, mapping.getAccumulatedDepreciationAccount()).negate();
        BigDecimal accumulatedImpairment = netOf(net, mapping.getAccumulatedImpairmentAccount()).negate();
        if (cost.signum() <= 0) {
            cost = asset.getPurchaseCost() == null ? BigDecimal.ZERO : asset.getPurchaseCost();
            accumulatedDepreciation = workflow.getAccumulatedDepreciation() == null
                    ? BigDecimal.ZERO : workflow.getAccumulatedDepreciation();
            accumulatedImpairment = BigDecimal.ZERO;
        }
        BigDecimal proceeds = workflow.getDisposalProceeds() == null ? BigDecimal.ZERO : workflow.getDisposalProceeds();
        BigDecimal gainLoss = proceeds.subtract(cost).add(accumulatedDepreciation).add(accumulatedImpairment);

        JournalEntry entry = new JournalEntry();
        entry.setDate(workflow.getRequestedEffectiveDate() != null ? workflow.getRequestedEffectiveDate() : LocalDate.now());
        entry.setSourceType(JournalSourceType.DISPOSAL);
        entry.setSourceReferenceId(workflow.getId());
        entry.setDescription("Disposal (" + workflow.getDisposalMethod() + ") of " + asset.getAssetCode());

        addDebit(entry, mapping.getAccumulatedDepreciationAccount(), asset, accumulatedDepreciation, "Clear accumulated depreciation");
        addDebit(entry, mapping.getAccumulatedImpairmentAccount(), asset, accumulatedImpairment, "Clear accumulated impairment");
        addDebit(entry, mapping.getDisposalProceedsAccount(), asset, proceeds, "Disposal proceeds");
        addCredit(entry, mapping.getAssetCostAccount(), asset, cost, "Remove asset cost");
        if (gainLoss.signum() > 0) {
            addCredit(entry, mapping.getDisposalGainAccount(), asset, gainLoss, "Gain on disposal");
        } else if (gainLoss.signum() < 0) {
            addDebit(entry, mapping.getDisposalLossAccount(), asset, gainLoss.negate(), "Loss on disposal");
        }
        return saveIfValid(entry, asset.getAssetCode());
    }

    /** Net debit (debit minus credit) per GL account for one asset, excluding rejected entries. */
    private Map<Long, BigDecimal> ledgerNetByAccount(Long assetId) {
        Map<Long, BigDecimal> net = new HashMap<>();
        for (JournalLine l : journalLineRepository.findByAssetAndStatuses(assetId, ACTIVE)) {
            net.merge(l.getAccount().getId(), l.getDebit().subtract(l.getCredit()), BigDecimal::add);
        }
        return net;
    }

    private BigDecimal netOf(Map<Long, BigDecimal> net, GLAccount account) {
        return account == null ? BigDecimal.ZERO : net.getOrDefault(account.getId(), BigDecimal.ZERO);
    }

    @Transactional
    public JournalEntry post(Long journalEntryId, String postedBy) {
        JournalEntry entry = journalEntryRepository.findById(journalEntryId)
                .orElseThrow(() -> new IllegalArgumentException("Journal entry not found: " + journalEntryId));
        if (entry.getStatus() != JournalStatus.UNPOSTED) {
            throw new IllegalArgumentException("Only unposted journal entries can be posted.");
        }
        if (!isBalanced(entry)) {
            throw new IllegalArgumentException("Journal entry " + entry.getJournalNumber() + " is not balanced and cannot be posted.");
        }
        entry.setStatus(JournalStatus.POSTED);
        entry.setPostedBy(postedBy);
        entry.setPostedAt(LocalDateTime.now());
        return journalEntryRepository.save(entry);
    }

    @Transactional
    public JournalEntry reject(Long journalEntryId, String reason, String rejectedBy) {
        JournalEntry entry = journalEntryRepository.findById(journalEntryId)
                .orElseThrow(() -> new IllegalArgumentException("Journal entry not found: " + journalEntryId));
        if (entry.getStatus() != JournalStatus.UNPOSTED) {
            throw new IllegalArgumentException("Only unposted journal entries can be rejected.");
        }
        return rejectInternal(entry, reason, rejectedBy);
    }

    private JournalEntry rejectInternal(JournalEntry entry, String reason, String rejectedBy) {
        entry.setStatus(JournalStatus.REJECTED);
        entry.setRejectionReason(reason);
        entry.setRejectedBy(rejectedBy);
        entry.setRejectedAt(LocalDateTime.now());
        return journalEntryRepository.save(entry);
    }

    @Transactional(readOnly = true)
    public List<JournalEntry> findAll() {
        return journalEntryRepository.findAllByOrderByDateDesc();
    }

    @Transactional(readOnly = true)
    public List<JournalEntry> findByStatus(JournalStatus status) {
        return journalEntryRepository.findByStatusOrderByDateDesc(status);
    }
}
