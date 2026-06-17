package com.example.fams.organization;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "department_heads", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"user_id", "department_id"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DepartmentHead {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;

    @NotBlank(message = "User ID is required")
    @Column(name = "user_id", nullable = false, length = 100)
    private String userId;  // Keycloak user ID

    @Column(nullable = false, length = 150)
    private String userName;

    @Column(nullable = false, length = 150)
    private String userEmail;

    @Column(length = 150)
    private String fullName;

    @Column(name = "is_primary", nullable = false)
    private Boolean isPrimary = true;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private HeadStatus status = HeadStatus.ACTIVE;

    @Column(nullable = false)
    private LocalDateTime assignedAt;

    @Column
    private LocalDateTime removedAt;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @Column(nullable = false)
    private Boolean isActive = true;

    @PrePersist
    void beforeCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        assignedAt = now;
    }

    @PreUpdate
    void beforeUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public enum HeadStatus {
        ACTIVE, INACTIVE, REMOVED
    }
}

