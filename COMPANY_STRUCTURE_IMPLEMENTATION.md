# Company Structure & Department Heads Management System

## Overview

This comprehensive implementation adds a complete company organizational structure management system to the FAMS (Fixed Asset Management System) application. Super Administrators can now:

- Define and manage company entities
- Create and manage office locations and branches  
- Organize departments within branches
- Assign users as department heads
- Track and manage department head assignments

## Architecture

### Database Layer

#### Entities Created

1. **Company** - Top-level organizational entity
   - Fields: name, description, registrationNumber, taxId, industry, status
   - Relationships: One-to-many with Locations, Branches, Departments
   - Statuses: ACTIVE, INACTIVE, SUSPENDED

2. **Location** - Physical locations for the company
   - Fields: name, address, city, state, country, postalCode, phoneNumber, contactPerson
   - Types: OFFICE, WAREHOUSE, BRANCH, FACILITY
   - Statuses: ACTIVE, INACTIVE, CLOSED
   - Relationships: Many-to-one with Company, One-to-many with Branches

3. **Branch** - Company branches at specific locations
   - Fields: name, branchCode, description, managerName, managerPhone, managerEmail
   - Statuses: ACTIVE, INACTIVE, CLOSED
   - Relationships: Many-to-one with Company and Location, One-to-many with Departments

4. **Department** - Organizational departments within branches
   - Fields: name, departmentCode, description, budget
   - Statuses: ACTIVE, INACTIVE, CLOSED
   - Relationships: Many-to-one with Company and Branch, One-to-many with DepartmentHeads

5. **DepartmentHead** - User assignments as department heads
   - Fields: userId (Keycloak), userName, userEmail, fullName, isPrimary
   - Statuses: ACTIVE, INACTIVE, REMOVED
   - Relationships: Many-to-one with Department
   - Unique Constraint: One user per department (with cascade handling for primary heads)

#### Repositories

- **CompanyRepository** - Search by name and status
- **LocationRepository** - Filter by company and status
- **BranchRepository** - Filter by company, location, and status
- **DepartmentRepository** - Filter by company, branch, and status
- **DepartmentHeadRepository** - Advanced queries for finding heads by department, user, and company

### Business Logic Layer

#### CompanyStructureService

A comprehensive service layer handling all business operations:

**Company Operations:**
- `createCompany()` - Create with validation
- `updateCompany()` - Update existing companies
- `deleteCompany()` - Soft delete (marks as inactive)
- `getAllCompanies()` - List all active companies

**Location Operations:**
- `createLocation()` - Create with company validation
- `updateLocation()` - Update location details
- `deleteLocation()` - Soft delete
- `getLocationsByCompany()` - Get locations for a company

**Branch Operations:**
- `createBranch()` - Create with company and location validation
- `updateBranch()` - Update branch details
- `deleteBranch()` - Soft delete
- `getBranchesByCompany()` - Get branches for a company
- `getBranchesByLocation()` - Get branches at a location

**Department Operations:**
- `createDepartment()` - Create with branch validation
- `updateDepartment()` - Update department details
- `deleteDepartment()` - Soft delete
- `getDepartmentsByCompany()` - Get departments in a company
- `getDepartmentsByBranch()` - Get departments in a branch

**Department Head Operations:**
- `assignDepartmentHead()` - Assign user with primary head handling
- `removeDepartmentHead()` - Remove/deactivate department head
- `getDepartmentHeads()` - Get all heads for a department
- `getAllDepartmentHeads()` - List all active department heads
- `getDepartmentHeadsByUser()` - Get all departments where user is head

**Features:**
- Automatic timestamp management (@PrePersist, @PreUpdate)
- Soft delete support (isActive flag)
- Comprehensive error handling and validation
- DTO mapping for API responses

### Presentation Layer

#### SuperAdminCompanyController

REST Controller with endpoints for:

**Views:**
- `GET /superadmin/company-structure` - Company structure management page
- `GET /superadmin/departments` - Departments management page
- `GET /superadmin/department-heads` - Department heads management page

**Company Management:**
- `POST /superadmin/company/create` - Create company
- `POST /superadmin/company/update/{id}` - Update company
- `POST /superadmin/company/delete/{id}` - Delete company

**Location Management:**
- `POST /superadmin/location/create` - Create location
- `POST /superadmin/location/update/{id}` - Update location
- `POST /superadmin/location/delete/{id}` - Delete location

**Branch Management:**
- `POST /superadmin/branch/create` - Create branch
- `POST /superadmin/branch/update/{id}` - Update branch
- `POST /superadmin/branch/delete/{id}` - Delete branch

