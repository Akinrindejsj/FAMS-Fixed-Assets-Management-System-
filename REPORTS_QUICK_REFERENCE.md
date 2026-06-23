# Reports Module - Quick Reference & Implementation Checklist

## Quick Start Guide for Developers

### Adding a New Report Type

#### 1. Add Service Method in `ReportService.java`

```java
/**
 * Generate [Report Name]
 */
@Transactional(readOnly = true)
public ReportData generate[ReportName]Report(String param1, String param2) {
    ReportData report = new ReportData();
    report.setReportType("[report-name]");
    report.setReportName("Report Display Name");
    report.setGeneratedDate(LocalDate.now());
    
    // Your filtering logic
    List<Asset> assets = assetRepository.findAllByOrderByCreatedAtDesc();
    
    // Apply filters...
    
    List<ReportRow> rows = assets.stream()
            .map(this::assetToReportRow)
            .collect(Collectors.toList());
    
    report.setRows(rows);
    calculateTotals(report, rows);
    
    return report;
}
```

#### 2. Add API Endpoint in `ReportController.java`

```java
/**
 * Get [Report Name]
 */
@GetMapping("/[report-name]")
public ResponseEntity<Map<String, Object>> [reportName]Report(
        @RequestParam(required = false) String param1) {
    
    try {
        ReportData report = reportService.generate[ReportName]Report(param1);
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("data", report);
        return ResponseEntity.ok(response);
    } catch (Exception e) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("message", "Error: " + e.getMessage());
        return ResponseEntity.badRequest().body(response);
    }
}
```

#### 3. Add Configuration to HTML JavaScript

```javascript
const reportConfigs = {
    // ... existing configs ...
    '[report-name]': {
        name: '[Report Display Name]',
        description: 'Brief description of the report',
        apiEndpoint: '/api/reports/[report-name]',
        hasFilters: true  // or false
    }
};
```

#### 4. Add Button to Catalogue

```html
<li><button onclick="loadReport('[report-name]')" class="w-full text-left px-3 py-1.5 text-body-default text-secondary rounded-lg hover:bg-surface-canvas transition-colors">[Report Name]</button></li>
```

### Classes Overview

#### ReportRow (Record)
- **Purpose**: Data transfer object for report rows
- **Java 16+ Feature**: Record class (immutable data carrier)
- **Fields**: assetCode, assetName, category, acquisitionCost, netBookValue, status, etc.
- **Usage**: Created from Asset entities, used in ReportData

#### ReportData (Class)
- **Purpose**: Container for complete report information
- **Key Fields**: 
  - `reportType`: Unique identifier (e.g., "asset-register")
  - `reportName`: Display name (e.g., "Asset Register")
  - `rows`: List of ReportRow objects
  - `isEmpty`: Boolean flag for empty state
  - `totalValue`, `totalDepreciation`, `totalNetBookValue`: Aggregated values
  - `totalRecords`: Row count
- **Methods**: Getters and setters for all fields

#### ReportService (Service)
- **Purpose**: Business logic for report generation
- **Scope**: `@Service` - Manages report generation
- **Transactions**: Read-only for performance
- **Key Method**: `calculateTotals()` - Aggregates values
- **Dependency Injection**: AssetRepository, DepreciationService, MaintenanceService

#### ReportController (REST Controller)
- **Purpose**: HTTP API endpoints for reports
- **Mapping**: `/api/reports/*`
- **Response Format**: `{ success: boolean, data: ReportData, message: string }`
- **Error Handling**: Try-catch blocks with meaningful error messages

### JavaScript Functions Reference

```javascript
// Load a report by type
loadReport('asset-register')

// Reload current report with filters
reloadCurrentReport()

// Format value as Nigerian Naira
formatCurrency(1000000) // ₦1,000,000.00

// Get HTML badge for status
getStatusBadge('Assigned') // Returns colored badge HTML

// Show/hide empty state
showEmptyState()

// Export functions
exportToExcel()
exportToPDF()

// Display report data in table
displayReport(reportData)
```

### HTML Elements

#### Key IDs for JavaScript
```
reportLoadingIndicator   - Loading spinner
reportTitle             - Report title heading
reportDescription       - Report description
reportDataContainer     - Main table container
emptyStateContainer     - Empty state UI
reportBody              - Table body for rows
categoryFilter          - Category dropdown
departmentFilter        - Department dropdown
activeOnlyFilter        - Checkbox for active assets
totalValueStat          - Total value display
totalDepreciationStat   - Total depreciation display
totalNetBookValueStat   - Net book value display
reportRecordCount       - Record count display
```

## Implementation Checklist

