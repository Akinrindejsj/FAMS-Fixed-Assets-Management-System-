package com.example.fams.insurance;

import com.example.fams.assets.AssetService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Management page for insurance policies, coverage, claims and certifications. */
@Controller
@RequestMapping("/insurance")
public class InsuranceController {

    private static final Logger log = LoggerFactory.getLogger(InsuranceController.class);

    private final InsuranceService insuranceService;
    private final AssetService assetService;

    public InsuranceController(InsuranceService insuranceService, AssetService assetService) {
        this.insuranceService = insuranceService;
        this.assetService = assetService;
    }

    @GetMapping
    public String page(Model model) {
        model.addAttribute("policies", insuranceService.findPolicies());
        model.addAttribute("coverages", insuranceService.findCoverages());
        model.addAttribute("claims", insuranceService.findClaims());
        model.addAttribute("certifications", insuranceService.findCertifications());
        model.addAttribute("categories", insuranceService.findCategories());
        model.addAttribute("assets", assetService.findAll());
        model.addAttribute("claimStatuses", ClaimStatus.values());
        model.addAttribute("today", LocalDate.now());
        return "insurance/insurance-management";
    }

    private String done(RedirectAttributes redirect, String success, Runnable action) {
        try {
            action.run();
            redirect.addFlashAttribute("successMessage", success);
        } catch (Exception ex) {
            log.warn("Insurance action failed: {}", ex.getMessage());
            redirect.addFlashAttribute("errorMessage", ex.getMessage() == null ? "The action could not be completed." : ex.getMessage());
        }
        return "redirect:/insurance";
    }

    @PostMapping("/policies")
    public String createPolicy(@RequestParam String policyNumber, @RequestParam String insurer,
                               @RequestParam(required = false) String coverageType,
                               @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                               @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
                               @RequestParam(required = false) BigDecimal premium,
                               @RequestParam(required = false) String notes,
                               RedirectAttributes redirect) {
        return done(redirect, "Policy saved.",
                () -> insuranceService.createPolicy(policyNumber, insurer, coverageType, startDate, endDate, premium, notes));
    }

    @PostMapping("/policies/{id}/delete")
    public String deletePolicy(@PathVariable Long id, RedirectAttributes redirect) {
        return done(redirect, "Policy removed.", () -> insuranceService.deletePolicy(id));
    }

    @PostMapping("/coverages")
    public String addCoverage(@RequestParam Long policyId, @RequestParam Long assetId,
                              @RequestParam BigDecimal insuredAmount, RedirectAttributes redirect) {
        return done(redirect, "Coverage added.", () -> insuranceService.addCoverage(policyId, assetId, insuredAmount));
    }

    @PostMapping("/coverages/{id}/delete")
    public String deleteCoverage(@PathVariable Long id, RedirectAttributes redirect) {
        return done(redirect, "Coverage removed.", () -> insuranceService.deleteCoverage(id));
    }

    @PostMapping("/claims")
    public String createClaim(@RequestParam Long policyId, @RequestParam(required = false) Long assetId,
                              @RequestParam String claimNumber,
                              @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate incidentDate,
                              @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate claimDate,
                              @RequestParam(required = false) String description,
                              @RequestParam BigDecimal claimedAmount,
                              @RequestParam(required = false) BigDecimal settledAmount,
                              @RequestParam(required = false) ClaimStatus status,
                              RedirectAttributes redirect) {
        return done(redirect, "Claim recorded.", () -> insuranceService.createClaim(policyId, assetId, claimNumber,
                incidentDate, claimDate, description, claimedAmount, settledAmount, status));
    }

    @PostMapping("/claims/{id}/delete")
    public String deleteClaim(@PathVariable Long id, RedirectAttributes redirect) {
        return done(redirect, "Claim removed.", () -> insuranceService.deleteClaim(id));
    }

    @PostMapping("/certifications")
    public String createCertification(@RequestParam Long assetId, @RequestParam String certificationType,
                                      @RequestParam(required = false) String issuingAuthority,
                                      @RequestParam(required = false) String certificateNumber,
                                      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate issueDate,
                                      @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate expiryDate,
                                      @RequestParam(required = false) String notes,
                                      RedirectAttributes redirect) {
        return done(redirect, "Certification recorded.", () -> insuranceService.createCertification(assetId,
                certificationType, issuingAuthority, certificateNumber, issueDate, expiryDate, notes));
    }

    @PostMapping("/certifications/{id}/delete")
    public String deleteCertification(@PathVariable Long id, RedirectAttributes redirect) {
        return done(redirect, "Certification removed.", () -> insuranceService.deleteCertification(id));
    }

    @PostMapping("/categories/{id}")
    public String updateCategory(@PathVariable Long id,
                                 @RequestParam(defaultValue = "false") boolean insuranceRequired,
                                 @RequestParam(defaultValue = "false") boolean certificationRequired,
                                 RedirectAttributes redirect) {
        return done(redirect, "Category requirements saved.",
                () -> insuranceService.updateCategoryRequirements(id, insuranceRequired, certificationRequired));
    }
}
