# Asset Depreciation Module - Setup & Installation Guide

## Prerequisites

- Java 21 (required for FAMS)
- Spring Boot 3.5.3
- PostgreSQL database
- Maven 3.8+

## Installation Steps

### Step 1: Database Initialization

Run the following SQL statements to create the required tables:

```sql
-- Create depreciation_parameters table
CREATE TABLE depreciation_parameters (
    id BIGSERIAL PRIMARY KEY,
    category VARCHAR(120),
    asset_id BIGINT,
    method VARCHAR(50) NOT NULL,
    useful_life_years INT NOT NULL,
    residual_value DECIMAL(19,2),
    effective_from_date DATE NOT NULL,
    effective_to_date DATE,
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT true,
    UNIQUE(asset_id, effective_from_date),
    INDEX idx_asset_id (asset_id),
    INDEX idx_category (category),
    INDEX idx_effective_date (effective_from_date)
);

-- Create depreciation_postings table
CREATE TABLE depreciation_postings (
    id BIGSERIAL PRIMARY KEY,
    asset_id BIGINT NOT NULL,
    asset_code VARCHAR(32) NOT NULL,
    asset_name VARCHAR(160) NOT NULL,
    category VARCHAR(120) NOT NULL,
    department VARCHAR(120) NOT NULL,
    depreciation_method VARCHAR(50) NOT NULL,
    depreciation_period VARCHAR(32) NOT NULL,
    fiscal_year INT NOT NULL,
    asset_cost DECIMAL(19,2) NOT NULL,
    opening_accumulated_depreciation DECIMAL(19,2) NOT NULL,
    depreciation_charge DECIMAL(19,2) NOT NULL,
    closing_accumulated_depreciation DECIMAL(19,2) NOT NULL,
    book_value DECIMAL(19,2) NOT NULL,
    residual_value DECIMAL(19,2),
    fully_depreciated BOOLEAN NOT NULL DEFAULT false,
    useful_life_years INT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    UNIQUE(asset_id, depreciation_period),
    INDEX idx_asset_id (asset_id),
    INDEX idx_period (depreciation_period),
    INDEX idx_category (category),
    INDEX idx_department (department),
    INDEX idx_fiscal_year (fiscal_year)
);
```

### Step 2: Code Integration

The depreciation module code is already included in the project at:
```
src/main/java/com/example/fams/depreciation/
```

All classes are pre-configured with proper annotations and dependencies.

### Step 3: Build & Compile

```bash
# Clean build
mvn clean package

# Run tests (if available)
mvn test

# Verify no errors
# Should see: BUILD SUCCESS
```

### Step 4: Application Startup

```bash
# From project root
mvn spring-boot:run

# Or using JAR
java -jar target/FAMS-0.0.1-SNAPSHOT.jar
```

### Step 5: Verify Installation

1. Open browser to `http://localhost:9090`
2. Navigate to **Depreciation** in sidebar
3. Verify these pages are accessible:
   - `/depreciation` - Main dashboard
   - `/depreciation/configure` - Configuration page
   - `/depreciation/run` - Period close page
   - `/depreciation/history` - Asset history page

### Step 6: Test API Endpoints

```bash
# Get depreciation summary
curl http://localhost:9090/api/depreciation/summary

# Get all assets (for dropdown population)
curl http://localhost:9090/api/assets

# Get parameters for asset (replace ID)
curl http://localhost:9090/api/depreciation/parameters/asset/1
```

## Configuration

### Application Properties

Add to `application.properties` if not already present:

```properties
# Database
spring.datasource.url=jdbc:postgresql://localhost:5432/fams_db
spring.datasource.username=postgres
spring.datasource.password=your_password
spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect
spring.jpa.hibernate.ddl-auto=validate

# Application
spring.application.name=FAMS
server.port=9090

# Logging
logging.level.com.example.fams.depreciation=INFO
```

### JPA Configuration

Ensure JPA is properly configured in `pom.xml`:

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-jpa</artifactId>
</dependency>
```

## Module Structure

```
src/main/java/com/example/fams/depreciation/
├── DepreciationMethod.java              # Enum: SL, RB, DDB
├── DepreciationParameters.java          # Entity: Configuration
├── DepreciationParametersRepository.java # JPA Repository
├── DepreciationPosting.java             # Entity: Posting record
├── DepreciationPostingRepository.java    # JPA Repository
├── DepreciationCalculationService.java  # Calculations
├── DepreciationService.java             # Business logic
├── DepreciationController.java          # REST API
├── DepreciationReport.java              # DTO
├── DepreciationCategoryReport.java      # DTO
├── DepreciationDepartmentReport.java    # DTO
├── DepreciationSummary.java             # DTO
├── DepreciationRunResult.java           # DTO
└── DepreciationValidation.java          # Utilities

