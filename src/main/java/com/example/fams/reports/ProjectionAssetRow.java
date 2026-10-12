package com.example.fams.reports;

import java.util.List;

/** Section 4, item 15 — one asset's projected future depreciation, per-year. */
public record ProjectionAssetRow(
        String assetCode,
        String assetName,
        String category,
        List<DepreciationProjectionPoint> points
) {
}