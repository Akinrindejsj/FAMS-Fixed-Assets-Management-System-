package com.example.fams.reports;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.List;

/**
 * Reads the optional {@code costCentre}, {@code custodian} and {@code status} query values
 * for the asset-based report endpoints and makes them available through {@link ReportFilters}
 * for the length of the request. Other report families use {@code status} for something else
 * (such as journal status), so only these endpoints are affected.
 */
@Component
public class ReportFilterInterceptor implements HandlerInterceptor {

    private static final List<String> ASSET_REPORT_PREFIXES = List.of(
            "/api/reports/management", "/api/reports/forecast", "/api/reports/exceptions", "/api/reports/maintenance");

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String path = request.getRequestURI();
        if (path != null && ASSET_REPORT_PREFIXES.stream().anyMatch(path::startsWith)) {
            ReportFilters.set(new ReportFilters.Values(
                    request.getParameter("costCentre"), request.getParameter("custodian"), request.getParameter("status")));
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        ReportFilters.clear();
    }
}
