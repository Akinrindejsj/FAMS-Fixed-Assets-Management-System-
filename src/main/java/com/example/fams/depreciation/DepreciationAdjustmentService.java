package com.example.fams.depreciation;

import com.example.fams.assets.Asset;
import com.example.fams.assets.AssetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
public class DepreciationAdjustmentService {

    private final DepreciationAdjustmentRepository adjustmentRepository;
    private final AssetRepository assetRepository;

    public DepreciationAdjustmentService(DepreciationAdjustmentRepository adjustmentRepository, AssetRepository assetRepository) {
        this.adjustmentRepository = adjustmentRepository;
        this.assetRepository = assetRepository;
    }

    @Transactional
    public DepreciationAdjustment create(String assetCode, LocalDate date, String period,
                                          DepreciationAdjustmentType type, BigDecimal amount,
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
        if (amount == null || amount.compareTo(BigDecimal.ZERO) == 0) {
            throw new IllegalArgumentException("Amount must be a non-zero value (negative for a reversal).");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("A reason is required for audit purposes.");
        }

        DepreciationAdjustment adjustment = new DepreciationAdjustment();
        adjustment.setAsset(asset);
        adjustment.setDate(date);
        adjustment.setPeriod(period);
        adjustment.setType(type);
        adjustment.setAmount(amount);
        adjustment.setReason(reason.trim());
        adjustment.setRecordedBy(recordedBy);

        return adjustmentRepository.save(adjustment);
    }

    @Transactional(readOnly = true)
    public List<DepreciationAdjustment> findByAsset(Long assetId) {
        return adjustmentRepository.findByAssetIdOrderByDateDesc(assetId);
    }

    @Transactional(readOnly = true)
    public List<DepreciationAdjustment> findRecent() {
        return adjustmentRepository.findAllByOrderByDateDesc();
    }

    @Transactional(readOnly = true)
    public List<DepreciationAdjustment> findByDateRange(LocalDate fromDate, LocalDate toDate) {
        LocalDate from = fromDate == null ? LocalDate.of(1900, 1, 1) : fromDate;
        LocalDate to = toDate == null ? LocalDate.now() : toDate;
        return adjustmentRepository.findByDateBetweenOrderByDateDesc(from, to);
    }
}