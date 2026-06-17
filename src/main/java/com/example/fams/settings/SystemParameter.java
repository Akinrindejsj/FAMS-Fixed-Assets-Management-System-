package com.example.fams.settings;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "system_parameters")
public class SystemParameter {

    @Id
    @Column(length = 80)
    private String keyName;

    @Column(nullable = false, length = 120)
    private String label;

    @Column(nullable = false, length = 500)
    private String value;

    @Column(length = 500)
    private String description;

    @Column(nullable = false, length = 30)
    private String type;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    @PreUpdate
    void beforeSave() {
        updatedAt = LocalDateTime.now();
        if (keyName != null) {
            keyName = keyName.trim();
        }
        if (value != null) {
            value = value.trim();
        }
    }

    public String getKeyName() {
        return keyName;
    }

    public void setKeyName(String keyName) {
        this.keyName = keyName;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