**Department Management:**
- `POST /superadmin/department/create` - Create department
- `POST /superadmin/department/update/{id}` - Update department
- `POST /superadmin/department/delete/{id}` - Delete department

**Department Head Management:**
- `POST /superadmin/department-head/assign` - Assign department head
- `POST /superadmin/department-head/remove/{id}` - Remove department head

**API Endpoints:**
- `GET /superadmin/api/branches/{companyId}` - Get branches for company (JSON)
- `GET /superadmin/api/departments/{branchId}` - Get departments for branch (JSON)
- `GET /superadmin/api/locations/{companyId}` - Get locations for company (JSON)
- `GET /superadmin/api/department-heads/{departmentId}` - Get heads for department (JSON)

**Features:**
- Flash attributes for success/error messages
- User name resolution from OidcUser principal
- Keycloak integration for user management
- HTML sanitization for security
- Proper error message truncation (250 chars)

### User Interface

#### company-structure.html
- **Purpose:** Manage companies, locations, and branches
- **Layout:** Tabbed interface (Companies, Locations, Branches)
- **Features:**
  - Create forms with validation
  - Data tables with status badges
  - Edit/Delete actions
  - Dynamic dropdown population via AJAX
  - Status indicators (Active, Inactive)
  - Responsive grid layout (1-3 columns)
  - Consistent Material Design 3 styling

#### departments-management.html
- **Purpose:** Create and manage departments
- **Sections:**
  - Create department form with company/branch selection
  - All departments table
  - Department status tracking
  - Dynamic branch loading
  - Status badges (Active, Inactive, Closed)
  - Comprehensive error handling

#### department-heads-management.html
- **Purpose:** Assign and manage department heads
- **Sections:**
  - Assign department head form
  - User selection dropdown (from Keycloak)
  - Auto-filled user info
  - Primary head designation
  - Department heads listing table
  - Statistics cards (Total, Active, Department count)
  - Status and removal tracking

### UI/UX Design

**Design System:**
- Tailwind CSS with custom color palette
- Material Design 3 colors:
  - Primary: #98001a (burgundy)
  - Success: #1A7A4A (green)
  - Warning: #A05C00 (orange)
  - Error: #ba1a1a (red)
  - Surfaces: #FFFFFF, #F4F5F7

**Components:**
- Card-based layouts with shadows
- Tabbed navigation with active states
- Form controls with proper spacing
- Data tables with hover effects
- Status badges with color coding
- Material Symbols icons
- Toast-like alert messages
- Confirmation dialogs for destructive actions

**Responsive Design:**
- Mobile-first approach
- Grid layouts (1, 2, 3 columns)
- Adaptive forms and tables
- Touch-friendly button sizes

## Data Flow

### Creating a Company
```
User Input → Controller → Service → Repository → Database
                ↓
         Validation Check
                ↓
         DTO Mapping
                ↓
         Flash Message → Redirect
```

### Assigning Department Head
```
User Selects Department & User → Controller
                ↓
         Primary Head Check (Remove old if needed)
                ↓
         Create DepartmentHead Entity
                ↓
         Save to Database
                ↓
         Success Message → Redirect
```

### Fetching Related Data (AJAX)
```
User Selects Company → JavaScript Fetch
                ↓
         GET /superadmin/api/branches/{id}
                ↓
         Controller → Service → Repository
                ↓
         ApiResponse JSON
                ↓
         JavaScript Populates Dropdown
```

## Error Handling

### Service Layer
- Try-catch blocks for all database operations
- Meaningful error messages
- Logging of all errors
- Transaction rollback on failure

### Controller Layer
- Request validation
- Duplicate prevention checks
- Foreign key validation
- HTML sanitization for security
- Error messages via flash attributes

### UI Layer
- Form validation (required fields)
- Confirmation dialogs for destructive actions
- Error alert banners
- Success toast notifications
- User-friendly error messages

### Example Error Messages
- "Branch with this name already exists in this company"
- "Location not found"
- "User is already a department head for this department"
- "Failed to create company: {error message}"

## Security Features

1. **Authentication:** Integrated with Spring Security & Keycloak
2. **Authorization:** Super Admin role requirement (enforced at controller level)
3. **Input Validation:** @NotBlank, @NotNull annotations
4. **SQL Injection Prevention:** Parameterized queries via JPA
5. **XSS Prevention:** HTML sanitization in error messages
6. **Unique Constraints:** Database-level uniqueness for department-user combinations

## Navigation Integration

Updated `admin-layout.html` sidebar with new navigation links:
- Company Structure
- Departments
- Department Heads

Links appear in the main navigation with appropriate icons and are highlighted when active.

