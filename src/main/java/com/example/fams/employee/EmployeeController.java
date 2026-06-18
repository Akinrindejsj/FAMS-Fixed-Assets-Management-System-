package com.example.fams.employee;

import com.example.fams.assets.Asset;
import com.example.fams.assets.AssetRepository;
import com.example.fams.assets.AssetService;
import com.example.fams.lifecycle.AssetLifecycleService;
import com.example.fams.lifecycle.AssetLifecycleWorkflowRepository;
import com.example.fams.lifecycle.LifecycleWorkflowForm;
import com.example.fams.lifecycle.LifecycleWorkflowStatus;
import com.example.fams.lifecycle.LifecycleWorkflowType;
import com.example.fams.lifecycle.AssetLifecycleWorkflow;
import com.example.fams.maintenance.MaintenanceRecord;
import com.example.fams.maintenance.MaintenanceRecordRepository;
import com.example.fams.maintenance.MaintenanceService;
import com.example.fams.maintenance.MaintenanceStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;

@Controller
public class EmployeeController {

    private final AssetRepository assetRepository;
    private final AssetService assetService;
    private final MaintenanceService maintenanceService;
    private final MaintenanceRecordRepository maintenanceRecordRepository;
    private final AssetLifecycleService assetLifecycleService;
    private final AssetLifecycleWorkflowRepository workflowRepository;

    public EmployeeController(AssetRepository assetRepository,
                              AssetService assetService,
                              MaintenanceService maintenanceService,
                              MaintenanceRecordRepository maintenanceRecordRepository,
                              AssetLifecycleService assetLifecycleService,
                              AssetLifecycleWorkflowRepository workflowRepository) {
        this.assetRepository = assetRepository;
        this.assetService = assetService;
        this.maintenanceService = maintenanceService;
        this.maintenanceRecordRepository = maintenanceRecordRepository;
        this.assetLifecycleService = assetLifecycleService;
        this.workflowRepository = workflowRepository;
    }

    @GetMapping("/employee/dashboard")
    public String employeeDashboard(Model model) {
        List<Asset> assets = assignedAssets();
        model.addAttribute("assignedAssets", assets);
        model.addAttribute("maintenanceRequests", maintenanceFor(assets));
        model.addAttribute("returnRequests", workflowsFor(assets));
        model.addAttribute("openMaintenanceCount", openMaintenanceCount(assets));
        model.addAttribute("pendingWorkflowCount", pendingWorkflowCount(assets));
        return "employee/dashboard";
    }

    @GetMapping("/employee/assets")
    public String employeeAssets(Model model) {
        model.addAttribute("assignedAssets", assignedAssets());
        return "employee/assets";
    }

    @GetMapping("/employee/assets/{id}")
    public String employeeAssetDetails(@PathVariable Long id, Model model) {
        Asset asset = requireAssignedAsset(id);
        model.addAttribute("asset", asset);
        model.addAttribute("maintenanceRequests", maintenanceRecordRepository.findByAssetOrderByMaintenanceDateDescCreatedAtDesc(asset));
        model.addAttribute("returnRequests", workflowRepository.findByAssetOrderByRequestedAtDesc(asset));
        model.addAttribute("timeline", assetLifecycleService.timeline(asset));
        return "employee/asset-details";
    }

    @GetMapping("/employee/maintenance-requests")
    public String maintenanceRequests(Model model) {
        List<Asset> assets = assignedAssets();
        model.addAttribute("assignedAssets", assets);
        model.addAttribute("maintenanceRequests", maintenanceFor(assets));
        return "employee/maintenance-requests";
    }

