package com.example.fams.reports;

import java.time.LocalDate;
import java.util.List;

public class AcquisitionTrendData {
    private LocalDate generatedDate = LocalDate.now();
    private TrendGranularity granularity;
    private List<TrendPoint> points;

    public LocalDate getGeneratedDate() {
        return generatedDate;
    }

    public void setGeneratedDate(LocalDate generatedDate) {
        this.generatedDate = generatedDate;
    }

    public TrendGranularity getGranularity() {
        return granularity;
    }

    public void setGranularity(TrendGranularity granularity) {
        this.granularity = granularity;
    }

    public List<TrendPoint> getPoints() {
        return points;
    }

    public void setPoints(List<TrendPoint> points) {
        this.points = points;
    }
}
