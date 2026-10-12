package com.example.fams.depreciation;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DepreciationRunRepository extends JpaRepository<DepreciationRun, Long> {

    List<DepreciationRun> findAllByOrderByTriggeredAtDesc();

    List<DepreciationRun> findByPeriodOrderByTriggeredAtDesc(String period);
}