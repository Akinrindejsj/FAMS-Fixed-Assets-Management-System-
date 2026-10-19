package com.example.fams.insurance;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InsuranceCoverageRepository extends JpaRepository<InsuranceCoverage, Long> {

    @EntityGraph(attributePaths = {"policy", "asset"})
    List<InsuranceCoverage> findAllByOrderByIdAsc();

    boolean existsByPolicyIdAndAssetId(Long policyId, Long assetId);

    long countByPolicyId(Long policyId);

    void deleteByPolicyId(Long policyId);
}
