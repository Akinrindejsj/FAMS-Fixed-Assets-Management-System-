# Company Structure Management - Quick Start Guide

## What Was Implemented

A complete organizational structure management system for super admins with the following components:

### 1. Database Entities (5 new tables)
- `companies` - Company information
- `locations` - Office/warehouse locations  
- `branches` - Company branches
- `departments` - Organizational departments
- `department_heads` - Department head assignments

### 2. REST APIs
- Company CRUD operations
- Location management (create, update, delete)
- Branch management (create, update, delete)
- Department management (create, update, delete)
- Department head assignment/removal

### 3. Three New UI Pages
1. **Company Structure** (`/superadmin/company-structure`)
   - Tabbed interface for companies, locations, and branches
   - Create, view, and delete operations
   - Dynamic cascading dropdowns

2. **Departments** (`/superadmin/departments`)
   - Create departments within branches
   - List all departments with status
   - Delete departments

3. **Department Heads** (`/superadmin/department-heads`)
   - Assign users as department heads
   - Manage primary head designation
   - View all department heads with statistics
   - Remove department head assignments

### 4. Navigation Integration
- Updated admin sidebar with 3 new menu items
- Proper active state highlighting
- Consistent Material Design 3 styling

## Getting Started

### Prerequisites
- Spring Boot 3.5.3 with Java 21
- PostgreSQL database
- Keycloak server running
- Maven

### Installation Steps

1. **Build the project:**
   ```bash
   cd C:\Users\Akinkunmi\IdeaProjects\FAMS
   mvn clean install
   ```

2. **Start the application:**
   ```bash
   mvn spring-boot:run
   ```

3. **Access the application:**
   - Navigate to `http://localhost:8080`
   - Login as a super admin user

### First Steps

1. **Create a Company:**
   - Click "Company Structure" in sidebar
   - Fill in company details (name is required)
   - Click "Create Company"

2. **Create a Location:**
   - Switch to "Locations" tab
   - Select the company you created
   - Enter location details (city, address, etc.)
   - Click "Create Location"

3. **Create a Branch:**
   - Switch to "Branches" tab
   - Select company and location
   - Enter branch details (name is required)
   - Click "Create Branch"

4. **Create a Department:**
   - Click "Departments" in sidebar
   - Select company and branch
   - Enter department name
   - Click "Create Department"

5. **Assign Department Head:**
   - Click "Department Heads" in sidebar
   - Select department from dropdown
   - Select user from the user list
   - Optionally mark as primary head
   - Click "Assign Head"

## File Locations

### Java Code
```
src/main/java/com/example/fams/organization/
├── Company.java
├── Location.java
├── Branch.java
├── Department.java
├── DepartmentHead.java
├── CompanyStructureService.java
├── *Repository.java (5 files)
└── dto/
    ├── CompanyDTO.java
    ├── LocationDTO.java
    ├── BranchDTO.java
    ├── DepartmentDTO.java
    └── DepartmentHeadDTO.java

src/main/java/com/example/fams/superadmin/
└── SuperAdminCompanyController.java
```

### HTML Templates
```
src/main/resources/templates/admin/
├── company-structure.html
├── departments-management.html
└── department-heads-management.html
```

### Navigation
```
src/main/resources/templates/layouts/
└── admin-layout.html (updated)
```

## Key Features

✅ **Comprehensive Error Handling**
- User-friendly error messages
- Form validation
- Duplicate prevention
- Database constraint enforcement

✅ **Consistent UI/UX**
- Matches existing admin dashboard design
- Tailwind CSS styling
- Material Design 3 color scheme
- Responsive layouts
- Status badges and icons

✅ **Dynamic Data Loading**
- Cascading dropdowns (Company → Location → Branch)
- AJAX-based filtering
- Real-time UI updates

✅ **Soft Delete Support**
- All deletions mark items as inactive
- No permanent data loss
- Proper status tracking

✅ **Security**
- Spring Security integration
- Keycloak user management
- Input validation and sanitization
- SQL injection prevention

✅ **Database Integrity**
- Foreign key relationships
- Unique constraints
- Proper indexes
- Transaction management

## API Examples

### Create Company
```bash
POST /superadmin/company/create
Parameters:
  - name: "Acme Corporation"
  - description: "Global technology company"
  - taxId: "TAX12345"
```

### Get Branches by Company
```bash
GET /superadmin/api/branches/1
Response: JSON array of branches
```

### Assign Department Head
```bash
POST /superadmin/department-head/assign
Parameters:
  - departmentId: 5
  - userId: "user-uuid-from-keycloak"
  - userName: "john.doe"
  - userEmail: "john@example.com"
  - isPrimary: true
```

## Status Values

### Companies
- ACTIVE
- INACTIVE
- SUSPENDED

### Locations
- ACTIVE
- INACTIVE
- CLOSED

### Branches
- ACTIVE
- INACTIVE
- CLOSED

### Departments
- ACTIVE
- INACTIVE
- CLOSED

### Department Heads
- ACTIVE
- INACTIVE
- REMOVED

