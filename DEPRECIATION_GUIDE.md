# Asset Depreciation Module - User Guide

## Overview

The Asset Depreciation Module in FAMS provides comprehensive depreciation calculation and reporting capabilities that comply with international accounting standards (IFRS, US GAAP). The system automatically calculates depreciation, prevents over-depreciation, and maintains complete audit trails.

## Key Features

### ✅ Three Depreciation Methods Supported

1. **Straight-Line (SL)** - Equal depreciation each year
   - Formula: (Cost - Residual Value) / Useful Life
   - Best for: Most standard assets, buildings, furniture
   - Example: $10,000 asset, 5-year life, no residual = $2,000/year

2. **Reducing Balance (RB)** - Higher depreciation early years
   - Rate: 1 / Useful Life applied to declining book value
   - Best for: Vehicles, machinery that lose value faster
   - Example: Faster depreciation in year 1, slower in year 5

3. **Double Declining Balance (DDB)** - Accelerated depreciation
   - Rate: 2 x Straight-Line Rate applied to declining book value
   - Best for: Technology, items with rapid obsolescence
   - Example: Most depreciation in first 2-3 years

### ✅ Core Functionality

- **Automatic Residual Value Protection** - System prevents depreciation below configured residual value
- **Future-Dated Changes** - Update depreciation parameters with effective future dates
- **Complete Audit Trail** - Every depreciation posting recorded with method and amount used
- **Category-Wide or Asset-Specific Configuration** - Set parameters at any level
- **Multi-Level Reporting** - Views by asset, category, and department
- **Period Close Process** - Monthly, quarterly, or annual depreciation runs

## Getting Started

### Step 1: Configure Depreciation Parameters

**Where**: Settings → Asset Management → Depreciation Configuration

**For Individual Assets:**
1. Navigate to `Depreciation → Configure`
2. Select "Asset-Specific" configuration
3. Choose the asset to configure
4. Select depreciation method
5. Enter useful life in years (e.g., 5 for IT equipment)
6. Set residual/scrap value (if any)
7. Choose effective date (defaults to today)
8. Click "Save Configuration"

**For Asset Categories:**
1. Navigate to `Depreciation → Configure`
2. Select "Category-Wide" configuration
3. Choose asset category
4. Configure method, useful life, and residual value
5. Click "Save Configuration"

### Step 2: Close Depreciation Period

**Where**: Depreciation → Run Depreciation

1. Select period type: Monthly, Quarterly, or Annual
2. Choose year and applicable period
3. Confirm period end date auto-populates correctly
4. Review the summary preview
5. Check confirmation box acknowledging:
   - Depreciation parameters are configured
   - Period end date is correct
   - This is the first run for this period
6. Click "Run Depreciation"
7. System processes all configured assets
8. Review results when complete

### Step 3: View Depreciation Reports

**Period Report** (Depreciation Dashboard):
- Shows all assets in portfolio
- Displays original cost, accumulated depreciation, and book value
- Filters by category, department, and method
- Provides KPIs: Total assets, fully depreciated count, etc.

**Asset-Specific History** (Depreciation → History):
1. Select the asset from dropdown
2. View complete financial history
3. Click any row to see detailed posting information
4. Export history to CSV for records

### Configuration Best Practices

#### 1. Useful Life Settings
```
IT Equipment:          3-5 years
Vehicles:             5-7 years
Furniture:            5-10 years
Machinery:            10-15 years
Buildings:            20-50 years
Office Equipment:     3-5 years
```

#### 2. Residual Value
- Set to realistic scrap/salvage value
- Usually 5-20% of original cost (except buildings)
- Zero if no salvage value expected
- Important for accurate liability calculations

#### 3. Method Selection
- **Straight-Line**: Default, most conservative, easiest to explain
- **Reducing Balance**: For items losing value quickly early
- **DDB**: For tech with rapid obsolescence, most aggressive

#### 4. Effective Dating Changes
- To change parameters effective future date:
  1. Create new configuration with future effective date
  2. System auto-closes previous configuration
  3. New methods apply only from effective date forward
  4. Complete history remains intact

## Financial Reporting

### Depreciation Report Overview

The system generates comprehensive reports showing:

**For Each Period:**
- Asset-by-asset detail (Code, Name, Category, Method, Costs, Values)
- Category summary (total costs, accumulated depreciation)
- Department summary (allocation across organizational units)
- Fully depreciated asset count
- Recovery potential percentage

### Report Components

