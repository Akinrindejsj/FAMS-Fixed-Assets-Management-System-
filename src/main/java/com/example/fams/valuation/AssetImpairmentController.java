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
public class AssetImpairmentController {

    private final AssetImpairmentService assetImpairmentService;

    public AssetImpairmentController(AssetImpairmentService assetImpairmentService) {
        this.assetImpairmentService = assetImpairmentService;
    }

    @GetMapping("/assets/impairments")
    public String impairments(Model model) {
        model.addAttribute("impairments", assetImpairmentService.findRecent());
        model.addAttribute("types", ImpairmentType.values());
        return "assets/impairments";
    }

    @PostMapping("/assets/impairments")
    public String createImpairment(@RequestParam String assetCode,
                                    @RequestParam LocalDate date,
                                    @RequestParam ImpairmentType type,
                                    @RequestParam BigDecimal amount,
                                    @RequestParam String reason,
                                    RedirectAttributes redirectAttributes) {
        try {
            String recordedBy = SecurityContextHolder.getContext().getAuthentication() != null
                    ? SecurityContextHolder.getContext().getAuthentication().getName()
                    : "Unknown";
            assetImpairmentService.create(assetCode, date, type, amount, reason, recordedBy);
            redirectAttributes.addFlashAttribute("successMessage", "Impairment recorded for " + assetCode + ".");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/assets/impairments";
    }
}