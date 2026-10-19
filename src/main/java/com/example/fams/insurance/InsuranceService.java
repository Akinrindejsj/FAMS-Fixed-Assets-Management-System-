package com.example.fams.insurance;

import com.example.fams.assets.AssetService;
import com.example.fams.settings.AssetCategory;
import com.example.fams.settings.AssetCategoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;

/** Records insurance policies, the assets they cover, claims and asset certifications. */
@Service
public class InsuranceService {

    private final InsurancePolicyRepository policyRepository;
    private final InsuranceCoverageRepository coverageRepository;
    private final InsuranceClaimRepository claimRepository;
    private final AssetCertificationRepository certificationRepository;
    private final AssetCategoryRepository categoryRepository;
    private final AssetService assetService;

    public InsuranceService(InsurancePolicyRepository policyRepository,
                            InsuranceCoverageRepository coverageRepository,
                            InsuranceClaimRepository claimRepository,
                            AssetCertificationRepository certificationRepository,
                            AssetCategoryRepository categoryRepository,
                            AssetService assetService) {
        this.policyRepository = policyRepository;
        this.coverageRepository = coverageRepository;
        this.claimRepository = claimRepository;
        this.certificationRepository = certificationRepository;
        this.categoryRepository = categoryRepository;
        this.assetService = assetService;
    }

    @Transactional(readOnly = true)
    public List<InsurancePolicy> findPolicies() {
        return policyRepository.findAllByOrderByEndDateAsc();
    }

    @Transactional(readOnly = true)
    public List<InsuranceCoverage> findCoverages() {
        return coverageRepository.findAllByOrderByIdAsc();
    }

    @Transactional(readOnly = true)
    public List<InsuranceClaim> findClaims() {
        return claimRepository.findAllByOrderByIncidentDateDesc();
    }

    @Transactional(readOnly = true)
    public List<AssetCertification> findCertifications() {
        return certificationRepository.findAllByOrderByExpiryDateAsc();
    }

