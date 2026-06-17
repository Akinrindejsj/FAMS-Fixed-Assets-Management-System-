package com.example.fams.organization;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BranchRepository extends JpaRepository<Branch, Long> {
    Optional<Branch> findByNameAndCompanyIdAndIsActiveTrue(String name, Long companyId);
    Optional<Branch> findByBranchCodeAndIsActiveTrue(String branchCode);
    List<Branch> findByCompanyIdAndIsActiveTrueOrderByCreatedAtDesc(Long companyId);
    List<Branch> findByLocationIdAndIsActiveTrueOrderByCreatedAtDesc(Long locationId);
    List<Branch> findByStatusAndIsActiveTrueOrderByCreatedAtDesc(Branch.BranchStatus status);
}

