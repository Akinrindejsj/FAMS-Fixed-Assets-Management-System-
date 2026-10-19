package com.example.fams.reports;

import com.example.fams.assets.Asset;
import com.example.fams.assets.AssetRepository;
import com.example.fams.common.AppClock;
import com.example.fams.depreciation.DepreciationPosting;
import com.example.fams.depreciation.DepreciationService;
import com.example.fams.insurance.AssetCertification;
import com.example.fams.insurance.AssetCertificationRepository;
import com.example.fams.insurance.InsuranceClaim;
import com.example.fams.insurance.InsuranceClaimRepository;
import com.example.fams.insurance.InsuranceCoverage;
import com.example.fams.insurance.InsuranceCoverageRepository;
import com.example.fams.insurance.InsurancePolicy;
import com.example.fams.insurance.InsurancePolicyRepository;
import com.example.fams.reports.TabularReport.Column;
import com.example.fams.settings.AdminSettingsService;
import com.example.fams.settings.AssetCategory;
import com.example.fams.settings.AssetCategoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Insurance and compliance reports: insured and uninsured assets, policies, coverage,
 * expiry, value against cover, underinsurance, certifications and claims.
 *
 * <p>A policy is active between its start and end dates inclusive. Assets are "expected" to
 * be insured or certified when their category is flagged that way on the Insurance &amp;
 * Compliance page. Not department-scoped: callers must be restricted to finance-capable
 * roles and auditors.
 */
@Service
@Transactional(readOnly = true)
public class InsuranceReportService {

    private static final LocalDate MIN_DATE = LocalDate.of(1900, 1, 1);
    private static final LocalDate MAX_DATE = LocalDate.of(2999, 12, 31);
    private static final int EXPIRING_SOON_DAYS = 30;
    private static final int EXPIRING_WITHIN_DAYS = 90;

    private final InsurancePolicyRepository policyRepository;
    private final InsuranceCoverageRepository coverageRepository;
    private final InsuranceClaimRepository claimRepository;
    private final AssetCertificationRepository certificationRepository;
    private final AssetCategoryRepository categoryRepository;
    private final AssetRepository assetRepository;
    private final DepreciationService depreciationService;
    private final AdminSettingsService settingsService;

    public InsuranceReportService(InsurancePolicyRepository policyRepository,
                                  InsuranceCoverageRepository coverageRepository,
                                  InsuranceClaimRepository claimRepository,
                                  AssetCertificationRepository certificationRepository,
                                  AssetCategoryRepository categoryRepository,
                                  AssetRepository assetRepository,
                                  DepreciationService depreciationService,
                                  AdminSettingsService settingsService) {
        this.policyRepository = policyRepository;
        this.coverageRepository = coverageRepository;
        this.claimRepository = claimRepository;
        this.certificationRepository = certificationRepository;
        this.categoryRepository = categoryRepository;
        this.assetRepository = assetRepository;
        this.depreciationService = depreciationService;
        this.settingsService = settingsService;
    }

    // ------------------------------------------------------------------ helpers

    private BigDecimal money(BigDecimal value) {
        return (value == null ? BigDecimal.ZERO : value).setScale(2, RoundingMode.HALF_UP);
    }

    private String nz(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private Map<String, Object> row(Object... keyValues) {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            row.put((String) keyValues[i], keyValues[i + 1]);
        }
        return row;
    }

