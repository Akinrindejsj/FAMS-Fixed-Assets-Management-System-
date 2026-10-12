package com.example.fams.depreciation;

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
public class DepreciationAdjustmentController {

    private final DepreciationAdjustmentService depreciationAdjustmentService;

    public DepreciationAdjustmentController(DepreciationAdjustmentService depreciationAdjustmentService) {
        this.depreciationAdjustmentService = depreciationAdjustmentService;
    }

    @GetMapping("/depreciation/adjustments")
    public String adjustments(Model model) {
        model.addAttribute("adjustments", depreciationAdjustmentService.findRecent());
        model.addAttribute("types", DepreciationAdjustmentType.values());
        return "assets/depreciation-adjustments";
    }

    @PostMapping("/depreciation/adjustments")
    public String createAdjustment(@RequestParam String assetCode,
                                    @RequestParam LocalDate date,
                                    @RequestParam(required = false) String period,
                                    @RequestParam DepreciationAdjustmentType type,
                                    @RequestParam BigDecimal amount,
                                    @RequestParam String reason,
                                    RedirectAttributes redirectAttributes) {
        try {
            String recordedBy = SecurityContextHolder.getContext().getAuthentication() != null
                    ? SecurityContextHolder.getContext().getAuthentication().getName()
                    : "Unknown";
            depreciationAdjustmentService.create(assetCode, date, period, type, amount, reason, recordedBy);
            redirectAttributes.addFlashAttribute("successMessage", "Depreciation adjustment recorded for " + assetCode + ".");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/depreciation/adjustments";
    }
}