package com.example.fams.assets;

import com.example.fams.gl.GLAccountingService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Service
public class CapitalAdditionService {

    private final CapitalAdditionRepository capitalAdditionRepository;
    private final AssetRepository assetRepository;
    private final GLAccountingService glAccountingService;

    public CapitalAdditionService(CapitalAdditionRepository capitalAdditionRepository, AssetRepository assetRepository,
                                  GLAccountingService glAccountingService) {
        this.capitalAdditionRepository = capitalAdditionRepository;
        this.assetRepository = assetRepository;
        this.glAccountingService = glAccountingService;
    }

    @Transactional
    public CapitalAddition create(String assetCode, LocalDate date, BigDecimal amount,
                                   CapitalAdditionType type, String description, String recordedBy) {
        if (assetCode == null || assetCode.isBlank()) {
            throw new IllegalArgumentException("Asset code is required.");
        }
        Asset asset = assetRepository.findByAssetCodeIgnoreCase(assetCode.trim())
                .orElseThrow(() -> new IllegalArgumentException("Asset code not found: " + assetCode));
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Amount must be a positive value.");
        }
        if (date == null) {
            throw new IllegalArgumentException("Date is required.");
        }
        if (type == null) {
            throw new IllegalArgumentException("Type is required.");
        }

        CapitalAddition addition = new CapitalAddition();
        addition.setAsset(asset);
        addition.setDate(date);
        addition.setAmount(amount);
        addition.setType(type);
        addition.setDescription(description == null ? "" : description.trim());
        addition.setRecordedBy(recordedBy);

        CapitalAddition saved = capitalAdditionRepository.save(addition);
        glAccountingService.generateForCapitalAddition(saved);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<CapitalAddition> findByAsset(Long assetId) {
        return capitalAdditionRepository.findByAssetIdOrderByDateDesc(assetId);
    }

    @Transactional(readOnly = true)
    public List<CapitalAddition> findRecent() {
        return capitalAdditionRepository.findAllByOrderByDateDesc();
    }

    @Transactional(readOnly = true)
    public List<CapitalAddition> findByDateRange(LocalDate fromDate, LocalDate toDate) {
        LocalDate from = fromDate == null ? LocalDate.of(1900, 1, 1) : fromDate;
        LocalDate to = toDate == null ? LocalDate.now() : toDate;
        return capitalAdditionRepository.findByDateBetweenOrderByDateDesc(from, to);
    }
}