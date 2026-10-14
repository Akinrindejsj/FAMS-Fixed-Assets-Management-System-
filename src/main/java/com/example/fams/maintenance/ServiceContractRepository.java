package com.example.fams.maintenance;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ServiceContractRepository extends JpaRepository<ServiceContract, Long> {

    @EntityGraph(attributePaths = "asset")
    List<ServiceContract> findAllByOrderByEndDateAsc();
}
