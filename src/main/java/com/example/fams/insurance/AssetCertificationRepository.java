package com.example.fams.insurance;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AssetCertificationRepository extends JpaRepository<AssetCertification, Long> {

    @EntityGraph(attributePaths = "asset")
    List<AssetCertification> findAllByOrderByExpiryDateAsc();
}