    private Map<String, Object> summary(Object... keyValues) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put((String) keyValues[i], String.valueOf(keyValues[i + 1]));
        }
        return map;
    }

    private TabularReport table(String title, String description, List<Column> columns, List<Map<String, Object>> rows,
                                Map<String, Object> totals, Map<String, Object> summary, List<String> notes) {
        return new TabularReport(title, description, AppClock.today(), columns, rows,
                totals == null ? Map.<String, Object>of() : totals,
                summary == null ? Map.<String, Object>of() : summary,
                notes == null ? List.<String>of() : notes, rows.isEmpty());
    }

    private Column text(String key, String label) {
        return new Column(key, label, "text");
    }

    private Column date(String key, String label) {
        return new Column(key, label, "date");
    }

    private Column moneyCol(String key, String label) {
        return new Column(key, label, "money");
    }

    private Column number(String key, String label) {
        return new Column(key, label, "number");
    }

    private boolean isActive(InsurancePolicy policy, LocalDate today) {
        return !policy.getStartDate().isAfter(today) && !policy.getEndDate().isBefore(today);
    }

    private String policyStatus(InsurancePolicy policy, LocalDate today) {
        if (policy.getEndDate().isBefore(today)) return "Expired";
        if (policy.getStartDate().isAfter(today)) return "Not started";
        return "Active";
    }

    private String expiryStatus(long days) {
        if (days < 0) return "Expired";
        if (days <= EXPIRING_SOON_DAYS) return "Expires within 30 days";
        if (days <= EXPIRING_WITHIN_DAYS) return "Expires within 90 days";
        return "Valid";
    }

    private boolean live(Asset asset) {
        return !"Disposed".equalsIgnoreCase(asset.getStatus());
    }

    private Set<String> categoriesWhere(java.util.function.Predicate<AssetCategory> flag) {
        return categoryRepository.findAll().stream().filter(flag)
                .map(c -> c.getName().trim().toLowerCase()).collect(Collectors.toSet());
    }

    private boolean inCategories(Asset asset, Set<String> names) {
        return asset.getCategory() != null && names.contains(asset.getCategory().trim().toLowerCase());
    }

    private List<InsuranceCoverage> activeCoverages(LocalDate today) {
        return coverageRepository.findAllByOrderByIdAsc().stream()
                .filter(c -> isActive(c.getPolicy(), today)).toList();
    }

    private Map<Long, BigDecimal> insuredByAsset(List<InsuranceCoverage> coverages) {
        Map<Long, BigDecimal> insured = new HashMap<>();
        for (InsuranceCoverage c : coverages) {
            insured.merge(c.getAsset().getId(), c.getInsuredAmount(), BigDecimal::add);
        }
        return insured;
    }

    /** Value of an asset for insurance comparison, on the configured basis (cost or net book value). */
    private BigDecimal valueOf(Asset asset, Map<Long, DepreciationPosting> postings, boolean nbvBasis) {
        BigDecimal cost = asset.getPurchaseCost() == null ? BigDecimal.ZERO : asset.getPurchaseCost();
        if (!nbvBasis) return cost;
        DepreciationPosting posting = postings.get(asset.getId());
        return posting != null && posting.getBookValue() != null ? posting.getBookValue() : cost;
    }

    private BigDecimal threshold() {
        try {
            return new BigDecimal(settingsService.getParameterValue(AdminSettingsService.INSURANCE_THRESHOLD_KEY, "100"));
        } catch (NumberFormatException ex) {
            return BigDecimal.valueOf(100);
        }
    }

    private boolean nbvBasis() {
        return "NBV".equalsIgnoreCase(settingsService.getParameterValue(AdminSettingsService.INSURANCE_BASIS_KEY, "COST"));
    }

    private String percent(BigDecimal part, BigDecimal whole) {
        if (whole == null || whole.signum() == 0) return "—";
        return part.multiply(BigDecimal.valueOf(100)).divide(whole, 1, RoundingMode.HALF_UP) + "%";
    }

    // ------------------------------------------------------------------ reports

    /** Insured Assets Register: assets with cover in force today. */
    public TabularReport insuredAssets() {
        LocalDate today = AppClock.today();
        List<Map<String, Object>> rows = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        Set<Long> assets = new HashSet<>();
        for (InsuranceCoverage c : activeCoverages(today)) {
            Asset a = c.getAsset();
            InsurancePolicy p = c.getPolicy();
            assets.add(a.getId());
            total = total.add(c.getInsuredAmount());
            rows.add(row("assetCode", a.getAssetCode(), "assetName", a.getName(), "category", nz(a.getCategory()),
                    "policy", p.getPolicyNumber(), "insurer", p.getInsurer(), "coverageType", nz(p.getCoverageType()),
                    "insured", money(c.getInsuredAmount()), "end", p.getEndDate().toString(),
                    "days", ChronoUnit.DAYS.between(today, p.getEndDate())));
        }
        rows.sort(Comparator.comparing(m -> String.valueOf(m.get("assetCode"))));
        List<Column> cols = List.of(text("assetCode", "Asset Code"), text("assetName", "Asset"), text("category", "Category"),
                text("policy", "Policy"), text("insurer", "Insurer"), text("coverageType", "Coverage Type"),
                moneyCol("insured", "Insured Amount"), date("end", "Policy Expires"), number("days", "Days to Expiry"));
        return table("Insured Assets Register", "Assets with insurance cover in force today.", cols, rows,
                rows.isEmpty() ? null : row("assetName", "Total insured", "insured", money(total)),
                summary("Insured assets", assets.size(), "Total insured", money(total)), null);
    }

    /** Uninsured Assets: assets in categories expected to be insured that have no cover in force. */
    public TabularReport uninsuredAssets() {
        LocalDate today = AppClock.today();
        Set<String> required = categoriesWhere(AssetCategory::isInsuranceRequired);
        Set<Long> covered = activeCoverages(today).stream().map(c -> c.getAsset().getId()).collect(Collectors.toSet());
        Map<Long, LocalDate> lastCoverEnd = new HashMap<>();
        for (InsuranceCoverage c : coverageRepository.findAllByOrderByIdAsc()) {
            lastCoverEnd.merge(c.getAsset().getId(), c.getPolicy().getEndDate(), (x, y) -> x.isAfter(y) ? x : y);
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Asset a : assetRepository.findAll()) {
            if (!live(a) || !inCategories(a, required) || covered.contains(a.getId())) continue;
            LocalDate last = lastCoverEnd.get(a.getId());
            rows.add(row("assetCode", a.getAssetCode(), "assetName", a.getName(), "category", nz(a.getCategory()),
                    "department", nz(a.getDepartment()), "status", nz(a.getStatus()),
                    "cost", money(a.getPurchaseCost()), "lastCover", last == null ? "Never insured" : "Expired " + last));
        }
        rows.sort(Comparator.comparing(m -> String.valueOf(m.get("assetCode"))));
        List<String> notes = new ArrayList<>();
        if (required.isEmpty()) {
            notes.add("No categories are marked as requiring insurance. Set this on the Insurance & Compliance page.");
        }
        List<Column> cols = List.of(text("assetCode", "Asset Code"), text("assetName", "Asset"), text("category", "Category"),
                text("department", "Department"), text("status", "Status"), moneyCol("cost", "Cost"),
                text("lastCover", "Last Cover"));
        return table("Uninsured Assets Report", "Assets in categories that should be insured but have no cover in force.",
                cols, rows, null, summary("Uninsured assets", rows.size()), notes);
    }

    /** Insurance Policy Register: every policy with its covered assets, premium and claims. */
    public TabularReport policyRegister() {
        LocalDate today = AppClock.today();
        Map<Long, List<InsuranceCoverage>> byPolicy = coverageRepository.findAllByOrderByIdAsc().stream()
                .collect(Collectors.groupingBy(c -> c.getPolicy().getId()));
        Map<Long, Long> claimsByPolicy = claimRepository.findAllByOrderByIncidentDateDesc().stream()
                .collect(Collectors.groupingBy(c -> c.getPolicy().getId(), Collectors.counting()));
        List<Map<String, Object>> rows = new ArrayList<>();
        BigDecimal premium = BigDecimal.ZERO, insured = BigDecimal.ZERO;
        for (InsurancePolicy p : policyRepository.findAllByOrderByEndDateAsc()) {
            List<InsuranceCoverage> covers = byPolicy.getOrDefault(p.getId(), List.of());
            BigDecimal policyInsured = covers.stream().map(InsuranceCoverage::getInsuredAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            premium = premium.add(p.getPremium() == null ? BigDecimal.ZERO : p.getPremium());
            insured = insured.add(policyInsured);
            rows.add(row("policy", p.getPolicyNumber(), "insurer", p.getInsurer(), "coverageType", nz(p.getCoverageType()),
                    "start", p.getStartDate().toString(), "end", p.getEndDate().toString(),
                    "premium", money(p.getPremium()), "assets", covers.size(), "insured", money(policyInsured),
                    "claims", claimsByPolicy.getOrDefault(p.getId(), 0L), "status", policyStatus(p, today)));
        }
        List<Column> cols = List.of(text("policy", "Policy"), text("insurer", "Insurer"), text("coverageType", "Coverage Type"),
                date("start", "Start"), date("end", "End"), moneyCol("premium", "Premium"), number("assets", "Assets Covered"),
                moneyCol("insured", "Total Insured"), number("claims", "Claims"), text("status", "Status"));
        return table("Insurance Policy Register", "Insurance policies with the assets they cover.", cols, rows,
                rows.isEmpty() ? null : row("insurer", "Totals", "premium", money(premium), "insured", money(insured)),
                summary("Policies", rows.size()), null);
    }

    /** Insurance Coverage by Asset: policy and cover details for each asset that has ever been covered. */
    public TabularReport coverageByAsset() {
        LocalDate today = AppClock.today();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (InsuranceCoverage c : coverageRepository.findAllByOrderByIdAsc()) {
            Asset a = c.getAsset();
            InsurancePolicy p = c.getPolicy();
            rows.add(row("assetCode", a.getAssetCode(), "assetName", a.getName(), "category", nz(a.getCategory()),
                    "policy", p.getPolicyNumber(), "insurer", p.getInsurer(), "coverageType", nz(p.getCoverageType()),
                    "insured", money(c.getInsuredAmount()), "period", p.getStartDate() + " to " + p.getEndDate(),
                    "status", policyStatus(p, today)));
        }
        rows.sort(Comparator.comparing((Map<String, Object> m) -> String.valueOf(m.get("assetCode")))
                .thenComparing(m -> String.valueOf(m.get("period"))));
        List<Column> cols = List.of(text("assetCode", "Asset Code"), text("assetName", "Asset"), text("category", "Category"),
                text("policy", "Policy"), text("insurer", "Insurer"), text("coverageType", "Coverage Type"),
                moneyCol("insured", "Insured Amount"), text("period", "Policy Period"), text("status", "Status"));
        return table("Insurance Coverage by Asset", "Policy and cover details for each insured asset, current and past.",
                cols, rows, null, summary("Coverage records", rows.size()), null);
    }

    /** Insurance Expiry: policies by days to expiry, optionally limited to an expiry-date range. */
    public TabularReport insuranceExpiry(LocalDate from, LocalDate to) {
        LocalDate today = AppClock.today();
        Map<Long, Long> assetsByPolicy = coverageRepository.findAllByOrderByIdAsc().stream()
                .collect(Collectors.groupingBy(c -> c.getPolicy().getId(), Collectors.counting()));
        List<Map<String, Object>> rows = new ArrayList<>();
        for (InsurancePolicy p : policyRepository.findAllByOrderByEndDateAsc()) {
            if (p.getEndDate().isBefore(from == null ? MIN_DATE : from) || p.getEndDate().isAfter(to == null ? MAX_DATE : to)) continue;
            long days = ChronoUnit.DAYS.between(today, p.getEndDate());
            rows.add(row("policy", p.getPolicyNumber(), "insurer", p.getInsurer(), "coverageType", nz(p.getCoverageType()),
                    "end", p.getEndDate().toString(), "days", days, "assets", assetsByPolicy.getOrDefault(p.getId(), 0L),
                    "status", expiryStatus(days)));
        }
        List<Column> cols = List.of(text("policy", "Policy"), text("insurer", "Insurer"), text("coverageType", "Coverage Type"),
                date("end", "Expires"), number("days", "Days to Expiry"), number("assets", "Assets Covered"), text("status", "Status"));
        return table("Insurance Expiry Report", "Policies approaching or past expiry, soonest first.", cols, rows, null,
                summary("Policies", rows.size()), List.of("Negative days mean the policy has already expired."));
    }

    private record ValueRow(Asset asset, BigDecimal value, BigDecimal insured) {
    }

    private List<ValueRow> valueRows() {
        LocalDate today = AppClock.today();
        boolean nbv = nbvBasis();
        List<InsuranceCoverage> active = activeCoverages(today);
        Map<Long, BigDecimal> insured = insuredByAsset(active);
        Map<Long, Asset> assets = new LinkedHashMap<>();
        active.forEach(c -> assets.putIfAbsent(c.getAsset().getId(), c.getAsset()));
        Map<Long, DepreciationPosting> postings = nbv && !assets.isEmpty()
                ? depreciationService.getLatestPostingsForAssets(new ArrayList<>(assets.keySet())) : Map.of();
        List<ValueRow> rows = new ArrayList<>();
        for (Asset a : assets.values()) {
            rows.add(new ValueRow(a, valueOf(a, postings, nbv), insured.get(a.getId())));
        }
        rows.sort(Comparator.comparing(r -> nz(r.asset().getAssetCode())));
        return rows;
    }

    /** Asset Value vs Insurance Coverage: insured amount against value for assets with cover. */
    public TabularReport valueVsCoverage() {
        boolean nbv = nbvBasis();
        List<Map<String, Object>> rows = new ArrayList<>();
        BigDecimal value = BigDecimal.ZERO, insured = BigDecimal.ZERO;
        for (ValueRow r : valueRows()) {
            value = value.add(r.value());
            insured = insured.add(r.insured());
            rows.add(row("assetCode", r.asset().getAssetCode(), "assetName", r.asset().getName(),
                    "category", nz(r.asset().getCategory()), "value", money(r.value()), "insured", money(r.insured()),
                    "difference", money(r.insured().subtract(r.value())), "coverage", percent(r.insured(), r.value())));
        }
        List<Column> cols = List.of(text("assetCode", "Asset Code"), text("assetName", "Asset"), text("category", "Category"),
                moneyCol("value", nbv ? "Net Book Value" : "Original Cost"), moneyCol("insured", "Insured Amount"),
                moneyCol("difference", "Insured − Value"), text("coverage", "Cover %"));
        return table("Asset Value vs Insurance Coverage", "Insured amount compared with asset value for assets with cover in force.",
                cols, rows,
                rows.isEmpty() ? null : row("assetName", "Totals", "value", money(value), "insured", money(insured),
                        "difference", money(insured.subtract(value)), "coverage", percent(insured, value)),
                summary("Valuation basis", nbv ? "Net book value" : "Original cost"), null);
    }

    /** Underinsured Assets: cover in force but below the configured percentage of asset value. */
    public TabularReport underinsured() {
        BigDecimal threshold = threshold();
        boolean nbv = nbvBasis();
        List<Map<String, Object>> rows = new ArrayList<>();
        BigDecimal shortfallTotal = BigDecimal.ZERO;
        for (ValueRow r : valueRows()) {
            if (r.value().signum() == 0) continue;
            BigDecimal required = r.value().multiply(threshold).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            if (r.insured().compareTo(required) >= 0) continue;
            BigDecimal shortfall = required.subtract(r.insured());
            shortfallTotal = shortfallTotal.add(shortfall);
            rows.add(row("assetCode", r.asset().getAssetCode(), "assetName", r.asset().getName(),
                    "category", nz(r.asset().getCategory()), "value", money(r.value()), "insured", money(r.insured()),
                    "required", money(required), "shortfall", money(shortfall), "coverage", percent(r.insured(), r.value())));
        }
        rows.sort(Comparator.comparing((Map<String, Object> m) -> (BigDecimal) m.get("shortfall")).reversed());
        List<Column> cols = List.of(text("assetCode", "Asset Code"), text("assetName", "Asset"), text("category", "Category"),
                moneyCol("value", nbv ? "Net Book Value" : "Original Cost"), moneyCol("insured", "Insured Amount"),
                moneyCol("required", "Required Cover"), moneyCol("shortfall", "Shortfall"), text("coverage", "Cover %"));
        return table("Underinsured Assets Report", "Assets whose cover is below the configured share of their value.", cols, rows,
                rows.isEmpty() ? null : row("assetName", "Total shortfall", "shortfall", money(shortfallTotal)),
                summary("Threshold", threshold.stripTrailingZeros().toPlainString() + "% of "
                        + (nbv ? "net book value" : "original cost"), "Underinsured assets", rows.size()),
                List.of("Change the threshold and valuation basis in Admin settings. Only assets with cover in force are assessed; "
                        + "assets with no cover appear in the Uninsured Assets Report."));
    }

    /**
     * Asset Compliance/Certification: assets expected to hold certification (their category
     * is flagged, or they already have a certification record) with their certification status.
     */
    public TabularReport certificationCompliance() {
        LocalDate today = AppClock.today();
        Set<String> required = categoriesWhere(AssetCategory::isCertificationRequired);
        Map<Long, List<AssetCertification>> certs = certificationRepository.findAllByOrderByExpiryDateAsc().stream()
                .collect(Collectors.groupingBy(c -> c.getAsset().getId()));
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, Integer> statusCounts = new LinkedHashMap<>();
        for (Asset a : assetRepository.findAll()) {
            if (!live(a)) continue;
            List<AssetCertification> own = certs.getOrDefault(a.getId(), List.of());
            boolean categoryRequires = inCategories(a, required);
            if (!categoryRequires && own.isEmpty()) continue;

            List<AssetCertification> valid = own.stream().filter(c -> !c.getExpiryDate().isBefore(today)).toList();
            String status;
            LocalDate relevantExpiry;
            if (own.isEmpty()) {
                status = "Missing";
                relevantExpiry = null;
            } else if (valid.isEmpty()) {
                status = "Expired";
                relevantExpiry = own.stream().map(AssetCertification::getExpiryDate).max(LocalDate::compareTo).orElse(null);
            } else {
                relevantExpiry = valid.stream().map(AssetCertification::getExpiryDate).min(LocalDate::compareTo).orElse(null);
                status = ChronoUnit.DAYS.between(today, relevantExpiry) <= EXPIRING_WITHIN_DAYS ? "Expiring soon" : "Valid";
            }
            statusCounts.merge(status, 1, Integer::sum);
            rows.add(row("assetCode", a.getAssetCode(), "assetName", a.getName(), "category", nz(a.getCategory()),
                    "requirement", categoryRequires ? "Required by category" : "Has certification record",
                    "total", own.size(), "valid", valid.size(), "expiry", relevantExpiry == null ? "" : relevantExpiry.toString(),
                    "status", status));
        }
        rows.sort(Comparator.comparing((Map<String, Object> m) -> String.valueOf(m.get("status")).equals("Missing") ? 0
                : String.valueOf(m.get("status")).equals("Expired") ? 1 : 2).thenComparing(m -> String.valueOf(m.get("assetCode"))));
        List<String> notes = new ArrayList<>();
        notes.add("An asset is treated as compliant when it holds at least one unexpired certification. Required certification types per category are not modelled.");
        if (required.isEmpty()) {
            notes.add("No categories are marked as requiring certification, so only assets with a certification record are listed.");
        }
        List<Column> cols = List.of(text("assetCode", "Asset Code"), text("assetName", "Asset"), text("category", "Category"),
                text("requirement", "Why Listed"), number("total", "Certifications"), number("valid", "Valid"),
                date("expiry", "Next Expiry / Last Expired"), text("status", "Status"));
        Map<String, Object> summary = new LinkedHashMap<>();
        statusCounts.forEach((k, v) -> summary.put(k, String.valueOf(v)));
        return table("Asset Compliance / Certification Report", "Assets requiring certification and whether they hold a valid one.",
                cols, rows, null, summary, notes);
    }

    /** Certification Expiry: each certification by days to expiry, optionally limited to an expiry-date range. */
    public TabularReport certificationExpiry(LocalDate from, LocalDate to) {
        LocalDate today = AppClock.today();
        List<Map<String, Object>> rows = new ArrayList<>();
        for (AssetCertification c : certificationRepository.findAllByOrderByExpiryDateAsc()) {
            if (c.getExpiryDate().isBefore(from == null ? MIN_DATE : from) || c.getExpiryDate().isAfter(to == null ? MAX_DATE : to)) continue;
            long days = ChronoUnit.DAYS.between(today, c.getExpiryDate());
            rows.add(row("assetCode", c.getAsset().getAssetCode(), "assetName", c.getAsset().getName(),
                    "type", c.getCertificationType(), "authority", nz(c.getIssuingAuthority()),
                    "number", nz(c.getCertificateNumber()), "issued", c.getIssueDate().toString(),
                    "expiry", c.getExpiryDate().toString(), "days", days, "status", expiryStatus(days)));
        }
        List<Column> cols = List.of(text("assetCode", "Asset Code"), text("assetName", "Asset"), text("type", "Certification"),
                text("authority", "Authority"), text("number", "Certificate No."), date("issued", "Issued"),
                date("expiry", "Expires"), number("days", "Days to Expiry"), text("status", "Status"));
        return table("Certification Expiry Report", "Certifications approaching or past expiry, soonest first.", cols, rows,
                null, summary("Certifications", rows.size()), List.of("Negative days mean the certification has already expired."));
    }

    /** Insurance Claim History: claims raised, optionally limited to an incident-date range. */
    public TabularReport claimHistory(LocalDate from, LocalDate to) {
        List<Map<String, Object>> rows = new ArrayList<>();
        BigDecimal claimed = BigDecimal.ZERO, settled = BigDecimal.ZERO;
        for (InsuranceClaim c : claimRepository.findAllByOrderByIncidentDateDesc()) {
            if (c.getIncidentDate().isBefore(from == null ? MIN_DATE : from) || c.getIncidentDate().isAfter(to == null ? MAX_DATE : to)) continue;
            claimed = claimed.add(c.getClaimedAmount());
            settled = settled.add(c.getSettledAmount() == null ? BigDecimal.ZERO : c.getSettledAmount());
            rows.add(row("claim", c.getClaimNumber(), "policy", c.getPolicy().getPolicyNumber(), "insurer", c.getPolicy().getInsurer(),
                    "asset", c.getAsset() == null ? "Not asset-specific" : c.getAsset().getAssetCode() + " — " + c.getAsset().getName(),
                    "incident", c.getIncidentDate().toString(), "claimDate", c.getClaimDate().toString(),
                    "status", c.getStatus().name(), "claimed", money(c.getClaimedAmount()),
                    "settled", c.getSettledAmount() == null ? null : money(c.getSettledAmount()), "description", nz(c.getDescription())));
        }
        List<Column> cols = List.of(text("claim", "Claim"), text("policy", "Policy"), text("insurer", "Insurer"),
                text("asset", "Asset"), date("incident", "Incident"), date("claimDate", "Claimed On"), text("status", "Status"),
                moneyCol("claimed", "Claimed"), moneyCol("settled", "Settled"), text("description", "Description"));
        return table("Insurance Claim History", "Claims raised in relation to insured assets.", cols, rows,
                rows.isEmpty() ? null : row("insurer", "Totals", "claimed", money(claimed), "settled", money(settled)),
                summary("Claims", rows.size()), null);
    }
}