## Database Schema

### companies
```sql
CREATE TABLE companies (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    name VARCHAR(200) NOT NULL UNIQUE,
    description TEXT,
    registration_number VARCHAR(100),
    tax_id VARCHAR(20),
    industry VARCHAR(100),
    status ENUM('ACTIVE', 'INACTIVE', 'SUSPENDED'),
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);
```

### locations
```sql
CREATE TABLE locations (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    company_id BIGINT NOT NULL,
    name VARCHAR(150) NOT NULL,
    address TEXT,
    city VARCHAR(100),
    state VARCHAR(100),
    country VARCHAR(100),
    postal_code VARCHAR(20),
    phone_number VARCHAR(20),
    contact_person VARCHAR(100),
    location_type ENUM('OFFICE', 'WAREHOUSE', 'BRANCH', 'FACILITY'),
    status ENUM('ACTIVE', 'INACTIVE', 'CLOSED'),
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    FOREIGN KEY (company_id) REFERENCES companies(id)
);
```

### branches
```sql
CREATE TABLE branches (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    company_id BIGINT NOT NULL,
    location_id BIGINT NOT NULL,
    name VARCHAR(150) NOT NULL,
    description TEXT,
    branch_code VARCHAR(100),
    manager_name VARCHAR(100),
    manager_phone VARCHAR(20),
    manager_email VARCHAR(100),
    status ENUM('ACTIVE', 'INACTIVE', 'CLOSED'),
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    FOREIGN KEY (company_id) REFERENCES companies(id),
    FOREIGN KEY (location_id) REFERENCES locations(id)
);
```

### departments
```sql
CREATE TABLE departments (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    company_id BIGINT NOT NULL,
    branch_id BIGINT NOT NULL,
    name VARCHAR(150) NOT NULL,
    description TEXT,
    department_code VARCHAR(100),
    budget VARCHAR(100),
    status ENUM('ACTIVE', 'INACTIVE', 'CLOSED'),
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    FOREIGN KEY (company_id) REFERENCES companies(id),
    FOREIGN KEY (branch_id) REFERENCES branches(id)
);
```

### department_heads
```sql
CREATE TABLE department_heads (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    department_id BIGINT NOT NULL,
    user_id VARCHAR(100) NOT NULL,
    user_name VARCHAR(150) NOT NULL,
    user_email VARCHAR(150) NOT NULL,
    full_name VARCHAR(150),
    is_primary BOOLEAN DEFAULT FALSE,
    status ENUM('ACTIVE', 'INACTIVE', 'REMOVED'),
    assigned_at TIMESTAMP NOT NULL,
    removed_at TIMESTAMP,
    is_active BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    FOREIGN KEY (department_id) REFERENCES departments(id),
    UNIQUE KEY unique_user_department (user_id, department_id)
);
```

## Troubleshooting

### Page Not Loading
1. Clear browser cache (Ctrl+F5)
2. Check console for JavaScript errors (F12)
3. Verify user has super admin role in Keycloak

### Dropdown Not Auto-populating
1. Check network tab for failed AJAX requests
2. Verify API endpoint is correct (e.g., `/superadmin/api/branches/1`)
3. Check browser console for errors

### Database Errors
1. Verify PostgreSQL is running
2. Check database credentials in application.yml
3. Ensure tables were created (check migration logs)

### User Not Found in Keycloak
1. Verify Keycloak server is running
2. Check Keycloak realm configuration
3. Ensure user is created in Keycloak with proper groups

## Performance Tips

1. **Indexes:** Database has indexes on foreign keys
2. **Lazy Loading:** All relationships use FetchType.LAZY
3. **Pagination:** Implement if you have thousands of items
4. **Caching:** Consider caching company list for frequently accessed data

## Security Checklist

- ✅ Only super admins can access these pages
- ✅ All inputs are validated
- ✅ SQL injection prevention via parameterized queries
- ✅ XSS prevention via HTML sanitization
- ✅ CSRF protection via Spring Security
- ✅ Keycloak integration for user authentication

## Support & Documentation

Complete documentation available in:
- `COMPANY_STRUCTURE_IMPLEMENTATION.md` - Full technical documentation
- Code comments in Java files
- HTML comments in template files

## Next Steps

1. **Testing:**
   - Create test data
   - Verify all CRUD operations
   - Test cascading deletes
   - Check error handling

2. **Customization:**
   - Add department budgets tracking
   - Implement department hierarchy (sub-departments)
   - Add department contact information
   - Create organization chart visualization

3. **Integration:**
   - Link with asset allocation by department
   - Integrate with reporting system
   - Connect with audit trails
   - Add department-level permissions

## Version Info

- Framework: Spring Boot 3.5.3
- Java: 21
- Database: PostgreSQL
- Templating: Thymeleaf
- Frontend: Tailwind CSS + Bootstrap 5.3.7
- Icons: Google Material Symbols

---

**Created:** June 17, 2026
**Implementation Status:** ✅ Complete and Ready for Testing