    @PostMapping("/employee/maintenance-requests")
    public String submitMaintenanceRequest(@RequestParam Long assetId,
                                           @RequestParam String issueDescription,
                                           @RequestParam(required = false) String serviceProvider,
                                           RedirectAttributes redirectAttributes) {
        try {
            Asset asset = requireAssignedAsset(assetId);
            maintenanceService.recordCorrective(asset.getId(), issueDescription, serviceProvider, null, null, currentDisplayName());
            redirectAttributes.addFlashAttribute("successMessage", "Maintenance request submitted and routed to asset management.");
        } catch (IllegalArgumentException | NoSuchElementException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/employee/maintenance-requests";
    }

    @GetMapping("/employee/return-requests")
    public String returnRequests(Model model) {
        List<Asset> assets = assignedAssets();
        model.addAttribute("assignedAssets", assets);
        model.addAttribute("returnRequests", workflowsFor(assets));
        model.addAttribute("tomorrow", LocalDate.now().plusDays(1));
        return "employee/return-requests";
    }

    @PostMapping("/employee/return-requests")
    public String submitReturnRequest(@RequestParam Long assetId,
                                      @RequestParam LocalDate requestedEffectiveDate,
                                      @RequestParam(required = false) String reason,
                                      RedirectAttributes redirectAttributes) {
        try {
            Asset asset = requireAssignedAsset(assetId);
            LifecycleWorkflowForm form = new LifecycleWorkflowForm();
            form.setAssetId(asset.getId());
            form.setType(LifecycleWorkflowType.RETURN);
            form.setRequestedEffectiveDate(requestedEffectiveDate);
            form.setToEmployee("Asset Store");
            form.setToDepartment(asset.getDepartment());
            form.setToBranch(asset.getBranch());
            form.setReason(reason);
            assetLifecycleService.submit(form);
            redirectAttributes.addFlashAttribute("successMessage", "Return request submitted for approval.");
        } catch (IllegalArgumentException | NoSuchElementException ex) {
            redirectAttributes.addFlashAttribute("errorMessage", ex.getMessage());
        }
        return "redirect:/employee/return-requests";
    }

    private List<Asset> assignedAssets() {
        return assetRepository.findByCustodianIgnoreCaseOrCustodianIgnoreCaseOrderByCreatedAtDesc(currentUsername(), currentDisplayName());
    }

    private Asset requireAssignedAsset(Long id) {
        Asset asset = assetService.findById(id);
        String custodian = asset.getCustodian();
        if (custodian != null && (custodian.equalsIgnoreCase(currentUsername()) || custodian.equalsIgnoreCase(currentDisplayName()))) {
            return asset;
        }
        throw new NoSuchElementException("Asset is not assigned to the current employee.");
    }

    private List<MaintenanceRecord> maintenanceFor(List<Asset> assets) {
        if (assets.isEmpty()) {
            return List.of();
        }
        return maintenanceRecordRepository.findByAssetInOrderByMaintenanceDateDescCreatedAtDesc(assets);
    }

    private List<AssetLifecycleWorkflow> workflowsFor(List<Asset> assets) {
        if (assets.isEmpty()) {
            return List.of();
        }
        return workflowRepository.findByAssetInOrderByRequestedAtDesc(assets);
    }

    private long openMaintenanceCount(List<Asset> assets) {
        if (assets.isEmpty()) {
            return 0;
        }
        return maintenanceRecordRepository.countByAssetInAndStatus(assets, MaintenanceStatus.OPEN);
    }

    private long pendingWorkflowCount(List<Asset> assets) {
        if (assets.isEmpty()) {
            return 0;
        }
        return workflowRepository.countByAssetInAndStatus(assets, LifecycleWorkflowStatus.PENDING_APPROVAL);
    }

    private String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            return "guest";
        }
        return auth.getName();
    }

    private String currentDisplayName() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return "Guest";
        }
        Object principal = auth.getPrincipal();
        if (principal instanceof DefaultOidcUser oidc) {
            Object name = oidc.getClaims().get("name");
            if (name instanceof String value && !value.isBlank()) {
                return value;
            }
            Object username = oidc.getClaims().get("preferred_username");
            if (username instanceof String value && !value.isBlank()) {
                return value;
            }
        }
        return currentUsername();
    }
}
