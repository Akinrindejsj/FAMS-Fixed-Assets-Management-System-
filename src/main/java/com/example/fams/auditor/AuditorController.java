package com.example.fams.auditor;

import com.example.fams.assets.AssetRepository;
import com.example.fams.audit.AuditDiscrepancyReport;
import com.example.fams.audit.AuditHistoryReport;
import com.example.fams.audit.AuditResultView;
import com.example.fams.audit.AuditService;
import com.example.fams.audit.AuditSession;
import com.example.fams.audit.AuditSessionStatus;
import com.example.fams.audit.AuditSessionSummary;
import com.example.fams.audit.AuditVerificationStatus;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

@Controller
public class AuditorController {

    private final AuditService auditService;
    private final AssetRepository assetRepository;

    public AuditorController(AuditService auditService, AssetRepository assetRepository) {
        this.auditService = auditService;
        this.assetRepository = assetRepository;
    }

    @GetMapping("/auditor/dashboard")
    public String auditorDashboard(Model model) {
        List<AuditSessionSummary> sessions = auditService.findSessionSummaries();
        AuditSession activeSession = auditService.findActiveOrLatestSession();
        AuditHistoryReport historyReport = auditService.historyReport(LocalDate.now().minusMonths(3), LocalDate.now());
        long verifiedTotal = sessions.stream().mapToLong(AuditSessionSummary::verifiedCount).sum();
        long discrepancyTotal = auditService.totalDiscrepancyCount();
        long activeAuditCount = sessions.stream().filter(session -> session.status() == AuditSessionStatus.ACTIVE).count();
        long complianceRate = verifiedTotal == 0 ? 100 : Math.max(0, Math.round(((verifiedTotal - discrepancyTotal) * 100.0) / verifiedTotal));

        model.addAttribute("activeAuditCount", activeAuditCount);
        model.addAttribute("discrepancyTotal", discrepancyTotal);
        model.addAttribute("complianceRate", complianceRate);
        model.addAttribute("assetCount", assetRepository.count());
        model.addAttribute("activeSession", activeSession);
        model.addAttribute("sessions", sessions);
        model.addAttribute("recentResults", auditService.recentResults());
        model.addAttribute("historyReport", historyReport);
        model.addAttribute("statusCounts", statusCounts(auditService.recentResults()));
        return "auditor/dashboard";
    }

    @GetMapping({"/auditor/verifications", "/auditor/compliance"})
    public String verifications(@RequestParam(required = false) Long sessionId, Model model) {
        AuditSession activeSession = auditService.findSession(sessionId);
        populateAuditWorkspace(model, activeSession);
        return "auditor/verifications";
    }

    @GetMapping("/auditor/audits/new")
    public String newAuditorAudit() {
        return "redirect:/auditor/verifications";
    }

    @PostMapping("/auditor/audit-sessions")
    public String startSession(@RequestParam String title,
                               @RequestParam(required = false) String scopeLocation,
                               @RequestParam(required = false) String notes,
                               Authentication authentication,
                               RedirectAttributes redirectAttributes) {
        try {
            AuditSession session = auditService.startSession(title, scopeLocation, displayName(authentication), notes);
            redirectAttributes.addFlashAttribute("successMessage", "Audit session started.");
            return "redirect:/auditor/verifications?sessionId=" + session.getId();
        } catch (IllegalArgumentException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
            return "redirect:/auditor/verifications";
        }
    }

    @PostMapping("/auditor/audit-sessions/{sessionId}/complete")
    public String completeSession(@PathVariable Long sessionId, RedirectAttributes redirectAttributes) {
        try {
            auditService.completeSession(sessionId);
            redirectAttributes.addFlashAttribute("successMessage", "Audit session completed.");
        } catch (IllegalArgumentException | NoSuchElementException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/auditor/verifications";
    }

    @GetMapping("/auditor/assets")
    public String assets(Model model) {
        model.addAttribute("assets", assetRepository.findAllByOrderByCreatedAtDesc());
        return "auditor/assets";
    }

    @GetMapping("/auditor/discrepancies")
    public String discrepancies(@RequestParam(required = false) Long sessionId, Model model) {
        AuditSession activeSession = auditService.findSession(sessionId);
        populateAuditWorkspace(model, activeSession);
        return "auditor/discrepancies";
    }

    @GetMapping("/auditor/reports")
    public String reports(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                          @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                          Model model) {
        LocalDate end = to == null ? LocalDate.now() : to;
        LocalDate start = from == null ? end.minusMonths(1) : from;
        model.addAttribute("historyReport", auditService.historyReport(start, end));
        model.addAttribute("from", start);
        model.addAttribute("to", end);
        return "auditor/reports";
    }

    private void populateAuditWorkspace(Model model, AuditSession activeSession) {
        model.addAttribute("sessions", auditService.findSessionSummaries());
        model.addAttribute("activeSession", activeSession);
        model.addAttribute("verificationStatuses", AuditVerificationStatus.values());
        model.addAttribute("defaultAuditTitle", "Physical Asset Audit - " + LocalDate.now().format(DateTimeFormatter.ofPattern("MMM dd, yyyy")));
        model.addAttribute("historyReport", auditService.historyReport(LocalDate.now().minusMonths(1), LocalDate.now()));
        if (activeSession != null) {
            model.addAttribute("results", auditService.resultsForSession(activeSession.getId()));
            model.addAttribute("discrepancyReport", auditService.discrepancyReport(activeSession.getId()));
        } else {
            model.addAttribute("results", List.of());
            model.addAttribute("discrepancyReport", new AuditDiscrepancyReport(null, "No active session", 0, 0, Map.of(), List.of()));
        }
    }

    private Map<AuditVerificationStatus, Long> statusCounts(List<AuditResultView> results) {
        return results.stream().collect(Collectors.groupingBy(AuditResultView::resultStatus, Collectors.counting()));
    }

    private String displayName(Authentication authentication) {
        if (authentication == null || authentication.getName() == null || authentication.getName().isBlank()) {
            return "Auditor";
        }
        return authentication.getName();
    }
}
