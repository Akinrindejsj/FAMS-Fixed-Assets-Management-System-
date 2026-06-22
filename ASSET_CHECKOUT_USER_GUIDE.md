# Asset Check-In/Check-Out - User Guide

## Quick Start

### Accessing the Feature
1. Log in to FAMS
2. Look for "Check-In/Check-Out" in the left sidebar (under Asset Management section)
3. Click to access the main dashboard

OR

1. Go to any asset's detail page
2. Click the "Checkout" button in the action buttons

## Workflow Overview

```
ASSET CHECKOUT PROCESS
├── STEP 1: Checkout Asset
│   ├── Select asset
│   ├── Enter who is checking out
│   ├── Set checkout and due return dates
│   └── Document condition before checkout
│
├── STEP 2: Asset Checked Out (Status: Checked Out)
│   ├── Asset is in use
│   └── Tracking information available in dashboard
│
├── STEP 3: Return Asset
│   ├── Go to checkout record
│   ├── Click "Return Asset"
│   ├── Document condition after return
│   └── Add any notes about condition/issues
│
├── STEP 4: Asset Returned (Status: Returned)
│   ├── Pending verification
│   └── Appears in "Pending Verification" tab
│
└── STEP 5: Verify Return
    ├── Manager/admin verifies the asset
    ├── Final inspection notes added
    └── Asset returns to "Available" status
```

## Step-by-Step Operations

### Creating a New Checkout

1. **Navigate to Check-In/Check-Out Dashboard**
   - Click "Check-In/Check-Out" in sidebar

2. **Find the Asset**
   - From asset detail page, click "Checkout" button
   - OR use asset search/list to find asset

3. **Complete Checkout Form**
   - **Checked Out By** (Required)
     - Enter employee name or ID
     - Example: "John Smith" or "EMP-123"
   
   - **Checkout Date** (Required)
     - Select date asset is checked out
     - Defaults to today
   
   - **Due Return Date** (Required)
     - Select when asset should be returned
     - Must be same day or later than checkout date
     - Example: Project duration, trip dates, etc.
   
   - **Purpose** (Optional)
     - Explain why asset is being checked out
     - Examples:
       - "Field survey for project Alpha"
       - "Training session"
       - "Off-site meeting"
   
   - **Condition Before Checkout** (Optional)
     - Select from dropdown: Excellent, Good, Fair, Poor, Needs Repair
     - Helps track asset condition changes

4. **Review Previous Checkouts**
   - View calendar of previous checkouts below form
   - Helps identify patterns and issues

5. **Submit**
   - Click "Checkout Asset" button
   - You'll see success message
   - Asset status changes to "Checked Out"

### Processing a Return

1. **Locate the Active Checkout**
   - Go to Check-In/Check-Out dashboard
   - Click "Active (Checked Out)" tab
   - OR find the checkout in "All Checkouts" tab
   - Click "Return Asset" button

2. **Complete Return Form**
   - **Condition After Return** (Recommended)
     - Select from dropdown
     - Important: Document any damage!
     - Options: Excellent, Good, Fair, Poor, Damaged, Lost
   
   - **Return Notes** (Optional)
     - Document any issues found
     - Examples:
       - "Minor scuff on screen"
       - "Working properly"
       - "Needs repair - display flickering"

3. **Submit Return**
   - Click "Submit Return" button
   - You'll see success message
   - Asset status changes to "Returned"

### Verifying a Return

1. **Locate Pending Returns**
   - Go to Check-In/Check-Out dashboard
   - Click "Pending Verification" tab
   - OR click "Returned" tab in All Checkouts

2. **Complete Verification Form**
   - **Verified By** (Required)
     - Your name or ID
     - Who is approving this return
   
   - **Verification Notes** (Optional)
     - Final inspection findings
     - Confirm received in good condition
     - Document any maintenance needed

3. **Submit Verification**
   - Click "Complete Verification" button
   - Asset status changes to "Available"
   - Asset is now ready for next checkout

## Dashboard Features

### Statistics Cards
- **Total Checkouts**: All checkout records
- **Currently Checked Out**: Assets still out
- **Overdue Returns**: Late returns
- **Pending Verification**: Returns waiting approval

