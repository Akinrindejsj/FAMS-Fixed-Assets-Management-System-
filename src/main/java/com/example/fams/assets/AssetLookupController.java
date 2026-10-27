package com.example.fams.assets;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

/** Opens an asset's detail page from its asset code, so report rows can link to the asset. */
@Controller
public class AssetLookupController {

    private final AssetRepository assetRepository;

    public AssetLookupController(AssetRepository assetRepository) {
        this.assetRepository = assetRepository;
    }

    @GetMapping("/assets/lookup")
    public String lookup(@RequestParam String code, RedirectAttributes redirectAttributes) {
        return assetRepository.findByAssetCodeIgnoreCase(code.trim())
                .map(asset -> "redirect:/assets/" + asset.getId())
                .orElseGet(() -> {
                    redirectAttributes.addFlashAttribute("errorMessage", "No asset was found with code " + code.trim() + ".");
                    return "redirect:/assets";
                });
    }
}
