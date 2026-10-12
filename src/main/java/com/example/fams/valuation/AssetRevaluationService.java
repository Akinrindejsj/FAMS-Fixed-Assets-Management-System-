package com.example.fams.valuation;

import com.example.fams.assets.Asset;
import com.example.fams.assets.AssetRepository;
import com.example.fams.depreciation.DepreciationPosting;
import com.example.fams.depreciation.DepreciationService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
public class AssetRevaluationService {

    private final AssetRevaluationRepository revaluationRepository;
    private final AssetRepository assetRepository;
    private final DepreciationService depreciationService;

    public AssetRevaluationService(AssetRevaluationRepository revaluationRepository, AssetRepository assetRepository,
                                    DepreciationService depreciationService) {
        this.revaluationRepository = revaluationRepository;
        this.assetRepository = assetRepository;
        this.depreciationService = depreciationService;
    }

    @Transactional
    public AssetRevaluation create(String assetCode, LocalDate date, BigDecimal revaluedAmount, String reason, String recordedBy) {
        if (assetCode == null || assetCode.isBlank()) {
            throw new IllegalArgumentException("Asset code is required.");
        }
        Asset asset = assetRepository.findByAssetCodeIgnoreCase(assetCode.trim())
                .orElseThrow(() -> new IllegalArgumentException("Asset code not found: " + assetCode));
        if (date == null) {
            throw new IllegalArgumentException("Date is required.");
        }
        if (revaluedAmount == null || revaluedAmount.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Revalued amount must be zero or positive.");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("A reason is required for audit purposes.");
        }

        BigDecimal previousCarryingAmount = currentCarryingAmount(asset);

        AssetRevaluation revaluation = new AssetRevaluation();
        revaluation.setAsset(asset);
        revaluation.setDate(date);
        revaluation.setPreviousCarryingAmount(previousCarryingAmount);
        revaluation.setRevaluedAmount(revaluedAmount);
        revaluation.setGainLoss(revaluedAmount.subtract(previousCarryingAmount));
        revaluation.setReason(reason.trim());
        revaluation.setRecordedBy(recordedBy);

        return revaluationRepository.save(revaluation);
    }

    /** Current NBV (cost - accumulated depreciation), same basis the reports use. */
    private BigDecimal currentCarryingAmount(Asset asset) {
        DepreciationPosting latest = depreciationService.getLatestPostingsForAssets(List.of(asset.getId())).get(asset.getId());
        if (latest != null && latest.getBookValue() != null) {
            return latest.getBookValue();
        }
        return asset.getPurchaseCost() == null ? BigDecimal.ZERO : asset.getPurchaseCost();
    }

    @Transactional(readOnly = true)
    public List<AssetRevaluation> findByAsset(Long assetId) {
        return revaluationRepository.findByAssetIdOrderByDateDesc(assetId);
    }

    @Transactional(readOnly = true)
    public List<AssetRevaluation> findRecent() {
        return revaluationRepository.findAllByOrderByDateDesc();
    }

    @Transactional(readOnly = true)
    public List<AssetRevaluation> findByDateRange(LocalDate fromDate, LocalDate toDate) {
        LocalDate from = fromDate == null ? LocalDate.of(1900, 1, 1) : fromDate;
        LocalDate to = toDate == null ? LocalDate.now() : toDate;
        return revaluationRepository.findByDateBetweenOrderByDateDesc(from, to);
    }
}