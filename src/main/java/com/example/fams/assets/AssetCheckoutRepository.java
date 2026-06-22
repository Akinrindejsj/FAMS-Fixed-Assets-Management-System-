package com.example.fams.assets;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AssetCheckoutRepository extends JpaRepository<AssetCheckout, Long> {

    /**
     * Find all checkouts for a specific asset
     */
    List<AssetCheckout> findByAssetIdOrderByCheckoutDateDesc(Long assetId);

    /**
     * Find currently checked-out assets
     */
    List<AssetCheckout> findByStatusOrderByCheckoutDateDesc(String status);

    /**
     * Find all checkouts by a specific person
     */
    List<AssetCheckout> findByCheckedOutByOrderByCheckoutDateDesc(String checkedOutBy);

    /**
     * Find checkouts that are overdue (due return date is in the past and status is "Checked Out")
     */
    List<AssetCheckout> findByStatusAndDueReturnDateBefore(String status, java.time.LocalDate date);
}

