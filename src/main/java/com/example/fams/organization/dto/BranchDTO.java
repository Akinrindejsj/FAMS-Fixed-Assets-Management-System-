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
public class BranchDTO {
    private Long id;
    private Long companyId;
    private String companyName;
    private Long locationId;
    private String locationName;
    private String name;
    private String description;
    private String branchCode;
    private String managerName;
    private String managerPhone;
    private String managerEmail;
    private String status;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

