package com.example.fams.governance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface UserActivityLogRepository extends JpaRepository<UserActivityLog, Long> {

    @Query("select l from UserActivityLog l where l.occurredAt between :from and :to order by l.occurredAt desc")
    List<UserActivityLog> findInRange(@Param("from") LocalDateTime from, @Param("to") LocalDateTime to);
}
