package com.example.fams.valuation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface AssetImpairmentRepository extends JpaRepository<AssetImpairment, Long> {

    List<AssetImpairment> findByAssetIdOrderByDateDesc(Long assetId);

    List<AssetImpairment> findByDateBetweenOrderByDateDesc(LocalDate fromDate, LocalDate toDate);

    List<AssetImpairment> findAllByOrderByDateDesc();

    List<AssetImpairment> findByAssetIdInOrderByDateDesc(Collection<Long> assetIds);
}