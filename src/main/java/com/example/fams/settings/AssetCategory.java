package com.example.fams.settings;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(
        name = "asset_categories",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_asset_category_name", columnNames = "name"),
                @UniqueConstraint(name = "uk_asset_category_code", columnNames = "code")
        }
)
public class AssetCategory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, length = 24)
    private String code;

    @Column(length = 500)
    private String description;

    @Column(nullable = false)
    private Integer defaultUsefulLifeYears;

    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal defaultResidualRate;

    @Column(nullable = false, length = 40)
    private String defaultDepreciationMethod;

    @Column(nullable = false, length = 40)
    private String defaultStatus;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    void beforeCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        normalize();
    }

    @PreUpdate
    void beforeUpdate() {
        updatedAt = LocalDateTime.now();
        normalize();
    }

    public void normalize() {
        if (name != null) {
            name = name.trim();
        }
        if (code != null) {
            code = code.trim().toUpperCase();
        }
        if (description != null) {
            description = description.trim();
        }
        if (defaultStatus != null) {
            defaultStatus = defaultStatus.trim();
        }
        if (defaultDepreciationMethod != null) {
            defaultDepreciationMethod = defaultDepreciationMethod.trim().toUpperCase();
        }
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Integer getDefaultUsefulLifeYears() {
        return defaultUsefulLifeYears;
    }

    public void setDefaultUsefulLifeYears(Integer defaultUsefulLifeYears) {
        this.defaultUsefulLifeYears = defaultUsefulLifeYears;
    }

    public BigDecimal getDefaultResidualRate() {
        return defaultResidualRate;
    }

    public void setDefaultResidualRate(BigDecimal defaultResidualRate) {
        this.defaultResidualRate = defaultResidualRate;
    }

    public String getDefaultDepreciationMethod() {
        return defaultDepreciationMethod;
    }

    public void setDefaultDepreciationMethod(String defaultDepreciationMethod) {
        this.defaultDepreciationMethod = defaultDepreciationMethod;
    }

    public String getDefaultStatus() {
        return defaultStatus;
    }

    public void setDefaultStatus(String defaultStatus) {
        this.defaultStatus = defaultStatus;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
