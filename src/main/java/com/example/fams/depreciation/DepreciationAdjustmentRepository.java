package com.example.fams.depreciation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface DepreciationAdjustmentRepository extends JpaRepository<DepreciationAdjustment, Long> {

    List<DepreciationAdjustment> findByAssetIdOrderByDateDesc(Long assetId);

    List<DepreciationAdjustment> findByDateBetweenOrderByDateDesc(LocalDate fromDate, LocalDate toDate);

    List<DepreciationAdjustment> findAllByOrderByDateDesc();
}