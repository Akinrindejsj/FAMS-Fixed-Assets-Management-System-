package com.example.fams.audit;

public class AuditVerificationRequest {

    private Long assetId;
    private String scannedCode;
    private AuditVerificationStatus resultStatus;
    private String actualLocation;
    private String conditionNotes;
    private String actualCustodian;
    private AssetCondition conditionRating;
    private ScanMethod scanMethod;

    public Long getAssetId() {
        return assetId;
    }

    public void setAssetId(Long assetId) {
        this.assetId = assetId;
    }

    public String getScannedCode() {
        return scannedCode;
    }

    public void setScannedCode(String scannedCode) {
        this.scannedCode = scannedCode;
    }

    public AuditVerificationStatus getResultStatus() {
        return resultStatus;
    }

    public void setResultStatus(AuditVerificationStatus resultStatus) {
        this.resultStatus = resultStatus;
    }

    public String getActualLocation() {
        return actualLocation;
    }

    public void setActualLocation(String actualLocation) {
        this.actualLocation = actualLocation;
    }

    public String getConditionNotes() {
        return conditionNotes;
    }

    public void setConditionNotes(String conditionNotes) {
        this.conditionNotes = conditionNotes;
    }

    public String getActualCustodian() {
        return actualCustodian;
    }

    public void setActualCustodian(String actualCustodian) {
        this.actualCustodian = actualCustodian;
    }

    public AssetCondition getConditionRating() {
        return conditionRating;
    }

    public void setConditionRating(AssetCondition conditionRating) {
        this.conditionRating = conditionRating;
    }

    public ScanMethod getScanMethod() {
        return scanMethod;
    }

    public void setScanMethod(ScanMethod scanMethod) {
        this.scanMethod = scanMethod;
    }
}
