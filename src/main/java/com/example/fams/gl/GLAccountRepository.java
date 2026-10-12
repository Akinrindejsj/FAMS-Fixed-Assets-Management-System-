package com.example.fams.gl;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GLAccountRepository extends JpaRepository<GLAccount, Long> {

    List<GLAccount> findAllByOrderByAccountCodeAsc();

    Optional<GLAccount> findByAccountCodeIgnoreCase(String accountCode);
}