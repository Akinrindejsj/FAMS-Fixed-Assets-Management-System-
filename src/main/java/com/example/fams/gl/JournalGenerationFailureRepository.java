package com.example.fams.gl;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface JournalGenerationFailureRepository extends JpaRepository<JournalGenerationFailure, Long> {

    List<JournalGenerationFailure> findByCreatedAtBetweenOrderByCreatedAtDesc(LocalDateTime from, LocalDateTime to);
}
