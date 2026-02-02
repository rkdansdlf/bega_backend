# Team Optimization Implementation Guide

**Quick Start Guide for Implementing Team Data Fixes**

---

## Step-by-Step Implementation

### Step 1: Backup Current Database (CRITICAL)

```bash
# Export teams table before any changes
cd /Users/mac/project/KBO_platform/backend_bega/scripts

# Using psql (requires .env credentials)
psql $DB_URL -c "\COPY teams TO 'backup_teams_$(date +%Y%m%d_%H%M%S).csv' WITH CSV HEADER"

# Or create full database backup
pg_dump $DB_URL > backup_$(date +%Y%m%d_%H%M%S).sql
```

### Step 2: Run Analysis Script

```bash
# Connect to database and run analysis
psql $DB_URL -f analyze_team_data.sql > analysis_results.txt

# Review the output
less analysis_results.txt

# Key things to check:
# - Total teams: Should be 44
# - Active teams: Currently ALL 44 (WRONG)
# - Teams without franchise_id: International teams
# - Empty aliases: All teams
```

### Step 3: Fix is_active Flags (CRITICAL FIX)

```bash
# Run the fix script
psql $DB_URL -f fix_01_is_active_flags.sql

# Verify results:
# - Active teams should be 10
# - Inactive teams should be 34
# - Each franchise has exactly 1 active team
```

**Expected Output:**
```
BEFORE FIX - Current State
total_teams | active_teams | inactive_teams
44          | 44           | 0

AFTER FIX - Updated State
total_teams | active_teams | inactive_teams
44          | 10           | 34
```

### Step 4: Update Spring Boot Entities

#### Option A: Simple Update (No PostgreSQL Array)

```bash
cd /Users/mac/project/KBO_platform/backend_bega/BEGA_PROJECT/src/main/java/com/example/demo/entity

# Backup original file
cp TeamEntity.java TeamEntity.java.backup

# Edit TeamEntity.java - Add these fields:
```

Add to `TeamEntity.java`:
```java
@Column(name = "franchise_id")
private Integer franchiseId;

@Column(name = "is_active")
private Boolean isActive;

// Skip aliases for now (implement later if needed)
```

#### Option B: Full Update with PostgreSQL Arrays

If you want alias functionality:

1. Add hibernate-types dependency to `build.gradle`:
   ```gradle
   implementation 'io.hypersistence:hypersistence-utils-hibernate-63:3.7.0'
   ```

2. Use the full `TeamEntity_UPDATED.java` from scripts folder

3. Rebuild project:
   ```bash
   cd /Users/mac/project/KBO_platform/backend_bega/BEGA_PROJECT
   ./gradlew clean build
   ```

### Step 5: Update Repository

```bash
# Backup original
cp src/main/java/com/example/demo/repo/TeamRepository.java \
   src/main/java/com/example/demo/repo/TeamRepository.java.backup

# Copy updated version or manually add methods:
```

Add to `TeamRepository.java`:
```java
// Get active teams only
List<TeamEntity> findByIsActiveTrue();

// Get teams by franchise
List<TeamEntity> findByFranchiseId(Integer franchiseId);
```

### Step 6: Test the Changes

```bash
cd /Users/mac/project/KBO_platform/backend_bega/BEGA_PROJECT

# Run tests
./gradlew test

# Start application
./gradlew bootRun

# Test API endpoint (in another terminal)
curl http://localhost:8080/api/teams/active
```

### Step 7: Populate Aliases (OPTIONAL)

Only if you implemented alias functionality:

```bash
# Run alias population script
psql $DB_URL -f fix_02_populate_aliases.sql

# Verify aliases are populated
psql $DB_URL -c "SELECT team_id, team_name, aliases FROM teams WHERE franchise_id IS NOT NULL LIMIT 20"
```

### Step 8: Handle Edge Cases (OPTIONAL)

```bash
# Run edge case investigation
psql $DB_URL -f fix_03_edge_cases.sql

# Review results and decide:
# - LOT vs LT: Check which exists, fix if needed
# - TP team: Keep as inactive historical team
# - International teams: Already set to inactive
```

---

## Quick Verification Checklist

After implementation, verify:

### Database Checks

```sql
-- Should return 10
SELECT COUNT(*) FROM teams WHERE is_active = true;

-- Should return 34
SELECT COUNT(*) FROM teams WHERE is_active = false;

-- Should show 10 franchises with 1 active team each
SELECT franchise_id, COUNT(*) FROM teams WHERE is_active = true GROUP BY franchise_id;

-- Check current teams list
SELECT team_id, team_name, franchise_id, is_active
FROM teams WHERE is_active = true ORDER BY team_id;
```

Expected active teams:
```
SS   - 삼성
LOT  - 롯데
LG   - LG
OB   - 두산 (current_code)
KIA  - KIA
WO   - 키움
HH   - 한화
SSG  - SSG
NC   - NC
KT   - KT
```

### Spring Boot Checks

```bash
# Application should start without errors
./gradlew bootRun

# No JPA mapping errors
# No SQL exceptions
```

### API Checks

```bash
# Get all teams (should return 44 if no filter)
curl http://localhost:8080/api/teams

# Get active teams only (should return 10)
curl http://localhost:8080/api/teams/active

# Check specific team
curl http://localhost:8080/api/teams/SS
curl http://localhost:8080/api/teams/KIA
```

### Frontend Checks

1. Open frontend application
2. Navigate to team selection page
3. Verify only 10 current teams are shown
4. Check that historical teams don't appear in dropdowns

---

## Rollback Procedure

If something goes wrong:

