package com.example.fams.assets;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
public class AssetController {

    private final AssetService assetService;

    public AssetController(AssetService assetService) {
        this.assetService = assetService;
    }

    @ModelAttribute("departments")
    public List<String> departments() {
        return List.of("Finance", "Operations", "Information Technology", "Human Resources", "Procurement");
    }

    @ModelAttribute("branches")
    public List<String> branches() {
        return List.of("Lagos HQ", "Abuja Branch", "Port Harcourt Branch", "Ibadan Branch");
    }

    @ModelAttribute("custodians")
    public List<String> custodians() {
        return List.of("Amina Bello", "Chinedu Okafor", "Tola Adeyemi", "Ifeanyi Nwosu", "Warehouse Custody");
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
}
