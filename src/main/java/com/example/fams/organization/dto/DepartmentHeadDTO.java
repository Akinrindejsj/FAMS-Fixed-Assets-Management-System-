package com.example.fams.organization.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DepartmentHeadDTO {
    private Long id;
    private Long departmentId;
    private String departmentName;
    private String branchName;
    private String companyName;
    private String userId;
    private String userName;
    private String userEmail;
    private String fullName;
    private Boolean isPrimary;
    private String status;
    private Boolean isActive;
    private LocalDateTime assignedAt;
    private LocalDateTime removedAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

