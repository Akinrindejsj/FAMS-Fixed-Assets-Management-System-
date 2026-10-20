package com.example.fams.reports;

import com.example.fams.assets.Asset;
import com.example.fams.assets.AssetRepository;
import com.example.fams.core.config.SecurityScopeService.ReportScope;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/** Finds the assets a report caller may see, optionally narrowed by category and department. */
@Component
public class ScopedAssetFinder {

    private final AssetRepository assetRepository;

    public ScopedAssetFinder(AssetRepository assetRepository) {
        this.assetRepository = assetRepository;
    }

    /** All statuses, sorted by asset code. Blank filters and the "All" placeholders are ignored. */
    public List<Asset> find(ReportScope scope, String category, String department) {
        List<Asset> assets;
        if (scope == null || scope.isGlobal()) {
            assets = assetRepository.findAll();
        } else if (scope.isEmptyRestriction()) {
            assets = List.of();
        } else if (scope.isDepartment()) {
            assets = assetRepository.findByDepartmentInIgnoreCaseOrderByCreatedAtDesc(
                    scope.departments().stream().map(String::toLowerCase).collect(Collectors.toList()));
        } else {
            assets = assetRepository.findByCustodianInIgnoreCaseOrderByCreatedAtDesc(scope.custodians());
        }
        return assets.stream()
                .filter(a -> isBlank(category) || "All Categories".equalsIgnoreCase(category)
                        || (a.getCategory() != null && a.getCategory().equalsIgnoreCase(category.trim())))
                .filter(a -> isBlank(department) || "Global Operations".equalsIgnoreCase(department)
                        || (a.getDepartment() != null && a.getDepartment().equalsIgnoreCase(department.trim())))
                .sorted(Comparator.comparing((Asset a) -> a.getAssetCode() == null ? "" : a.getAssetCode()))
                .toList();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
