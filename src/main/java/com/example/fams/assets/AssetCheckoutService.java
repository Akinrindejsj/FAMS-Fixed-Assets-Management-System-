package com.example.fams.assets;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;

@Service
public class AssetCheckoutService {

    private final AssetCheckoutRepository checkoutRepository;
    private final AssetRepository assetRepository;

    public AssetCheckoutService(AssetCheckoutRepository checkoutRepository,
                                AssetRepository assetRepository) {
        this.checkoutRepository = checkoutRepository;
        this.assetRepository = assetRepository;
    }

    /**
     * Create a new asset checkout
     */
    @Transactional
    public AssetCheckout checkout(Long assetId, String checkedOutBy, LocalDate checkoutDate,
                                  LocalDate dueReturnDate, String purpose, String conditionBeforeCheckout) {
        Asset asset = assetRepository.findById(assetId)
                .orElseThrow(() -> new NoSuchElementException("Asset not found with id: " + assetId));

        if (!asset.getStatus().equals("In Stock") && !asset.getStatus().equals("Available") && !asset.getStatus().equals("Returned")) {
            throw new IllegalArgumentException("Asset is not available for checkout. Current status: " + asset.getStatus());
        }

        if (dueReturnDate.isBefore(checkoutDate)) {
            throw new IllegalArgumentException("Due return date cannot be before checkout date");
        }

        AssetCheckout checkout = new AssetCheckout();
        checkout.setAsset(asset);
        checkout.setCheckedOutBy(checkedOutBy);
        checkout.setCheckoutDate(checkoutDate);
        checkout.setDueReturnDate(dueReturnDate);
        checkout.setPurpose(purpose);
        checkout.setConditionBeforeCheckout(conditionBeforeCheckout);
        checkout.setStatus("Checked Out");

        AssetCheckout saved = checkoutRepository.save(checkout);

        // Update asset status
        asset.setStatus("Checked Out");
        assetRepository.save(asset);

        return saved;
    }

    /**
     * Return a checked-out asset
     */
    @Transactional
    public AssetCheckout returnCheckout(Long checkoutId, String conditionAfterReturn, String returnNotes) {
        AssetCheckout checkout = checkoutRepository.findById(checkoutId)
                .orElseThrow(() -> new NoSuchElementException("Checkout record not found with id: " + checkoutId));

        if (!checkout.getStatus().equals("Checked Out")) {
            throw new IllegalArgumentException("Cannot return an asset that is not checked out");
        }

        checkout.setReturnDate(LocalDate.now());
        checkout.setConditionAfterReturn(conditionAfterReturn);
        checkout.setReturnNotes(returnNotes);
        checkout.setStatus("Returned");

        AssetCheckout saved = checkoutRepository.save(checkout);

        // Update asset status to Returned
        Asset asset = checkout.getAsset();
        asset.setStatus("Returned");
        assetRepository.save(asset);

        return saved;
    }

    /**
     * Verify a returned asset checkout
     */
    @Transactional
    public AssetCheckout verifyReturn(Long checkoutId, String verifiedBy, String notes) {
        AssetCheckout checkout = checkoutRepository.findById(checkoutId)
                .orElseThrow(() -> new NoSuchElementException("Checkout record not found with id: " + checkoutId));

        if (!checkout.getStatus().equals("Returned")) {
            throw new IllegalArgumentException("Only returned assets can be verified");
        }

        checkout.setVerifiedBy(verifiedBy);
        checkout.setVerifiedAt(LocalDateTime.now());
        checkout.setStatus("Verified");

        if (notes != null && !notes.isBlank()) {
            checkout.setReturnNotes(notes);
        }

        AssetCheckout saved = checkoutRepository.save(checkout);

        // Update asset status to Available/In Stock
        Asset asset = checkout.getAsset();
        asset.setStatus("Available");
        assetRepository.save(asset);

        return saved;
    }

    /**
     * Get all checkouts for an asset
     */
    public List<AssetCheckout> getCheckoutsForAsset(Long assetId) {
        return checkoutRepository.findByAssetIdOrderByCheckoutDateDesc(assetId);
    }

    /**
     * Get all currently checked-out assets
     */
    public List<AssetCheckout> getActiveCheckouts() {
        return checkoutRepository.findByStatusOrderByCheckoutDateDesc("Checked Out");
    }

    /**
     * Get all checkouts by a specific person
     */
    public List<AssetCheckout> getCheckoutsByPerson(String person) {
        return checkoutRepository.findByCheckedOutByOrderByCheckoutDateDesc(person);
    }

    /**
     * Get overdue checkouts
     */
    public List<AssetCheckout> getOverdueCheckouts() {
        return checkoutRepository.findByStatusAndDueReturnDateBefore("Checked Out", LocalDate.now());
    }

    /**
     * Get a specific checkout record
     */
    public AssetCheckout getCheckout(Long checkoutId) {
        return checkoutRepository.findById(checkoutId)
                .orElseThrow(() -> new NoSuchElementException("Checkout record not found with id: " + checkoutId));
    }

    /**
     * Get all checkouts
     */
    public List<AssetCheckout> getAllCheckouts() {
        return checkoutRepository.findAll();
    }
}

