package com.example.fams.assets;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface CapitalAdditionRepository extends JpaRepository<CapitalAddition, Long> {

    List<CapitalAddition> findByAssetIdOrderByDateDesc(Long assetId);

    List<CapitalAddition> findByDateBetweenOrderByDateDesc(LocalDate fromDate, LocalDate toDate);

    List<CapitalAddition> findAllByOrderByDateDesc();
}
