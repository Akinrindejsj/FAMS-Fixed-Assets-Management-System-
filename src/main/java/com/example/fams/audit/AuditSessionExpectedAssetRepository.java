package com.example.fams.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface AuditSessionExpectedAssetRepository extends JpaRepository<AuditSessionExpectedAsset, Long> {

    boolean existsBySessionId(Long sessionId);

    boolean existsBySessionIdAndAssetId(Long sessionId, Long assetId);

    @Query("select e from AuditSessionExpectedAsset e join fetch e.asset where e.session.id = :sessionId")
    List<AuditSessionExpectedAsset> findBySession(@Param("sessionId") Long sessionId);
}
