package com.example.fams.depreciation;

public enum DepreciationMethod {
    STRAIGHT_LINE("Straight-Line", "SL"),
    REDUCING_BALANCE("Reducing Balance", "RB"),
    DOUBLE_DECLINING_BALANCE("Double Declining Balance", "DDB");

    private final String displayName;
    private final String abbreviation;

    DepreciationMethod(String displayName, String abbreviation) {
        this.displayName = displayName;
        this.abbreviation = abbreviation;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getAbbreviation() {
        return abbreviation;
    }
}