### Tabs

#### All Checkouts
- Complete list of all checkout records
- View all statuses
- Sort by date
- Includes: Asset, Person, Dates, Purpose, Status

#### Active (Checked Out)
- Only currently checked-out assets
- Quick return processing
- Shows: Condition before checkout
- Action: "Return Asset"

#### Overdue Checkouts
- Returns past due date
- Alert color highlighting
- Shows days overdue
- Action: "Process Return"

#### Pending Verification
- Returned but not yet verified
- Shows condition upon return
- Requires verification
- Action: "Verify & Complete"

## Viewing Checkout Details

1. Click "View" button on any checkout record
2. See complete information:
   - Asset details
   - Checkout information
   - Return information (if returned)
   - Verification information (if verified)
   - Complete timeline

3. From detail view:
   - Click "Process Return" if still checked out
   - Click "Verify Return" if awaiting verification
   - Click "View Asset" to go to asset details

## Tips & Best Practices

### ✓ Do This

- **Record Purpose**: Always note WHY asset is checked out
- **Set Realistic Dates**: Due dates should match actual need
- **Document Condition**: Always select condition before checkout
- **Note Issues**: Record any damage found upon return
- **Verify Promptly**: Don't let returns pile up
- **Check Dashboard**: Review overdue items regularly

### ✗ Avoid This

- **Missing Custody Info**: Always record who checked out
- **Unrealistic Dates**: Set due dates based on actual use
- **Skipping Verification**: All returns must be verified
- **Vague Notes**: Be specific about condition/damage
- **Losing Track**: Use dashboard to track items

## Common Scenarios

### Scenario 1: Project Equipment Checkout
```
1. Click "Checkout Asset" on laptop
2. Who: "Project Team Lead"
3. Dates: Today → End of project date
4. Purpose: "Development project #123"
5. Condition: "Good"
6. SUBMIT
```

### Scenario 2: Daily Field Asset
```
1. Morning: Check out measurement tools
   - Due: End of day
   - Purpose: "Site survey"
   
2. Afternoon: Return tools
   - Condition: "Good"
   - Notes: "All items functional"
   
3. Next morning: Verify return
   - Approved by: Manager name
   - Notes: "Verified - ready for next use"
```

### Scenario 3: Damaged Asset Return
```
1. Employee returns laptop
   - Condition: "Damaged"
   - Notes: "Display has crack in corner - still works but needs repair"

2. Manager verifies
   - Creates maintenance ticket
   - Notes: "Confirmed screen damage - submit for repair"
   - Asset status: Available (but marked for repair)

3. Maintenance team handles repair
   - Uses maintenance module
   - Updates asset when complete
```

## Status Meanings

| Status | Meaning | What You Can Do |
|--------|---------|-----------------|
| **Available** | Ready to be checked out | Check out the asset |
| **Checked Out** | Currently in use | Process return |
| **Returned** | Returned but not verified | Verify the return |
| **Verified** | Complete, asset is available | Nothing - cycle complete |

## Troubleshooting

### Can't Find Asset to Checkout
- Use search bar on main dashboard
- Browse asset list
- Check asset is not "Disposed"

### Can't Process Return
- Asset must be in "Checked Out" status
- Check asset detail page for current status
- Verify you have the right checkout record

### Overdue Checkout
- Not a problem! Go to checkout record
- Click "Process Return"
- Document current condition
- Verify the return
- No penalties - just keeping records accurate

### Need to Cancel Checkout
- Contact your administrator
- Record why checkout was cancelled
- Use asset detail page to check current status

## When to Use Each Feature

### Use Checkout For
- Equipment loans
- Off-site work
- Training sessions
- Temporary transfers
- Asset movement tracking

### Use Maintenance For
- Repairs needed
- Scheduled maintenance
- Service records

### Use Lifecycle for
- Permanent assignments
- Transfers
- Returns to inventory
- Disposal

## Support

If you encounter issues:
1. Check this guide for your scenario
2. Review error message carefully
3. Ensure all required fields are filled
4. Contact your FAMS administrator

---

**Last Updated**: June 2026
**Feature Version**: 1.0
**Status**: Production Ready

