package com.example.fams.gl;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@org.springframework.web.bind.annotation.RequestMapping("/gl")
public class GLAccountingController {

    private final GLAccountRepository accountRepository;
    private final GLAccountMappingRepository mappingRepository;
    private final GLAccountingService glAccountingService;
    private final com.example.fams.core.config.AuthenticationManager authenticationManager;

    private static final int JOURNAL_DISPLAY_LIMIT = 200;

    public GLAccountingController(GLAccountRepository accountRepository, GLAccountMappingRepository mappingRepository,
                                   GLAccountingService glAccountingService,
                                   com.example.fams.core.config.AuthenticationManager authenticationManager) {
        this.authenticationManager = authenticationManager;
        this.accountRepository = accountRepository;
        this.mappingRepository = mappingRepository;
        this.glAccountingService = glAccountingService;
    }

    @GetMapping("/accounts")
    public String accounts(Model model) {
        model.addAttribute("accounts", accountRepository.findAllByOrderByAccountCodeAsc());
        model.addAttribute("types", GLAccountType.values());
        return "gl/accounts";
    }

    @PostMapping("/accounts")
    public String createAccount(@RequestParam String accountCode, @RequestParam String accountName,
                                 @RequestParam GLAccountType accountType, RedirectAttributes redirectAttributes) {
        try {
            if (accountRepository.findByAccountCodeIgnoreCase(accountCode).isPresent()) {
                throw new IllegalArgumentException("Account code already exists: " + accountCode);
            }
            GLAccount account = new GLAccount();
            account.setAccountCode(accountCode.trim());
            account.setAccountName(accountName.trim());
            account.setAccountType(accountType);
            accountRepository.save(account);
            redirectAttributes.addFlashAttribute("successMessage", "GL account " + accountCode + " created.");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/gl/accounts";
    }

    @GetMapping("/mappings")
    public String mappings(Model model) {
        model.addAttribute("mappings", mappingRepository.findAllByOrderByCategoryAsc());
        model.addAttribute("accounts", accountRepository.findAllByOrderByAccountCodeAsc());
        return "gl/mappings";
    }

    @PostMapping("/mappings")
    public String saveMapping(@RequestParam(required = false) String category,
                               @RequestParam(required = false) Long assetCostAccountId,
                               @RequestParam(required = false) Long accumulatedDepreciationAccountId,
                               @RequestParam(required = false) Long depreciationExpenseAccountId,
                               @RequestParam(required = false) Long accumulatedImpairmentAccountId,
                               @RequestParam(required = false) Long impairmentLossAccountId,
                               @RequestParam(required = false) Long revaluationSurplusAccountId,
                               @RequestParam(required = false) Long revaluationLossAccountId,
                               @RequestParam(required = false) Long disposalGainAccountId,
                               @RequestParam(required = false) Long disposalLossAccountId,
                               @RequestParam(required = false) Long disposalProceedsAccountId,
                               @RequestParam(required = false) Long cashPayablesAccountId,
                               RedirectAttributes redirectAttributes) {
        try {
            String normalizedCategory = (category == null || category.isBlank()) ? null : category.trim();
            GLAccountMapping mapping = (normalizedCategory == null
                    ? mappingRepository.findByCategoryIsNull()
                    : mappingRepository.findByCategoryIgnoreCase(normalizedCategory))
                    .orElseGet(GLAccountMapping::new);
            mapping.setCategory(normalizedCategory);
            mapping.setAssetCostAccount(lookup(assetCostAccountId));
            mapping.setAccumulatedDepreciationAccount(lookup(accumulatedDepreciationAccountId));
            mapping.setDepreciationExpenseAccount(lookup(depreciationExpenseAccountId));
            mapping.setAccumulatedImpairmentAccount(lookup(accumulatedImpairmentAccountId));
            mapping.setImpairmentLossAccount(lookup(impairmentLossAccountId));
            mapping.setRevaluationSurplusAccount(lookup(revaluationSurplusAccountId));
            mapping.setRevaluationLossAccount(lookup(revaluationLossAccountId));
            mapping.setDisposalGainAccount(lookup(disposalGainAccountId));
            mapping.setDisposalLossAccount(lookup(disposalLossAccountId));
            mapping.setDisposalProceedsAccount(lookup(disposalProceedsAccountId));
            mapping.setCashPayablesAccount(lookup(cashPayablesAccountId));
            mappingRepository.save(mapping);
            redirectAttributes.addFlashAttribute("successMessage",
                    "Mapping saved for " + (normalizedCategory == null ? "global default" : normalizedCategory) + ".");
        } catch (Exception ex) {
            redirectAttributes.addFlashAttribute("errorMessage", "Could not save mapping: " + ex.getMessage());
        }
        return "redirect:/gl/mappings";
    }

    private JournalStatus parseStatus(String status) {
        try {
            return JournalStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown journal status: " + status);
        }
    }

    private GLAccount lookup(Long id) {
        return id == null ? null : accountRepository.findById(id).orElse(null);
    }

    @GetMapping("/journals")
    public String journals(@RequestParam(required = false) String status, Model model) {
        List<JournalEntry> all = status != null && !status.isBlank()
                ? glAccountingService.findByStatus(parseStatus(status))
                : glAccountingService.findAll();
        model.addAttribute("journalTotal", all.size());
        model.addAttribute("journals", all.size() > JOURNAL_DISPLAY_LIMIT ? all.subList(0, JOURNAL_DISPLAY_LIMIT) : all);
        model.addAttribute("canBackfill", authenticationManager.isAdmin());
        model.addAttribute("statuses", JournalStatus.values());
        return "gl/journals";
    }

    @PostMapping("/journals/{id}/post")
    public String postJournal(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            String postedBy = SecurityContextHolder.getContext().getAuthentication() != null
                    ? SecurityContextHolder.getContext().getAuthentication().getName() : "Unknown";
            glAccountingService.post(id, postedBy);
            redirectAttributes.addFlashAttribute("successMessage", "Journal entry posted.");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/gl/journals";
    }

    @PostMapping("/journals/{id}/reject")
    public String rejectJournal(@PathVariable Long id, @RequestParam String reason, RedirectAttributes redirectAttributes) {
        try {
            String rejectedBy = SecurityContextHolder.getContext().getAuthentication() != null
                    ? SecurityContextHolder.getContext().getAuthentication().getName() : "Unknown";
            glAccountingService.reject(id, reason, rejectedBy);
            redirectAttributes.addFlashAttribute("successMessage", "Journal entry rejected.");
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/gl/journals";
    }
}