## File Structure

```
src/main/java/com/example/fams/organization/
├── Company.java
├── Location.java
├── Branch.java
├── Department.java
├── DepartmentHead.java
├── CompanyRepository.java
├── LocationRepository.java
├── BranchRepository.java
├── DepartmentRepository.java
├── DepartmentHeadRepository.java
├── CompanyStructureService.java
├── dto/
│   ├── CompanyDTO.java
│   ├── LocationDTO.java
│   ├── BranchDTO.java
│   ├── DepartmentDTO.java
│   └── DepartmentHeadDTO.java

src/main/java/com/example/fams/superadmin/
├── SuperAdminCompanyController.java

src/main/resources/templates/admin/
├── company-structure.html
├── departments-management.html
└── department-heads-management.html
```

## API Response Format

All API responses follow the ApiResponse pattern:

**Success Response:**
```json
{
  "success": true,
  "message": "Data retrieved successfully",
  "data": [...],
  "timestamp": "2024-10-17T10:30:00"
}
```

**Error Response:**
```json
{
  "success": false,
  "message": "Operation failed",
  "error": {
    "code": "ErrorClass",
    "description": "Error description here"
  },
  "timestamp": "2024-10-17T10:30:00"
}
```

## Usage Guide

### For Super Admin Users

1. **Access Company Structure:** Click "Company Structure" in sidebar
2. **Create Company:** Fill form in Companies tab and click "Create Company"
3. **Add Locations:** Switch to Locations tab, select company, fill form, submit
4. **Create Branches:** Go to Branches tab, select company and location, submit
5. **Manage Departments:** Click "Departments" in sidebar, select branch, create
6. **Assign Heads:** Click "Department Heads", select department and user, assign
7. **Update or Remove:** Use edit/delete buttons in tables or remove from heads list

### Workflow Example

1. Create Company "Acme Corp"
2. Create Location "Lagos HQ" for Acme Corp
3. Create Branch "Lagos Branch" at Lagos HQ
4. Create Department "Finance" in Lagos Branch
5. Assign User "John Doe" as Head of Finance department
6. John now has department head role and can:
   - Access department head dashboard
   - Manage assets in that department
   - Submit department reports

## Validation Rules

- Company name: Required, unique
- Location name: Required, unique per company
- Branch name: Required, unique per company
- Department name: Required, unique per branch
- Department head: One primary per department, uniqueness per user-department pair
- All dates: Automatically set (created, updated, assigned, removed)

## Status Lifecycle

### Company Status
ACTIVE → INACTIVE/SUSPENDED → (cannot revert in current implementation)

### Location Status
ACTIVE → INACTIVE/CLOSED

### Branch Status
ACTIVE → INACTIVE/CLOSED

### Department Status
ACTIVE → INACTIVE/CLOSED

### Department Head Status
ACTIVE → INACTIVE/REMOVED

## Future Enhancements

1. Bulk operations for departments and heads
2. Department hierarchy/sub-departments
3. Department budget tracking and reporting
4. User role assignment consolidation with Keycloak
5. Advanced filtering and search
6. Export to CSV/Excel
7. Department head approval workflows
8. Time-based assignments (temporary heads)
9. Audit trail for all structure changes
10. Organization chart visualization

## Troubleshooting

### Issue: Locations not loading in branch form
- **Solution:** Ensure company is selected first; check browser console for AJAX errors

### Issue: Department head assignment fails
- **Solution:** Verify user exists in Keycloak; check for duplicate assignments

### Issue: Changes not persisting
- **Solution:** Check database logs; ensure transactions are not rolling back

### Issue: Navigation links not appearing
- **Solution:** Clear browser cache; verify admin-layout.html was updated correctly

## Testing Recommendations

1. **Unit Tests:** Service layer methods
2. **Integration Tests:** Repository queries
3. **Controller Tests:** Request handling and response formatting
4. **UI Tests:** Form submission and validation
5. **Security Tests:** Authorization checks
6. **Performance Tests:** Large dataset handling

## Dependencies

Required dependencies (already in pom.xml):
- Spring Boot Data JPA
- PostgreSQL Driver
- Lombok
- Thymeleaf
- Spring Security
- Keycloak Admin Client

## Configuration

No additional configuration required. The system uses:
- Auto-configuration for JPA entities
- Spring Security with existing Keycloak realm
- Thymeleaf auto-configuration for template rendering
- Tailwind CSS via CDN

## Support and Maintenance

- All operations are logged via SLF4J
- Database transactions are properly managed
- Soft delete ensures data is never lost
- Timestamps track all changes
- Error messages are user-friendly and actionable

