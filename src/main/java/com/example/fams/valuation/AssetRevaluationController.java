package com.example.fams.valuation;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;

@Controller
public class AssetRevaluationController {

    private final AssetRevaluationService assetRevaluationService;

    public AssetRevaluationController(AssetRevaluationService assetRevaluationService) {
        this.assetRevaluationService = assetRevaluationService;
    }

    @GetMapping("/assets/revaluations")
    public String revaluations(Model model) {
        model.addAttribute("revaluations", assetRevaluationService.findRecent());
        return "assets/revaluations";
    }

    @PostMapping("/assets/revaluations")
    public String createRevaluation(@RequestParam String assetCode,
                                     @RequestParam LocalDate date,
                                     @RequestParam BigDecimal revaluedAmount,
                                     @RequestParam String reason,
                                     RedirectAttributes redirectAttributes) {
        try {
            String recordedBy = SecurityContextHolder.getContext().getAuthentication() != null
                    ? SecurityContextHolder.getContext().getAuthentication().getName()
                    : "Unknown";
            assetRevaluationService.create(assetCode, date, revaluedAmount, reason, recordedBy);
            redirectAttributes.addFlashAttribute("successMessage", "Revaluation recorded for " + assetCode + ".");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/assets/revaluations";
    }
}