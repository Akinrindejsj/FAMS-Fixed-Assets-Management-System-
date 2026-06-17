package com.example.fams.settings;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SystemParameterRepository extends JpaRepository<SystemParameter, String> {

    List<SystemParameter> findAllByOrderByKeyNameAsc();
}