    @Transactional(readOnly = true)
    public List<AssetCategory> findCategories() {
        return categoryRepository.findAll().stream()
                .sorted(java.util.Comparator.comparing(AssetCategory::getName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private String required(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " is required.");
        }
        return value.trim();
    }

    private String optional(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    // ---------------------------------------------------------------- policies

    @Transactional
    public InsurancePolicy createPolicy(String policyNumber, String insurer, String coverageType, LocalDate startDate,
                                        LocalDate endDate, BigDecimal premium, String notes) {
        String number = required(policyNumber, "Policy number");
        if (policyRepository.existsByPolicyNumberIgnoreCase(number)) {
            throw new IllegalArgumentException("A policy with number " + number + " already exists.");
        }
        if (startDate == null || endDate == null) {
            throw new IllegalArgumentException("Start and end dates are required.");
        }
        if (endDate.isBefore(startDate)) {
            throw new IllegalArgumentException("The policy end date cannot be before its start date.");
        }
        if (premium != null && premium.signum() < 0) {
            throw new IllegalArgumentException("The premium cannot be negative.");
        }
        InsurancePolicy policy = new InsurancePolicy();
        policy.setPolicyNumber(number);
        policy.setInsurer(required(insurer, "Insurer"));
        policy.setCoverageType(optional(coverageType));
        policy.setStartDate(startDate);
        policy.setEndDate(endDate);
        policy.setPremium(premium);
        policy.setNotes(optional(notes));
        return policyRepository.save(policy);
    }

    @Transactional
    public void deletePolicy(Long id) {
        if (claimRepository.countByPolicyId(id) > 0) {
            throw new IllegalArgumentException("This policy has claims recorded against it and cannot be removed.");
        }
        coverageRepository.deleteByPolicyId(id);
        policyRepository.deleteById(id);
    }

    // ---------------------------------------------------------------- coverage

    @Transactional
    public InsuranceCoverage addCoverage(Long policyId, Long assetId, BigDecimal insuredAmount) {
        if (policyId == null || assetId == null) {
            throw new IllegalArgumentException("Choose a policy and an asset.");
        }
        if (insuredAmount == null || insuredAmount.signum() <= 0) {
            throw new IllegalArgumentException("The insured amount must be greater than zero.");
        }
        if (coverageRepository.existsByPolicyIdAndAssetId(policyId, assetId)) {
            throw new IllegalArgumentException("That asset is already covered by this policy.");
        }
        InsuranceCoverage coverage = new InsuranceCoverage();
        coverage.setPolicy(policyRepository.findById(policyId)
                .orElseThrow(() -> new NoSuchElementException("Policy not found.")));
        coverage.setAsset(assetService.findById(assetId));
        coverage.setInsuredAmount(insuredAmount);
        return coverageRepository.save(coverage);
    }

    @Transactional
    public void deleteCoverage(Long id) {
        coverageRepository.deleteById(id);
    }

    // ------------------------------------------------------------------ claims

    @Transactional
    public InsuranceClaim createClaim(Long policyId, Long assetId, String claimNumber, LocalDate incidentDate,
                                      LocalDate claimDate, String description, BigDecimal claimedAmount,
                                      BigDecimal settledAmount, ClaimStatus status) {
        if (policyId == null) {
            throw new IllegalArgumentException("Choose the policy the claim is made under.");
        }
        if (incidentDate == null || claimDate == null) {
            throw new IllegalArgumentException("Incident and claim dates are required.");
        }
        if (claimDate.isBefore(incidentDate)) {
            throw new IllegalArgumentException("The claim date cannot be before the incident date.");
        }
        if (claimedAmount == null || claimedAmount.signum() <= 0) {
            throw new IllegalArgumentException("The claimed amount must be greater than zero.");
        }
        if (settledAmount != null && settledAmount.signum() < 0) {
            throw new IllegalArgumentException("The settled amount cannot be negative.");
        }
        InsuranceClaim claim = new InsuranceClaim();
        claim.setPolicy(policyRepository.findById(policyId)
                .orElseThrow(() -> new NoSuchElementException("Policy not found.")));
        if (assetId != null) {
            claim.setAsset(assetService.findById(assetId));
        }
        claim.setClaimNumber(required(claimNumber, "Claim number"));
        claim.setIncidentDate(incidentDate);
        claim.setClaimDate(claimDate);
        claim.setDescription(optional(description));
        claim.setClaimedAmount(claimedAmount);
        claim.setSettledAmount(settledAmount);
        claim.setStatus(status == null ? ClaimStatus.SUBMITTED : status);
        return claimRepository.save(claim);
    }

    @Transactional
    public void deleteClaim(Long id) {
        claimRepository.deleteById(id);
    }

    // ---------------------------------------------------------- certifications

    @Transactional
    public AssetCertification createCertification(Long assetId, String certificationType, String issuingAuthority,
                                                  String certificateNumber, LocalDate issueDate, LocalDate expiryDate,
                                                  String notes) {
        if (assetId == null) {
            throw new IllegalArgumentException("Choose an asset.");
        }
        if (issueDate == null || expiryDate == null) {
            throw new IllegalArgumentException("Issue and expiry dates are required.");
        }
        if (expiryDate.isBefore(issueDate)) {
            throw new IllegalArgumentException("The expiry date cannot be before the issue date.");
        }
        AssetCertification certification = new AssetCertification();
        certification.setAsset(assetService.findById(assetId));
        certification.setCertificationType(required(certificationType, "Certification type"));
        certification.setIssuingAuthority(optional(issuingAuthority));
        certification.setCertificateNumber(optional(certificateNumber));
        certification.setIssueDate(issueDate);
        certification.setExpiryDate(expiryDate);
        certification.setNotes(optional(notes));
        return certificationRepository.save(certification);
    }

    @Transactional
    public void deleteCertification(Long id) {
        certificationRepository.deleteById(id);
    }

    // ------------------------------------------------------------ requirements

    /** Marks whether a category's assets are expected to be insured and/or certified. */
    @Transactional
    public void updateCategoryRequirements(Long categoryId, boolean insuranceRequired, boolean certificationRequired) {
        AssetCategory category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new NoSuchElementException("Asset category not found."));
        category.setInsuranceRequired(insuranceRequired);
        category.setCertificationRequired(certificationRequired);
        categoryRepository.save(category);
    }
}
