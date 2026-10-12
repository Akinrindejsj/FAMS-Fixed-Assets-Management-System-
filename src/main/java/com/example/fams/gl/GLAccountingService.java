package com.example.fams.gl;

import com.example.fams.assets.Asset;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Section 8 — generates double-entry journal entries from existing asset transactions
 * (acquisition, depreciation runs, capital additions, revaluations, impairments,
 * disposals) and manages their posting workflow.
 *
 * <p>Every generation method here is called from elsewhere in the app right after the
 * underlying transaction is saved (see call sites in AssetService, DepreciationService,
 * CapitalAdditionService, AssetRevaluationService, AssetImpairmentService, and
 * AssetLifecycleService). If account mapping can't be resolved for an asset's category
 * (no category-specific mapping and no global default configured), journal generation is
 * silently skipped rather than blocking the underlying transaction — accounting setup
 * shouldn't be able to prevent someone from recording a real-world event like a
 * disposal. Skipped generations are not currently logged anywhere visible; that's a
 * reasonable follow-up if silent gaps become a problem in practice.
 */
@Service
public class GLAccountingService {

    private final GLAccountMappingRepository mappingRepository;
    private final JournalEntryRepository journalEntryRepository;

    public GLAccountingService(GLAccountMappingRepository mappingRepository, JournalEntryRepository journalEntryRepository) {
        this.mappingRepository = mappingRepository;
        this.journalEntryRepository = journalEntryRepository;
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

    private String nextJournalNumber() {
        long count = journalEntryRepository.count();
        return "JE-" + String.format("%06d", count + 1);
    }

    private boolean isBalanced(JournalEntry entry) {
        BigDecimal totalDebit = entry.getLines().stream().map(JournalLine::getDebit).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalCredit = entry.getLines().stream().map(JournalLine::getCredit).reduce(BigDecimal.ZERO, BigDecimal::add);
        return totalDebit.setScale(2, RoundingMode.HALF_UP).compareTo(totalCredit.setScale(2, RoundingMode.HALF_UP)) == 0;
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

    /**
     * Saves the entry if it balances and every required account was resolved; otherwise
     * discards it silently (see class javadoc) and returns empty.
     */
    private Optional<JournalEntry> saveIfValid(JournalEntry entry) {
        boolean anyMissingAccount = entry.getLines().stream().anyMatch(l -> l.getAccount() == null);
        if (anyMissingAccount || entry.getLines().isEmpty() || !isBalanced(entry)) {
            return Optional.empty();
        }
        entry.setJournalNumber(nextJournalNumber());
        return Optional.of(journalEntryRepository.save(entry));
    }

    // ---- Generation: one method per source type ----

    @Transactional
    public Optional<JournalEntry> generateForAcquisition(Asset asset) {
        Optional<GLAccountMapping> mappingOpt = resolveMapping(asset.getCategory());
        if (mappingOpt.isEmpty()) return Optional.empty();
        GLAccountMapping mapping = mappingOpt.get();
        BigDecimal cost = asset.getPurchaseCost() == null ? BigDecimal.ZERO : asset.getPurchaseCost();
        if (cost.compareTo(BigDecimal.ZERO) <= 0) return Optional.empty();

        JournalEntry entry = new JournalEntry();
        entry.setDate(asset.getPurchaseDate() != null ? asset.getPurchaseDate() : LocalDate.now());
        entry.setSourceType(JournalSourceType.ACQUISITION);
        entry.setSourceReferenceId(asset.getId());
        entry.setDescription("Acquisition of " + asset.getAssetCode() + " — " + asset.getName());
        entry.addLine(line(mapping.getAssetCostAccount(), asset, cost, null, "Asset cost capitalised"));
        entry.addLine(line(mapping.getCashPayablesAccount(), asset, null, cost, "Consideration for acquisition"));
        return saveIfValid(entry);
    }

    @Transactional
    public Optional<JournalEntry> generateForDepreciationRun(Long depreciationRunId, String period,
                                                               List<com.example.fams.depreciation.DepreciationPosting> postings) {
        if (postings == null || postings.isEmpty()) return Optional.empty();

        JournalEntry entry = new JournalEntry();
        entry.setDate(LocalDate.now());
        entry.setPeriod(period);
        entry.setSourceType(JournalSourceType.DEPRECIATION_RUN);
        entry.setSourceReferenceId(depreciationRunId);
        entry.setDescription("Depreciation run for period " + period);

        for (com.example.fams.depreciation.DepreciationPosting posting : postings) {
            if (posting.getDepreciationCharge() == null || posting.getDepreciationCharge().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            Optional<GLAccountMapping> mappingOpt = resolveMapping(posting.getCategory());
            if (mappingOpt.isEmpty()) continue;
            GLAccountMapping mapping = mappingOpt.get();
            if (mapping.getDepreciationExpenseAccount() == null || mapping.getAccumulatedDepreciationAccount() == null) continue;

            entry.addLine(line(mapping.getDepreciationExpenseAccount(), null, posting.getDepreciationCharge(), null,
                    posting.getAssetCode() + " depreciation charge"));
            entry.addLine(line(mapping.getAccumulatedDepreciationAccount(), null, null, posting.getDepreciationCharge(),
                    posting.getAssetCode() + " accumulated depreciation"));
        }
        return saveIfValid(entry);
    }

    @Transactional
    public Optional<JournalEntry> generateForCapitalAddition(com.example.fams.assets.CapitalAddition addition) {
        Asset asset = addition.getAsset();
        Optional<GLAccountMapping> mappingOpt = resolveMapping(asset.getCategory());
        if (mappingOpt.isEmpty()) return Optional.empty();
        GLAccountMapping mapping = mappingOpt.get();

        JournalEntry entry = new JournalEntry();
        entry.setDate(addition.getDate());
        entry.setSourceType(JournalSourceType.CAPITAL_ADDITION);
        entry.setSourceReferenceId(addition.getId());
        entry.setDescription("Capital addition (" + addition.getType() + ") for " + asset.getAssetCode());
        entry.addLine(line(mapping.getAssetCostAccount(), asset, addition.getAmount(), null, "Capital addition capitalised"));
        entry.addLine(line(mapping.getCashPayablesAccount(), asset, null, addition.getAmount(), "Consideration for addition"));
        return saveIfValid(entry);
    }

    @Transactional
    public Optional<JournalEntry> generateForRevaluation(com.example.fams.valuation.AssetRevaluation revaluation) {
        Asset asset = revaluation.getAsset();
        Optional<GLAccountMapping> mappingOpt = resolveMapping(asset.getCategory());
        if (mappingOpt.isEmpty()) return Optional.empty();
        GLAccountMapping mapping = mappingOpt.get();
        BigDecimal gainLoss = revaluation.getGainLoss();
        if (gainLoss == null || gainLoss.compareTo(BigDecimal.ZERO) == 0) return Optional.empty();

        JournalEntry entry = new JournalEntry();
        entry.setDate(revaluation.getDate());
        entry.setSourceType(JournalSourceType.REVALUATION);
        entry.setSourceReferenceId(revaluation.getId());
        entry.setDescription("Revaluation of " + asset.getAssetCode());

        if (gainLoss.compareTo(BigDecimal.ZERO) > 0) {
            // Write-up: Dr Asset Cost, Cr Revaluation Surplus
            entry.addLine(line(mapping.getAssetCostAccount(), asset, gainLoss, null, "Revaluation write-up"));
            entry.addLine(line(mapping.getRevaluationSurplusAccount(), asset, null, gainLoss, "Revaluation surplus"));
        } else {
            BigDecimal loss = gainLoss.abs();
            // Write-down: Dr Revaluation Loss, Cr Asset Cost
            entry.addLine(line(mapping.getRevaluationLossAccount(), asset, loss, null, "Revaluation write-down"));
            entry.addLine(line(mapping.getAssetCostAccount(), asset, null, loss, "Revaluation write-down"));
        }
        return saveIfValid(entry);
    }

    @Transactional
    public Optional<JournalEntry> generateForImpairment(com.example.fams.valuation.AssetImpairment impairment) {
        Asset asset = impairment.getAsset();
        Optional<GLAccountMapping> mappingOpt = resolveMapping(asset.getCategory());
        if (mappingOpt.isEmpty()) return Optional.empty();
        GLAccountMapping mapping = mappingOpt.get();
        BigDecimal amount = impairment.getAmount();
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) return Optional.empty();

        JournalEntry entry = new JournalEntry();
        entry.setDate(impairment.getDate());
        entry.setSourceType(JournalSourceType.IMPAIRMENT);
        entry.setSourceReferenceId(impairment.getId());
        entry.setDescription((impairment.getType() == com.example.fams.valuation.ImpairmentType.REVERSAL ? "Impairment reversal for " : "Impairment loss for ") + asset.getAssetCode());

        if (impairment.getType() == com.example.fams.valuation.ImpairmentType.IMPAIRMENT_LOSS) {
            entry.addLine(line(mapping.getImpairmentLossAccount(), asset, amount, null, "Impairment loss recognised"));
            entry.addLine(line(mapping.getAccumulatedImpairmentAccount(), asset, null, amount, "Accumulated impairment"));
        } else {
            entry.addLine(line(mapping.getAccumulatedImpairmentAccount(), asset, amount, null, "Impairment reversal"));
            entry.addLine(line(mapping.getImpairmentLossAccount(), asset, null, amount, "Impairment reversal (contra-expense)"));
        }
        return saveIfValid(entry);
    }

    /**
     * Disposal: Dr Accumulated Depreciation, Dr Proceeds Receivable (if any), Cr Asset
     * Cost, and a balancing Dr Loss or Cr Gain on Disposal for the difference — this
     * balances by construction since financialImpact was computed as proceeds - NBV.
     */
    @Transactional
    public Optional<JournalEntry> generateForDisposal(com.example.fams.lifecycle.AssetLifecycleWorkflow workflow) {
        Asset asset = workflow.getAsset();
        Optional<GLAccountMapping> mappingOpt = resolveMapping(asset.getCategory());
        if (mappingOpt.isEmpty()) return Optional.empty();
        GLAccountMapping mapping = mappingOpt.get();

        BigDecimal cost = asset.getPurchaseCost() == null ? BigDecimal.ZERO : asset.getPurchaseCost();
        BigDecimal accumDep = workflow.getAccumulatedDepreciation() == null ? BigDecimal.ZERO : workflow.getAccumulatedDepreciation();
        BigDecimal proceeds = workflow.getDisposalProceeds() == null ? BigDecimal.ZERO : workflow.getDisposalProceeds();
        BigDecimal financialImpact = workflow.getFinancialImpact() == null ? BigDecimal.ZERO : workflow.getFinancialImpact();

        JournalEntry entry = new JournalEntry();
        entry.setDate(workflow.getRequestedEffectiveDate() != null ? workflow.getRequestedEffectiveDate() : LocalDate.now());
        entry.setSourceType(JournalSourceType.DISPOSAL);
        entry.setSourceReferenceId(workflow.getId());
        entry.setDescription("Disposal (" + workflow.getDisposalMethod() + ") of " + asset.getAssetCode());

        entry.addLine(line(mapping.getAccumulatedDepreciationAccount(), asset, accumDep, null, "Clear accumulated depreciation"));
        if (proceeds.compareTo(BigDecimal.ZERO) > 0) {
            entry.addLine(line(mapping.getDisposalProceedsAccount(), asset, proceeds, null, "Disposal proceeds"));
        }
        entry.addLine(line(mapping.getAssetCostAccount(), asset, null, cost, "Remove asset cost"));
        if (financialImpact.compareTo(BigDecimal.ZERO) > 0) {
            entry.addLine(line(mapping.getDisposalGainAccount(), asset, null, financialImpact, "Gain on disposal"));
        } else if (financialImpact.compareTo(BigDecimal.ZERO) < 0) {
            entry.addLine(line(mapping.getDisposalLossAccount(), asset, financialImpact.abs(), null, "Loss on disposal"));
        }
        return saveIfValid(entry);
    }

    // ---- Posting workflow ----

    @Transactional
    public JournalEntry post(Long journalEntryId, String postedBy) {
        JournalEntry entry = journalEntryRepository.findById(journalEntryId)
                .orElseThrow(() -> new IllegalArgumentException("Journal entry not found: " + journalEntryId));
        if (entry.getStatus() != JournalStatus.UNPOSTED) {
            throw new IllegalArgumentException("Only unposted journal entries can be posted.");
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
        entry.setStatus(JournalStatus.REJECTED);
        entry.setRejectionReason(reason);
        entry.setPostedBy(rejectedBy);
        entry.setPostedAt(LocalDateTime.now());
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