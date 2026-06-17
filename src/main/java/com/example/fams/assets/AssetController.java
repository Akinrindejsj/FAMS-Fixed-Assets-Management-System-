package com.example.fams.assets;

import com.example.fams.aau.keycloak.KeycloakAdminService;
import com.example.fams.organization.Branch;
import com.example.fams.organization.BranchRepository;
import com.example.fams.organization.Company;
import com.example.fams.organization.CompanyRepository;
import com.example.fams.organization.Department;
import com.example.fams.organization.DepartmentRepository;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Controller
public class AssetController {

    private final AssetService assetService;
    private final DepartmentRepository departmentRepository;
    private final BranchRepository branchRepository;
    private final CompanyRepository companyRepository;
    private final KeycloakAdminService keycloakAdminService;

    @Value("${keycloak.realm:fams}")
    private String realmName;

    public AssetController(AssetService assetService,
                           DepartmentRepository departmentRepository,
                           BranchRepository branchRepository,
                           CompanyRepository companyRepository,
                           KeycloakAdminService keycloakAdminService) {
        this.assetService = assetService;
        this.departmentRepository = departmentRepository;
        this.branchRepository = branchRepository;
        this.companyRepository = companyRepository;
        this.keycloakAdminService = keycloakAdminService;
    }

    /**
     * Get the current active company or default to the first active company
     */
    private Company getCurrentCompany() {
        List<Company> activeCompanies = companyRepository.findByIsActiveTrueOrderByCreatedAtDesc();
        return activeCompanies.isEmpty() ? null : activeCompanies.get(0);
    }

    @ModelAttribute("departments")
    public List<Department> departments() {
        Company company = getCurrentCompany();
        if (company == null) {
            return new ArrayList<>();
        }
        return departmentRepository.findByCompanyIdAndIsActiveTrueOrderByCreatedAtDesc(company.getId());
    }

    @ModelAttribute("branches")
    public List<Branch> branches() {
        Company company = getCurrentCompany();
        if (company == null) {
            return new ArrayList<>();
        }
        return branchRepository.findByCompanyIdAndIsActiveTrueOrderByCreatedAtDesc(company.getId());
    }

    @ModelAttribute("custodians")
    public List<CustodianDTO> custodians() {
        try {
            // Get all users and find those in the 'employees' group
            List<UserRepresentation> allUsers = keycloakAdminService.listAllUsers(realmName);

            return allUsers.stream()
                    .filter(user -> {
                        try {
                            List<String> userGroups = keycloakAdminService.getUserGroups(realmName, user.getId());
                            return userGroups.stream()
                                    .anyMatch(group -> group.equalsIgnoreCase("employees"));
                        } catch (Exception e) {
                            return false;
                        }
                    })
                    .map(user -> new CustodianDTO(
                            user.getId(),
                            (user.getFirstName() != null ? user.getFirstName() : "") +
                            (user.getLastName() != null ? " " + user.getLastName() : ""),
                            user.getUsername()
                    ))
                    .collect(Collectors.toList());
        } catch (Exception e) {
            // If Keycloak is unavailable, return empty list
            return new ArrayList<>();
        }
    }

    @GetMapping("/assets")
    public String assetsList(Model model) {
        model.addAttribute("assets", assetService.findAll());
        return "assets/assets-list";
    }

    @GetMapping("/assets/register")
    public String registerAsset(Model model) {
        if (!model.containsAttribute("asset")) {
            model.addAttribute("asset", new Asset());
        }
        return "assets/register-assets";
    }

    @PostMapping("/assets")
    public String createAsset(@ModelAttribute Asset asset,
                              @RequestParam(value = "image", required = false) MultipartFile image,
                              RedirectAttributes redirectAttributes) {
        Asset savedAsset = assetService.create(asset, image);
        redirectAttributes.addFlashAttribute("successMessage", savedAsset.getAssetCode() + " was registered successfully.");
        return "redirect:/assets";
    }

    // REST API endpoint for getting all assets as JSON
    @GetMapping("/api/assets")
    @ResponseBody
    public List<Asset> getAssetsJson() {
        return assetService.findAll();
    }
}