src/main/resources/templates/assets/
├── depreciation-management.html         # Main dashboard
├── depreciation-configure.html          # Configuration
├── depreciation-run.html                # Period close
└── depreciation-history.html            # Asset history

src/main/java/com/example/fams/core/
└── ApiResponse.java                     # Generic response wrapper
```

## File Checklist

- [x] Created `DepreciationMethod.java`
- [x] Created `DepreciationParameters.java`
- [x] Created `DepreciationParametersRepository.java`
- [x] Created `DepreciationPosting.java`
- [x] Created `DepreciationPostingRepository.java`
- [x] Created `DepreciationCalculationService.java`
- [x] Created `DepreciationService.java`
- [x] Created `DepreciationController.java`
- [x] Created `DepreciationReport.java`
- [x] Created `DepreciationCategoryReport.java`
- [x] Created `DepreciationDepartmentReport.java`
- [x] Created `DepreciationSummary.java`
- [x] Created `DepreciationRunResult.java`
- [x] Created `DepreciationValidation.java`
- [x] Created `ApiResponse.java`
- [x] Created `depreciation-configure.html`
- [x] Created `depreciation-run.html`
- [x] Created `depreciation-history.html`
- [x] Updated `depreciation-management.html`
- [x] Updated `PageController.java`
- [x] Updated `AssetController.java`
- [x] Created `DEPRECIATION_GUIDE.md`
- [x] Created `DEPRECIATION_IMPLEMENTATION.md`

## Basic Usage Workflow

### 1. Configure Asset Depreciation
```
Navigate to: Depreciation → Configure
→ Select asset → Choose method → Enter useful life
→ Set residual value → Save
```

### 2. Close Depreciation Period
```
Navigate to: Depreciation → Run Depreciation
→ Select period type and date → Confirm → Run
→ Review results
```

### 3. View Asset History
```
Navigate to: Depreciation → History
→ Select asset → View depreciation postings
→ Click row for details or export CSV
```

## Troubleshooting

### Compilation Errors

**Error**: "class not found" for depreciation classes
- **Solution**: Ensure all files are in correct package: `com.example.fams.depreciation`
- **Check**: maven compilation output for file paths

**Error**: "No repository bean created for interface"
- **Solution**: Verify repositories extend `JpaRepository`
- **Check**: @Repository annotation present

### Runtime Errors

**Error**: "Table depreciation_parameters doesn't exist"
- **Solution**: Execute SQL schema creation statements
- **Verify**: Tables exist in database: `\d depreciation_parameters`

**Error**: "Cannot instantiate DepreciationService"
- **Solution**: Verify all constructor dependencies are available
- **Check**: AssetRepository, DepreciationParametersRepository, etc.

**Error**: "Failed to load assets in dropdown"
- **Solution**: Ensure /api/assets endpoint is working
- **Test**: `curl http://localhost:9090/api/assets`

### Database Issues

```sql
-- Check table structures
\d depreciation_parameters;
\d depreciation_postings;

-- Review sample data
SELECT COUNT(*) FROM depreciation_parameters;
SELECT COUNT(*) FROM depreciation_postings;

-- Check indexes
\d depreciation_postings;
```

## Performance Tuning

### Database Indexes
Ensure indexes are created for best performance:

```sql
-- Asset ID quick lookup
CREATE INDEX IF NOT EXISTS idx_posting_asset_period 
  ON depreciation_postings(asset_id, depreciation_period DESC);

-- Period quick lookup
CREATE INDEX IF NOT EXISTS idx_posting_period_asset 
  ON depreciation_postings(depreciation_period, asset_code);

-- Category queries
CREATE INDEX IF NOT EXISTS idx_posting_category_period 
  ON depreciation_postings(category, depreciation_period DESC);
```

### Connection Pooling
Optimize database connections in `application.properties`:

```properties
spring.datasource.hikari.maximum-pool-size=20
spring.datasource.hikari.minimum-idle=5
spring.datasource.hikari.connection-timeout=20000
```

## Security Considerations

1. **SQL Injection**: JPA parameterized queries prevent attacks
2. **Data Validation**: Input validation in DepreciationValidation
3. **Authorization**: Implement Spring Security for role-based access
4. **Audit Logging**: All changes logged with timestamps and user info

## Monitoring & Logging

### Enable Debug Logging
```properties
logging.level.com.example.fams.depreciation=DEBUG
logging.level.org.hibernate.SQL=DEBUG
logging.level.org.hibernate.type.descriptor.sql.BasicBinder=TRACE
```

### Key Logging Points
- Parameter configuration saves
- Depreciation run start/completion
- Calculation errors
- Period close operations

## Support & Contact

For issues or questions:
1. Check DEPRECIATION_GUIDE.md for user guide
2. Review DEPRECIATION_IMPLEMENTATION.md for technical details
3. Check application logs for errors
4. Contact system administrator

---

**Setup Date**: June 2026
**Last Updated**: June 2026
**Status**: Ready for Deployment