- [ ] Add `generateXxxReport()` method to `ReportService`
- [ ] Add `@GetMapping` endpoint to `ReportController`
- [ ] Add configuration object to reportConfigs in HTML
- [ ] Add report button to catalogue in HTML
- [ ] Test API endpoint in browser/Postman
- [ ] Test report loads in UI
- [ ] Test filters (if applicable)
- [ ] Test empty state (if no data matches)
- [ ] Test exports
- [ ] Update documentation

## Testing Code Snippets

### Test API with cURL
```bash
# Get Asset Register Report
curl -X GET "http://localhost:8080/api/reports/asset-register?category=IT%20Hardware"

# Get Valuation Report
curl -X GET "http://localhost:8080/api/reports/valuation?department=Finance"
```

### Test with JavaScript Console
```javascript
// Manually load a report
loadReport('asset-register')

// Check report configuration
console.log(reportConfigs)

// Monitor API response
fetch('/api/reports/asset-register')
    .then(r => r.json())
    .then(data => console.log(data))
```

## Common Patterns

### Filter Active Assets Only
```java
assets = assets.stream()
    .filter(a -> "In Stock".equalsIgnoreCase(a.getStatus()) ||
            "Assigned".equalsIgnoreCase(a.getStatus()))
    .collect(Collectors.toList());
```

### Filter by Date Range
```java
LocalDate startDate = LocalDate.of(2024, 1, 1);
LocalDate endDate = LocalDate.of(2024, 12, 31);

assets = assets.stream()
    .filter(a -> a.getPurchaseDate() != null &&
            !a.getPurchaseDate().isBefore(startDate) &&
            !a.getPurchaseDate().isAfter(endDate))
    .collect(Collectors.toList());
```

### Calculate Totals
```java
BigDecimal totalValue = rows.stream()
    .map(ReportRow::acquisitionCost)
    .filter(v -> v != null)
    .reduce(BigDecimal.ZERO, BigDecimal::add);
```

### Format Currency in JavaScript
```javascript
formatCurrency(1234567.89)  // ₦1,234,567.89
formatCurrency(0)           // ₦0.00
formatCurrency(null)        // ₦0.00
```

## Debugging Tips

1. **Check Browser Console** (F12)
   - JavaScript errors appear here
   - Network tab shows API requests/responses

2. **Check Server Logs**
   - Spring Boot logs show service execution
   - Check for exceptions in report generation

3. **Test API Directly**
   - Use Postman or cURL
   - Verify endpoint returns correct JSON structure
   - Check response times for performance issues

4. **Inspect HTML Elements**
   - Use browser DevTools to check element IDs
   - Verify data is binding correctly to table rows
   - Check for CSS class application

## Performance Optimization

### Current Implementation
- Reads: O(n) where n = number of assets
- Filters: Applied in-memory using Streams
- Calculations: Single pass over data

### Future Optimization
```java
// Use database queries instead of in-memory filtering
@Query("SELECT new com.example.fams.reports.ReportRow(...) " +
       "FROM Asset a WHERE a.category = :category")
List<ReportRow> findByCategory(@Param("category") String category);
```

## File Locations

```
src/main/java/com/example/fams/reports/
├── ReportRow.java           (21 lines)
├── ReportData.java          (120 lines)
├── ReportService.java       (350+ lines)
└── ReportController.java    (200+ lines)

src/main/resources/templates/reports/
└── reports-module.html      (508 lines)

Root Documentation:
├── REPORTS_FUNCTIONALITY_GUIDE.md
└── REPORTS_QUICK_REFERENCE.md (this file)
```

## Frequently Asked Questions

**Q: How do I add a new filter option?**
A: Add the filter control in the HTML, capture its value in JavaScript, and pass it as a query parameter to the API.

**Q: Can I combine multiple filters?**
A: Yes! The API supports multiple parameters: `/api/reports/asset-register?category=IT&department=Finance&status=Active`

**Q: How is data formatted in exports?**
A: Currently exports as CSV. Values are properly quoted and escaped. Dates use ISO format (YYYY-MM-DD).

**Q: Can I add real-time report updates?**
A: Yes, you can implement WebSockets or polling. Consider using SseEmitter or WebFlux for real-time updates.

**Q: What's the maximum number of records the page can handle?**
A: Currently tested with 1,000+ records. For very large datasets (100,000+), implement server-side pagination.

## Related Documentation

- See `REPORTS_FUNCTIONALITY_GUIDE.md` for detailed feature documentation
- See `DEPRECIATION_IMPLEMENTATION.md` for depreciation data integration
- Check `COMPANY_STRUCTURE_QUICKSTART.md` for organizational hierarchy

---

**Last Updated**: June 23, 2026
**Quick Reference Version**: 1.0

