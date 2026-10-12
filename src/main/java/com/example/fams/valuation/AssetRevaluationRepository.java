package com.example.fams.valuation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface AssetRevaluationRepository extends JpaRepository<AssetRevaluation, Long> {

    List<AssetRevaluation> findByAssetIdOrderByDateDesc(Long assetId);

    List<AssetRevaluation> findByDateBetweenOrderByDateDesc(LocalDate fromDate, LocalDate toDate);

    List<AssetRevaluation> findAllByOrderByDateDesc();

    List<AssetRevaluation> findByAssetIdInOrderByDateDesc(Collection<Long> assetIds);
}