```
Total Original Cost:              ₦X,XXX,XXX.00
Accumulated Depreciation:         ₦X,XXX,XXX.00
Total Book Value:                 ₦X,XXX,XXX.00
Depreciation Charge (This Period): ₦XXX,XXX.00

Average Recovery Rate:            65.3%
Fully Depreciated Assets:         12 of 150
```

### Export & Integration

- Export depreciation reports to CSV
- Export asset history for audit purposes
- Reports support rounding tolerance per accounting standards
- Suitable for financial statements, tax reporting, audit trails

## Error Handling & Validation

### Common Error Messages & Solutions

**"Asset not found in depreciation parameters"**
- Solution: Configure depreciation parameters for the asset first
- Navigate to Configure → Asset-Specific → Select asset → Save

**"Cannot depreciate asset below residual value"**
- This is OK - prevents over-depreciation
- Asset is marked as "Fully Depreciated"
- No more charges for future periods

**"Period already processed"**
- Solution: Cannot re-run same period
- To recalculate: Archive old entries and run new period
- Contact system admin if correction needed

**"No depreciation postings found"**
- Asset may not have parameters configured
- Check: Depreciation → History for details
- Verify asset status is not "Disposed"

### Validation Rules

- Useful life must be ≥ 1 year
- Residual value must be ≤ Original cost
- Period end date cannot be in future
- Same period cannot be run twice
- Assets must have original cost recorded

## Technical Details

### Calculation Precision

- All calculations use 2 decimal places
- Rounding: HALF_UP (standard accounting)
- Tolerance: ±0.01 currency units
- Suitable for financial audit

### Data Integrity

- All depreciation postings are immutable
- Complete audit trail maintained
- Each posting includes:
  - Period identifier
  - Depreciation method used
  - All financial values
  - Fully depreciated flag
  - Timestamp

### Performance

- Batch depreciation runs optimize for portfolio size
- Efficient calculation of future periods
- Category and department aggregations cached
- Reports generate in real-time

## Advanced Features

### Future-Dated Parameter Changes

Update depreciation parameters with future effective dates:

```
Current configuration: Straight-Line, 5 years
Effective Date: Dec 31, 2024

New configuration: Reducing Balance, 3 years  
Effective Date: Jan 1, 2025

Result: Old parameters used through 2024
        New parameters apply from 2025 onward
```

### Category-Wide Configuration

Apply depreciation to entire categories:

```
Category: "IT Equipment"
Method: Straight-Line
Useful Life: 4 years
Residual Value: ₦0

All new IT assets inherit these settings
Override per-asset as needed
```

### Manual Asset Configuration

Override category settings for specific assets:

```
Category Default: Straight-Line, 5 years
Asset Override: Asset-123, Reducing Balance, 3 years

Asset-123 uses override
Other category assets use default
```

## Troubleshooting

### Asset Not Showing in Dropdown

**Problem**: Selected asset doesn't appear in "Select Asset"
- **Solution**: Remove disposed assets from view
- **Verify**: Asset status is not "Disposed" or "Scrapped"
- **Check**: Asset has purchase cost recorded

### Incorrect Depreciation Amount

**Problem**: Calculated depreciation doesn't match expectations
- **Verify**: 
  - Method selected is correct
  - Useful life is correct
  - Residual value is correct
  - Asset cost is accurate
- **Check History**: View asset deprecated history for calculation details

### Period Won't Close

**Problem**: Run Depreciation showing errors
- **Step 1**: Verify all assets have depreciation configured
- **Step 2**: Check period end date is valid (not future)
- **Step 3**: Confirm not running duplicate period
- **Step 4**: Review system logs for specific errors

## Frequently Asked Questions

**Q: Can I change depreciation method after closing a period?**
A: Yes. Create new configuration with future effective date. Previous periods remain unchanged.

**Q: What happens if an asset is disposed mid-year?**
A: System prevents depreciation of disposed assets. Change status to reflect actual status.

**Q: How do I handle asset purchases mid-month?**
A: Use pro-rata calculation. System can calculate partial year depreciation.

**Q: Are these calculations auditable?**
A: Yes. Every depreciation posting includes:
- Original asset cost
- Method used
- Useful life
- Residual value
- Full calculation breakdown
- Timestamp

**Q: Can I export for tax purposes?**
A: Yes. Export depreciation history and reports. Format suitable for tax authority reporting.

## Support & Contact

For questions or issues:
- Check this guide for common scenarios
- Review error messages and troubleshooting section
- Contact your system administrator
- Submit support ticket with asset code and period

---

**Last Updated**: June 2026
**Version**: 1.0
**Status**: Production Ready

