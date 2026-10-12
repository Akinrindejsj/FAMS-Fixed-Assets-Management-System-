package com.example.fams.gl;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface JournalEntryRepository extends JpaRepository<JournalEntry, Long> {

    List<JournalEntry> findAllByOrderByDateDesc();

    List<JournalEntry> findByStatusOrderByDateDesc(JournalStatus status);

    List<JournalEntry> findByDateBetweenOrderByDateDesc(LocalDate fromDate, LocalDate toDate);

    List<JournalEntry> findByPeriodOrderByDateDesc(String period);

    long countByJournalNumberStartingWith(String prefix);
}