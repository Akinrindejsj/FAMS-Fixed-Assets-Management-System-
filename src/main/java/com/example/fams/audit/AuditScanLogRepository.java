package com.example.fams.audit;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface AuditScanLogRepository extends JpaRepository<AuditScanLog, Long> {

    @Query("select l from AuditScanLog l join fetch l.session join fetch l.asset "
            + "where l.scannedAt between :from and :to order by l.scannedAt desc")
    List<AuditScanLog> findInRange(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);

    @Query("select l from AuditScanLog l join fetch l.session join fetch l.asset "
            + "where l.session.id = :sessionId order by l.scannedAt desc")
    List<AuditScanLog> findBySession(@Param("sessionId") Long sessionId);
}
