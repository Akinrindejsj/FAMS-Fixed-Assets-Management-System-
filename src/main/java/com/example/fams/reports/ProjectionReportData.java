package com.example.fams.reports;

import java.time.LocalDate;
import java.util.List;

public class ProjectionReportData {
    private LocalDate generatedDate = LocalDate.now();
    private int horizonYears;
    private List<ProjectionAssetRow> assets;
    private boolean empty = true;

    public LocalDate getGeneratedDate() {
        return generatedDate;
    }

    public void setGeneratedDate(LocalDate generatedDate) {
        this.generatedDate = generatedDate;
    }

    public int getHorizonYears() {
        return horizonYears;
    }

    public void setHorizonYears(int horizonYears) {
        this.horizonYears = horizonYears;
    }

    public List<ProjectionAssetRow> getAssets() {
        return assets;
    }

    public void setAssets(List<ProjectionAssetRow> assets) {
        this.assets = assets;
        this.empty = assets == null || assets.isEmpty();
    }

    public boolean isEmpty() {
        return empty;
    }

    public void setEmpty(boolean empty) {
        this.empty = empty;
    }
}