package com.example.fams.gl;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

public interface JournalEntryRepository extends JpaRepository<JournalEntry, Long> {

    List<JournalEntry> findAllByOrderByDateDesc();

    List<JournalEntry> findByStatusOrderByDateDesc(JournalStatus status);

    List<JournalEntry> findByDateBetweenOrderByDateDesc(LocalDate fromDate, LocalDate toDate);

    List<JournalEntry> findByPeriodOrderByDateDesc(String period);

    boolean existsBySourceTypeAndSourceReferenceIdAndStatusIn(JournalSourceType sourceType, Long sourceReferenceId,
                                                              Collection<JournalStatus> statuses);

    /** Entries in a date range with their lines loaded, for totals without one query per entry. */
    @Query("select distinct e from JournalEntry e left join fetch e.lines "
            + "where e.date between :fromDate and :toDate order by e.date desc, e.id desc")
    List<JournalEntry> findWithLinesBetween(@Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate);
}
