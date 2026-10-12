package com.example.fams.gl;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GLAccountMappingRepository extends JpaRepository<GLAccountMapping, Long> {

    List<GLAccountMapping> findAllByOrderByCategoryAsc();

    Optional<GLAccountMapping> findByCategoryIgnoreCase(String category);

    Optional<GLAccountMapping> findByCategoryIsNull();
}