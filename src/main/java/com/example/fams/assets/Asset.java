package com.example.fams.assets;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

@Entity
@EntityListeners(com.example.fams.governance.AssetChangeListener.class)
@Table(name = "assets")
public class Asset {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 32)
    private String assetCode;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(nullable = false, length = 120)
    private String category;

    @Column(columnDefinition = "text")
    private String description;

    @Column(length = 120)
    private String serialNumber;

    @Column(length = 120)
    private String manufacturer;

    @Column(length = 120)
    private String model;

    private LocalDate purchaseDate;

    @Column(precision = 19, scale = 2)
    private BigDecimal purchaseCost;

    @Column(length = 160)
    private String vendor;

    private LocalDate warrantyExpiry;

    @Column(nullable = false, length = 120)
    private String department;

    @Column(nullable = false, length = 120)
    private String branch;

    @Column(length = 120)
    private String custodian;

    /**
     * Accounting cost centre — distinct from department (organisational owner).
     * Optional: not every deployment tracks cost centres separately from department,
     * so this is nullable and left unset by existing rows.
     */
    @Column(length = 120)
    private String costCentre;

    /**
     * Whether this asset has been formally capitalised (recognized as a depreciable
     * fixed asset on the books), as distinct from merely having been acquired/purchased.
     * Kept separate from DepreciationParameters.effectiveFromDate (which drives the
     * actual depreciation calculation) so capitalisation can be tracked/reported even
     * for assets that are genuinely non-depreciable once capitalised (e.g. land).
     */
    @Column(nullable = false)
    private boolean capitalised = false;

    private LocalDate capitalisationDate;

    /**
     * Optional link to a parent asset, for component-level tracking (e.g. a laptop's
     * battery or a vehicle's engine tracked as its own asset/cost/depreciation record,
     * rolled up under a parent for reporting). Stored as a plain FK id rather than a
     * JPA relationship, consistent with how Asset's other own attributes (department,
     * branch, custodian) are simple denormalized fields rather than entity relations —
     * real relations in this codebase are reserved for separate tables (see
     * MaintenanceRecord -> Asset).
     */
    private Long parentAssetId;

    /**
     * Not persisted — used only to accept a human-typed asset code on the create form
     * and resolve it to {@link #parentAssetId} in AssetService.create(). Kept off the
     * form's happy path (nullable, optional) since most assets have no parent.
     */
    @jakarta.persistence.Transient
    private String parentAssetCode;

    @Column(nullable = false, length = 40)
    private String status;

    @Column(length = 500)
    private String imageUrl;

    @Column(length = 180)
    private String imagePublicId;

    @Column(length = 32)
    private String barcodeValue;

    @Column(columnDefinition = "text")
    private String barcodeImageDataUri;

    @Column(columnDefinition = "text")
    private String qrCodeImageDataUri;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    /** Field values as loaded, used to work out what changed on update. Not persisted. */
    @Transient
    private Map<String, String> auditSnapshot;

    @PrePersist
    void beforeCreate() {
        LocalDateTime now = LocalDateTime.now();
        createdAt = now;
        updatedAt = now;
        if (status == null || status.isBlank()) {
            status = "In Stock";
        }
    }

    @PreUpdate
    void beforeUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public String getAssetCode() {
        return assetCode;
    }

    public void setAssetCode(String assetCode) {
        this.assetCode = assetCode;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getSerialNumber() {
        return serialNumber;
    }

    public void setSerialNumber(String serialNumber) {
        this.serialNumber = serialNumber;
    }

    public String getManufacturer() {
        return manufacturer;
    }

    public void setManufacturer(String manufacturer) {
        this.manufacturer = manufacturer;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public LocalDate getPurchaseDate() {
        return purchaseDate;
    }

    public void setPurchaseDate(LocalDate purchaseDate) {
        this.purchaseDate = purchaseDate;
    }

    public BigDecimal getPurchaseCost() {
        return purchaseCost;
    }

    public void setPurchaseCost(BigDecimal purchaseCost) {
        this.purchaseCost = purchaseCost;
    }

    public String getVendor() {
        return vendor;
    }

    public void setVendor(String vendor) {
        this.vendor = vendor;
    }

    public LocalDate getWarrantyExpiry() {
        return warrantyExpiry;
    }

    public void setWarrantyExpiry(LocalDate warrantyExpiry) {
        this.warrantyExpiry = warrantyExpiry;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }

    public String getBranch() {
        return branch;
    }

    public void setBranch(String branch) {
        this.branch = branch;
    }

    public String getCustodian() {
        return custodian;
    }

    public void setCustodian(String custodian) {
        this.custodian = custodian;
    }

    public String getCostCentre() {
        return costCentre;
    }

    public void setCostCentre(String costCentre) {
        this.costCentre = costCentre;
    }

    public boolean isCapitalised() {
        return capitalised;
    }

    public void setCapitalised(boolean capitalised) {
        this.capitalised = capitalised;
    }

    public LocalDate getCapitalisationDate() {
        return capitalisationDate;
    }

    public void setCapitalisationDate(LocalDate capitalisationDate) {
        this.capitalisationDate = capitalisationDate;
    }

    public Long getParentAssetId() {
        return parentAssetId;
    }

    public void setParentAssetId(Long parentAssetId) {
        this.parentAssetId = parentAssetId;
    }

    public String getParentAssetCode() {
        return parentAssetCode;
    }

    public void setParentAssetCode(String parentAssetCode) {
        this.parentAssetCode = parentAssetCode;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public void setImageUrl(String imageUrl) {
        this.imageUrl = imageUrl;
    }

    public String getImagePublicId() {
        return imagePublicId;
    }

    public void setImagePublicId(String imagePublicId) {
        this.imagePublicId = imagePublicId;
    }

    public String getBarcodeValue() {
        return barcodeValue;
    }

    public void setBarcodeValue(String barcodeValue) {
        this.barcodeValue = barcodeValue;
    }

    public String getBarcodeImageDataUri() {
        return barcodeImageDataUri;
    }

    public void setBarcodeImageDataUri(String barcodeImageDataUri) {
        this.barcodeImageDataUri = barcodeImageDataUri;
    }

    public String getQrCodeImageDataUri() {
        return qrCodeImageDataUri;
    }

    public void setQrCodeImageDataUri(String qrCodeImageDataUri) {
        this.qrCodeImageDataUri = qrCodeImageDataUri;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    @JsonIgnore
    public Map<String, String> getAuditSnapshot() {
        return auditSnapshot;
    }

    @JsonIgnore
    public void setAuditSnapshot(Map<String, String> auditSnapshot) {
        this.auditSnapshot = auditSnapshot;
    }
}
