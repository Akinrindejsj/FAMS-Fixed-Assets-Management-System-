package com.example.fams.organization;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DepartmentHeadRepository extends JpaRepository<DepartmentHead, Long> {
    @EntityGraph(attributePaths = {"department"})
    Optional<DepartmentHead> findByDepartmentIdAndUserIdAndIsActiveTrue(Long departmentId, String userId);

    @EntityGraph(attributePaths = {"department"})
    Optional<DepartmentHead> findByDepartmentIdAndIsPrimaryTrueAndIsActiveTrue(Long departmentId);

    @EntityGraph(attributePaths = {"department"})
    List<DepartmentHead> findByDepartmentIdAndIsActiveTrueOrderByAssignedAtDesc(Long departmentId);

    @EntityGraph(attributePaths = {"department"})
    List<DepartmentHead> findByUserIdAndIsActiveTrueOrderByAssignedAtDesc(String userId);

    @EntityGraph(attributePaths = {"department"})
    List<DepartmentHead> findByUserNameIgnoreCaseAndIsActiveTrueOrderByAssignedAtDesc(String userName);

    @EntityGraph(attributePaths = {"department"})
    List<DepartmentHead> findByUserEmailIgnoreCaseAndIsActiveTrueOrderByAssignedAtDesc(String userEmail);

    @EntityGraph(attributePaths = {"department"})
    List<DepartmentHead> findByStatusAndIsActiveTrueOrderByAssignedAtDesc(DepartmentHead.HeadStatus status);

    @Query("SELECT dh FROM DepartmentHead dh WHERE dh.status = 'ACTIVE' AND dh.isActive = true ORDER BY dh.assignedAt DESC")
    List<DepartmentHead> findAllActiveDepartmentHeads();

    @Query("SELECT dh FROM DepartmentHead dh WHERE dh.department.company.id = :companyId AND dh.isActive = true ORDER BY dh.assignedAt DESC")
    List<DepartmentHead> findByCompanyId(@Param("companyId") Long companyId);
}


