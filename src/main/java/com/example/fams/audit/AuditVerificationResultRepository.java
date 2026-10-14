package com.example.fams.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;

import java.util.List;
import java.util.Optional;

public interface AuditVerificationResultRepository extends JpaRepository<AuditVerificationResult, Long> {

    List<AuditVerificationResult> findTop8ByOrderByVerifiedAtDesc();

    List<AuditVerificationResult> findBySessionIdOrderByVerifiedAtDesc(Long sessionId);

    Optional<AuditVerificationResult> findBySessionIdAndAssetId(Long sessionId, Long assetId);

    long countBySessionId(Long sessionId);

    long countBySessionIdAndResultStatus(Long sessionId, AuditVerificationStatus resultStatus);

    long countBySessionIdAndDiscrepancyTypeIsNotNull(Long sessionId);

    long countByDiscrepancyTypeIsNotNull();

    @Query("select r from AuditVerificationResult r join fetch r.asset join fetch r.session "
            + "where r.session.id = :sessionId order by r.verifiedAt desc")
    List<AuditVerificationResult> findBySessionFetched(@Param("sessionId") Long sessionId);

    @Query("select r from AuditVerificationResult r join fetch r.asset join fetch r.session "
            + "where r.verifiedAt between :from and :to order by r.verifiedAt desc")
    List<AuditVerificationResult> findInRangeFetched(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
