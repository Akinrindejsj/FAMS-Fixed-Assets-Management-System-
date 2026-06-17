package com.example.fams.organization;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DepartmentHeadRepository extends JpaRepository<DepartmentHead, Long> {
    Optional<DepartmentHead> findByDepartmentIdAndUserIdAndIsActiveTrue(Long departmentId, String userId);
    Optional<DepartmentHead> findByDepartmentIdAndIsPrimaryTrueAndIsActiveTrue(Long departmentId);
    List<DepartmentHead> findByDepartmentIdAndIsActiveTrueOrderByAssignedAtDesc(Long departmentId);
    List<DepartmentHead> findByUserIdAndIsActiveTrueOrderByAssignedAtDesc(String userId);
    List<DepartmentHead> findByStatusAndIsActiveTrueOrderByAssignedAtDesc(DepartmentHead.HeadStatus status);

    @Query("SELECT dh FROM DepartmentHead dh WHERE dh.status = 'ACTIVE' AND dh.isActive = true ORDER BY dh.assignedAt DESC")
    List<DepartmentHead> findAllActiveDepartmentHeads();

    @Query("SELECT dh FROM DepartmentHead dh WHERE dh.department.company.id = :companyId AND dh.isActive = true ORDER BY dh.assignedAt DESC")
    List<DepartmentHead> findByCompanyId(@Param("companyId") Long companyId);
}