```bash
# Database rollback
psql $DB_URL -f rollback_fixes.sql

# Code rollback
cd /Users/mac/project/KBO_platform/backend_bega/BEGA_PROJECT
git checkout src/main/java/com/example/demo/entity/TeamEntity.java
git checkout src/main/java/com/example/demo/repo/TeamRepository.java

# Rebuild
./gradlew clean build
```

Or restore from backup:
```bash
# Restore teams table from CSV backup
psql $DB_URL -c "TRUNCATE teams CASCADE"
psql $DB_URL -c "\COPY teams FROM 'backup_teams_20260112.csv' WITH CSV HEADER"
```

---

## Troubleshooting

### Issue: "Column 'is_active' does not exist"

**Solution**: The database already has the column, check your entity mapping:
```java
@Column(name = "is_active")  // Make sure name matches exactly
private Boolean isActive;
```

### Issue: "Cannot resolve PostgreSQL array type"

**Option 1**: Skip aliases for now
```java
// Comment out aliases field temporarily
// @Column(name = "aliases")
// private String[] aliases;
```

**Option 2**: Add hibernate-types dependency
```gradle
implementation 'io.hypersistence:hypersistence-utils-hibernate-63:3.7.0'
```

**Option 3**: Use simple String instead of array
```java
@Column(name = "aliases")
private String aliasesString; // Store as comma-separated

// In service layer
public List<String> getAliases() {
    return aliasesString == null ? List.of() : Arrays.asList(aliasesString.split(","));
}
```

### Issue: "Foreign key violation"

**Cause**: Trying to delete/update teams referenced by other tables

**Solution**: Check foreign key references first:
```sql
-- Find all FK references
SELECT
    tc.table_name,
    kcu.column_name
FROM information_schema.table_constraints tc
JOIN information_schema.key_column_usage kcu
    ON tc.constraint_name = kcu.constraint_name
WHERE tc.constraint_type = 'FOREIGN KEY'
  AND kcu.column_name LIKE '%team%';
```

### Issue: "All tests failing after entity update"

**Solution**: Update test fixtures to include new fields:
```java
TeamEntity team = new TeamEntity();
team.setTeamId("SS");
team.setTeamName("삼성 라이온즈");
team.setIsActive(true);  // Add this
team.setFranchiseId(1);  // Add this
```

---

## Performance Considerations

### Indexes

The database should have these indexes for optimal performance:

```sql
-- Check existing indexes
SELECT indexname, tablename FROM pg_indexes WHERE tablename = 'teams';

-- Recommended indexes (may already exist)
CREATE INDEX IF NOT EXISTS idx_teams_is_active ON teams(is_active);
CREATE INDEX IF NOT EXISTS idx_teams_franchise_id ON teams(franchise_id);
CREATE INDEX IF NOT EXISTS idx_teams_franchise_active ON teams(franchise_id, is_active);
```

### Query Optimization

```java
// Good: Direct query for active teams
List<TeamEntity> activeTeams = teamRepository.findByIsActiveTrue();

// Bad: Fetch all then filter in memory
List<TeamEntity> allTeams = teamRepository.findAll();
List<TeamEntity> activeTeams = allTeams.stream()
    .filter(t -> t.getIsActive())
    .collect(Collectors.toList());
```

---

## Next Steps After Implementation

1. **Update API Documentation**
   - Document new is_active filter parameter
   - Update team list endpoint descriptions

2. **Frontend Integration**
   - Update team selection components to use is_active filter
   - Add historical team view (optional feature)

3. **Add Service Layer Methods**
   ```java
   @Service
   public class TeamService {
       public List<TeamDto> getCurrentTeams() {
           return teamRepository.findByIsActiveTrue()
               .stream()
               .map(this::toDto)
               .collect(Collectors.toList());
       }
   }
   ```

4. **Write Integration Tests**
   ```java
   @Test
   void shouldReturnOnlyActiveTeams() {
       List<TeamEntity> teams = teamRepository.findByIsActiveTrue();
       assertThat(teams).hasSize(10);
       assertThat(teams).allMatch(TeamEntity::getIsActive);
   }
   ```

5. **Update User Documentation**
   - Explain active vs historical teams
   - Document alias search feature (if implemented)

---

## Summary

**Minimum Required Changes:**
1. ✅ Run `fix_01_is_active_flags.sql` in database
2. ✅ Add `franchiseId` and `isActive` fields to `TeamEntity.java`
3. ✅ Add `findByIsActiveTrue()` to `TeamRepository.java`
4. ✅ Test that application starts and API returns correct data

**Optional Enhancements:**
- ⭐ Populate aliases column for flexible team search
- ⭐ Handle LOT/LT edge case if exists
- ⭐ Add comprehensive repository query methods
- ⭐ Write unit and integration tests

**Time Estimate:**
- Minimum: 1-2 hours
- Full implementation: 4-6 hours
- With comprehensive testing: 1 day

---

## Support

If you encounter issues:

1. Check the logs: `./gradlew bootRun` output
2. Verify database state: Run `analyze_team_data.sql`
3. Test database connection: `psql $DB_URL -c "SELECT COUNT(*) FROM teams"`
4. Review entity mappings: Ensure column names match exactly
5. Rollback if needed: Use `rollback_fixes.sql` and git checkout

**Database Connection String (Oracle):**
```bash
# From .env file
SPRING_DATASOURCE_URL=jdbc:oracle:thin:@your-oracle-host:1521/yourdb
SPRING_DATASOURCE_USERNAME=your_db_username
SPRING_DATASOURCE_PASSWORD=your_db_password
```

**Supabase (migration only):**  
Supabase 무료 플랜 용량 한계로 인해 OCI로 전환했으며, 아래 설정은 마이그레이션/검증 용도로만 유지합니다.
```bash
SUPABASE_DB_URL=postgresql://postgres:[password]@db.[project-ref].supabase.co:5432/postgres
```
