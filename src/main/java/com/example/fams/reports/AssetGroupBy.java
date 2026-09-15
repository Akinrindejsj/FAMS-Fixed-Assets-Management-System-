package com.example.fams.reports;


public enum AssetGroupBy {
    CATEGORY,
    LOCATION,      // Asset.branch
    DEPARTMENT,
    COST_CENTRE,
    CUSTODIAN,
    STATUS,
    ACQUISITION_MONTH, // Asset.purchaseDate, truncated to yyyy-MM
    SUPPLIER       // Asset.vendor
}
