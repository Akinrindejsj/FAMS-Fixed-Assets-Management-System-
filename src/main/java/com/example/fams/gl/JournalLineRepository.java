package com.example.fams.gl;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface JournalLineRepository extends JpaRepository<JournalLine, Long> {

    List<JournalLine> findByAccountIdOrderByJournalEntry_DateAsc(Long accountId);

    List<JournalLine> findByJournalEntry_StatusOrderByJournalEntry_DateAsc(JournalStatus status);
}