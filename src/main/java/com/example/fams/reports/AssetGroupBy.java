package com.example.fams.reports;


public enum AssetGroupBy {
    CATEGORY,
    LOCATION,      // Asset.branch
    DEPARTMENT,
    CUSTODIAN,
    STATUS,
    ACQUISITION_MONTH, // Asset.purchaseDate, truncated to yyyy-MM
    SUPPLIER       // Asset.vendor
}
