package com.example.fams.organization;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LocationRepository extends JpaRepository<Location, Long> {
    Optional<Location> findByNameAndCompanyIdAndIsActiveTrue(String name, Long companyId);
    List<Location> findByCompanyIdAndIsActiveTrueOrderByCreatedAtDesc(Long companyId);
    List<Location> findByStatusAndIsActiveTrueOrderByCreatedAtDesc(Location.LocationStatus status);
}

