package com.example.fams.depreciation;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Collection;
import java.util.List;

public interface DepreciationPostingRepository extends JpaRepository<DepreciationPosting, Long> {

    
    // Latest posting per asset (one row per asset id) for a batch of assets
    @Query(value = "SELECT DISTINCT ON (dp.asset_id) dp.* FROM depreciation_postings dp " +
            "WHERE dp.asset_id IN :assetIds ORDER BY dp.asset_id, dp.depreciation_period DESC",
            nativeQuery = true)
    List<DepreciationPosting> findLatestByAssetIdIn(@Param("assetIds") Collection<Long> assetIds);

    // Find all postings for a specific asset
    List<DepreciationPosting> findByAssetIdOrderByDepreciationPeriodDesc(Long assetId);

    // Find postings for a specific period
    List<DepreciationPosting> findByDepreciationPeriodOrderByAssetCode(String depreciationPeriod);

    // Find postings for a category
    List<DepreciationPosting> findByCategoryOrderByDepreciationPeriodDescAssetCode(String category);

    // Find postings for a department
    List<DepreciationPosting> findByDepartmentOrderByDepreciationPeriodDescAssetCode(String department);

    // Find paginated postings for a period
    Page<DepreciationPosting> findByDepreciationPeriod(String depreciationPeriod, Pageable pageable);

    // Find latest posting for an asset
    DepreciationPosting findFirstByAssetIdOrderByDepreciationPeriodDesc(Long assetId);

    // Find postings for a specific asset + period (used to replace on re-run)
    List<DepreciationPosting> findByAssetIdAndDepreciationPeriod(Long assetId, String depreciationPeriod);

    // Find postings by fiscal year
    List<DepreciationPosting> findByFiscalYearOrderByDepreciationPeriodDescAssetCode(Integer fiscalYear);
}