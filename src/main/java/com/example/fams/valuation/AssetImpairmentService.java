package com.example.fams.valuation;

import com.example.fams.assets.Asset;
import com.example.fams.assets.AssetRepository;
import com.example.fams.depreciation.DepreciationPosting;
import com.example.fams.depreciation.DepreciationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class AssetImpairmentService {

    private final AssetImpairmentRepository impairmentRepository;
    private final AssetRepository assetRepository;
    private final DepreciationService depreciationService;

    public AssetImpairmentService(AssetImpairmentRepository impairmentRepository, AssetRepository assetRepository,
                                   DepreciationService depreciationService) {
        this.impairmentRepository = impairmentRepository;
        this.assetRepository = assetRepository;
        this.depreciationService = depreciationService;
    }

    @Transactional
    public AssetImpairment create(String assetCode, LocalDate date, ImpairmentType type, BigDecimal amount,
                                   String reason, String recordedBy) {
        if (assetCode == null || assetCode.isBlank()) {
            throw new IllegalArgumentException("Asset code is required.");
        }
        Asset asset = assetRepository.findByAssetCodeIgnoreCase(assetCode.trim())
                .orElseThrow(() -> new IllegalArgumentException("Asset code not found: " + assetCode));
        if (date == null) {
            throw new IllegalArgumentException("Date is required.");
        }
        if (type == null) {
            throw new IllegalArgumentException("Type is required.");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be a positive value.");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("A reason is required for audit purposes.");
        }

        BigDecimal before = currentImpairedCarryingAmount(asset);
        BigDecimal after = type == ImpairmentType.IMPAIRMENT_LOSS ? before.subtract(amount) : before.add(amount);

        AssetImpairment impairment = new AssetImpairment();
        impairment.setAsset(asset);
        impairment.setDate(date);
        impairment.setType(type);
        impairment.setAmount(amount);
        impairment.setCarryingAmountBefore(before);
        impairment.setCarryingAmountAfter(after);
        impairment.setReason(reason.trim());
        impairment.setRecordedBy(recordedBy);

        return impairmentRepository.save(impairment);
    }

    /** Current NBV net of all prior impairments/reversals recorded for this asset. */
    private BigDecimal currentImpairedCarryingAmount(Asset asset) {
        DepreciationPosting latest = depreciationService.getLatestPostingsForAssets(List.of(asset.getId())).get(asset.getId());
        BigDecimal nbv = latest != null && latest.getBookValue() != null ? latest.getBookValue()
                : (asset.getPurchaseCost() == null ? BigDecimal.ZERO : asset.getPurchaseCost());
        BigDecimal cumulativeImpairment = cumulativeNetImpairment(findByAsset(asset.getId()));
        return nbv.subtract(cumulativeImpairment);
    }

    private BigDecimal cumulativeNetImpairment(List<AssetImpairment> history) {
        BigDecimal losses = history.stream().filter(i -> i.getType() == ImpairmentType.IMPAIRMENT_LOSS)
                .map(AssetImpairment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal reversals = history.stream().filter(i -> i.getType() == ImpairmentType.REVERSAL)
                .map(AssetImpairment::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        return losses.subtract(reversals);
    }

    /**
     * Bulk cumulative net impairment (losses minus reversals) per asset, used by the Net
     * Book Value Report (item 1) to avoid one query per asset.
     */
    @Transactional(readOnly = true)
    public Map<Long, BigDecimal> getCumulativeImpairmentForAssets(Collection<Long> assetIds) {
        if (assetIds == null || assetIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<AssetImpairment>> byAsset = impairmentRepository.findByAssetIdInOrderByDateDesc(assetIds).stream()
                .collect(Collectors.groupingBy(i -> i.getAsset().getId()));
        return byAsset.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> cumulativeNetImpairment(e.getValue())));
    }

    @Transactional(readOnly = true)
    public List<AssetImpairment> findByAsset(Long assetId) {
        return impairmentRepository.findByAssetIdOrderByDateDesc(assetId);
    }

    @Transactional(readOnly = true)
    public List<AssetImpairment> findRecent() {
        return impairmentRepository.findAllByOrderByDateDesc();
    }

    @Transactional(readOnly = true)
    public List<AssetImpairment> findByDateRange(LocalDate fromDate, LocalDate toDate) {
        LocalDate from = fromDate == null ? LocalDate.of(1900, 1, 1) : fromDate;
        LocalDate to = toDate == null ? LocalDate.now() : toDate;
        return impairmentRepository.findByDateBetweenOrderByDateDesc(from, to);
    }
}