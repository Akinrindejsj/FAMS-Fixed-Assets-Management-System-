package com.example.fams.governance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface AssetChangeLogRepository extends JpaRepository<AssetChangeLog, Long> {

    @Query("select c from AssetChangeLog c where c.changedAt between :from and :to order by c.changedAt desc, c.id desc")
    List<AssetChangeLog> findInRange(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
