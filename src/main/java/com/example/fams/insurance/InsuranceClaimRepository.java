package com.example.fams.insurance;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InsuranceClaimRepository extends JpaRepository<InsuranceClaim, Long> {

    @EntityGraph(attributePaths = {"policy", "asset"})
    List<InsuranceClaim> findAllByOrderByIncidentDateDesc();

    long countByPolicyId(Long policyId);
}
