package com.example.fams.assets;

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
public class CapitalAdditionController {

    private final CapitalAdditionService capitalAdditionService;

    public CapitalAdditionController(CapitalAdditionService capitalAdditionService) {
        this.capitalAdditionService = capitalAdditionService;
    }

    @GetMapping("/assets/capital-additions")
    public String capitalAdditions(Model model) {
        model.addAttribute("additions", capitalAdditionService.findRecent());
        model.addAttribute("types", CapitalAdditionType.values());
        return "assets/capital-additions";
    }

    @PostMapping("/assets/capital-additions")
    public String createCapitalAddition(@RequestParam String assetCode,
                                         @RequestParam LocalDate date,
                                         @RequestParam BigDecimal amount,
                                         @RequestParam CapitalAdditionType type,
                                         @RequestParam(required = false) String description,
                                         RedirectAttributes redirectAttributes) {
        try {
            String recordedBy = SecurityContextHolder.getContext().getAuthentication() != null
                    ? SecurityContextHolder.getContext().getAuthentication().getName()
                    : "Unknown";
            capitalAdditionService.create(assetCode, date, amount, type, description, recordedBy);
            redirectAttributes.addFlashAttribute("successMessage", "Capital addition recorded for " + assetCode + ".");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/assets/capital-additions";
    }
}
