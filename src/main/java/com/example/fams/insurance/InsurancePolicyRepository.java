package com.example.fams.insurance;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InsurancePolicyRepository extends JpaRepository<InsurancePolicy, Long> {

    boolean existsByPolicyNumberIgnoreCase(String policyNumber);

    List<InsurancePolicy> findAllByOrderByEndDateAsc();
}
