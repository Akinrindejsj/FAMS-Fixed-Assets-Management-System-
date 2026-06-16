package com.example.fams.depreciation;

import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface DepreciationParametersRepository extends JpaRepository<DepreciationParameters, Long> {

    // Find parameters for a specific asset
    Optional<DepreciationParameters> findByAssetIdAndIsActiveTrue(Long assetId);

    // Find parameters for a category
    List<DepreciationParameters> findByCategoryAndIsActiveTrueAndAssetIdIsNull(String category);

    // Find all active parameters that apply to a given date
    List<DepreciationParameters> findByIsActiveTrueAndEffectiveFromDateLessThanEqualAndEffectiveToDateIsNullOrEffectiveToDateGreaterThanEqual(
            LocalDate date1, LocalDate date2);

    // Find active parameters for an asset as of a specific date
    Optional<DepreciationParameters> findByAssetIdAndEffectiveFromDateLessThanEqualAndIsActiveTrue(Long assetId, LocalDate date);

}

