# Team Data Optimization Plan

**Created**: 2026-01-12
**Database**: Supabase PostgreSQL
**Target**: teams, team_franchises, team_history tables

---

## Executive Summary

Based on analysis of the current database state, this plan addresses critical data quality issues in the team management system while maintaining the carefully curated historical data from your team crawlers.

### Key Issues Identified

1. **All 44 teams marked as is_active=true** (CRITICAL)
   - Only 10 current KBO franchises should be active
   - 34 historical/international teams incorrectly marked active

2. **Missing Spring Boot entity fields**
   - `franchise_id`, `is_active`, `aliases` not mapped in Java entities

3. **Empty aliases column**
   - Historical team code mappings not populated

4. **Inconsistent LOT vs LT**
   - Franchise current_code is "LOT" but team_id might be "LT"

---

## Current Database State (Verified)

### team_franchises (10 records)
```
franchise_id | franchise_name | current_code | original_code | founded_year
-------------+----------------+--------------+---------------+-------------
1            | 삼성           | SS           | SS            | 1982
2            | 롯데           | LOT          | LOT           | 1982
3            | LG             | LG           | LG            | 1982
4            | 두산           | OB           | OB            | 1982
5            | KIA            | KIA          | HT            | 1982
6            | 키움           | WO           | WO            | 2008
7            | 한화           | HH           | HH            | 1986
8            | SSG            | SSG          | SK            | 2000
9            | NC             | NC           | NC            | 2013
10           | KT             | KT           | KT            | 2015
```

**Status**: ✅ All data correct, metadata_json and web_url populated

### teams (44 records)

**Structure**: ✅ Columns exist (franchise_id, is_active, aliases)

**Data Issues**:
- ❌ All 44 teams have is_active=true (should be only 10)
- ❌ aliases column is empty for all teams
- ⚠️ 10 current teams + 12 historical teams + 22 international teams

**Team Distribution by Franchise**:
```
franchise_id | teams | should_be_active
-------------+-------+------------------
1 (삼성)     | SS    | SS only
2 (롯데)     | LOT   | LOT only
3 (LG)       | LG, MBC | LG only
4 (두산)     | OB, DO  | OB only (current_code)
5 (KIA)      | KIA, HT | KIA only
6 (키움)     | WO, TP, SM, KI, HU, CB, NX | WO only
7 (한화)     | HH, BE | HH only
8 (SSG)      | SSG, SK, SL | SSG only
9 (NC)       | NC    | NC only
10 (KT)      | KT    | KT only
NULL         | 22 international + 5 special teams | ALL false
```

### team_history (309 records)
```
- Seasons: 1982-2024 (43 years)
- Franchise mapping: ✅ All records have franchise_id
- Historical codes: ✅ Properly mapped (MBC→LG, HT→KIA, etc.)
```

**Status**: ✅ High-quality historical data, no issues

---

## Optimization Strategy

### Phase 1: Fix is_active Flags (CRITICAL - DO FIRST)

**Problem**: All 44 teams incorrectly marked as active
**Impact**: Frontend shows historical teams, API returns wrong data
**Priority**: P0 - Must fix immediately

**Solution**: Update is_active based on franchise current_code

```sql
-- Reset all to inactive
UPDATE teams SET is_active = false;

-- Activate only current franchise codes
UPDATE teams
SET is_active = true
WHERE team_id IN (
    SELECT current_code
    FROM team_franchises
    WHERE current_code IS NOT NULL
);
-- Expected: 10 rows updated
```

**Verification**:
```sql
SELECT
    COUNT(*) as total,
    COUNT(CASE WHEN is_active = true THEN 1 END) as active,
    COUNT(CASE WHEN is_active = false THEN 1 END) as inactive
FROM teams;
-- Expected: total=44, active=10, inactive=34
```

---

### Phase 2: Update Spring Boot Entities

**File**: `/Users/mac/project/KBO_platform/backend_bega/BEGA_PROJECT/src/main/java/com/example/demo/entity/TeamEntity.java`

**Changes Required**:

1. Add `franchise_id` with FK relationship
2. Add `is_active` boolean field
3. Add `aliases` String array field

**Implementation**:

```java
@Entity
@Table(name = "teams", schema = "public")
@Getter
@Setter
@NoArgsConstructor
public class TeamEntity {

    @Id
    @Column(name = "team_id", nullable = false, length = 10)
    private String teamId;

    @Column(name = "team_name", nullable = false, length = 50)
    private String teamName;

    @Column(name = "team_short_name", nullable = false, length = 20)
    private String teamShortName;

    @Column(name = "city", nullable = false, length = 30)
    private String city;

    @Column(name = "founded_year")
    private Integer foundedYear;

    @Column(name = "stadium_name", length = 50)
    private String stadiumName;

    @Column(name = "color")
    private String color;

    // NEW FIELDS
    @Column(name = "franchise_id")
    private Integer franchiseId;

    @Column(name = "is_active")
    private Boolean isActive;

    @Type(JsonBinaryType.class)
    @Column(name = "aliases", columnDefinition = "text[]")
    private String[] aliases;
}
```

**Note**: PostgreSQL text[] arrays require special handling with Hibernate. Consider using:
- `@Type(JsonBinaryType.class)` with hibernate-types library, OR
- `@Column(columnDefinition = "text[]")` with converter, OR
- Simplified string parsing in service layer

---

### Phase 3: Update Repository Methods

**File**: `/Users/mac/project/KBO_platform/backend_bega/BEGA_PROJECT/src/main/java/com/example/demo/repo/TeamRepository.java`

**Add Query Methods**:

```java
@Repository
public interface TeamRepository extends JpaRepository<TeamEntity, String> {
    Optional<TeamEntity> findByTeamId(String teamId);

    // NEW: Get only active KBO teams
    List<TeamEntity> findByIsActiveTrue();

    // NEW: Get teams by franchise
    List<TeamEntity> findByFranchiseId(Integer franchiseId);

    // NEW: Get teams by franchise including historical
    @Query("SELECT t FROM TeamEntity t WHERE t.franchiseId = :franchiseId ORDER BY t.isActive DESC, t.foundedYear")
    List<TeamEntity> findByFranchiseIdWithHistory(@Param("franchiseId") Integer franchiseId);

    // NEW: Search by team ID or aliases
    @Query("SELECT t FROM TeamEntity t WHERE t.teamId = :code OR :code = ANY(t.aliases)")
    Optional<TeamEntity> findByTeamIdOrAlias(@Param("code") String code);
}
```

---

### Phase 4: Populate aliases Column (OPTIONAL)

**Purpose**: Enable flexible team code lookups (e.g., "두산" → finds OB team)

**Implementation**:

```sql
-- Historical team aliases
UPDATE teams SET aliases = ARRAY['두산', 'DO'] WHERE team_id = 'OB';
UPDATE teams SET aliases = ARRAY['해태', 'HT'] WHERE team_id = 'KIA';
UPDATE teams SET aliases = ARRAY['기아', 'KIA'] WHERE team_id = 'HT';
UPDATE teams SET aliases = ARRAY['MBC', 'LG'] WHERE team_id = 'MBC';
UPDATE teams SET aliases = ARRAY['LG'] WHERE team_id = 'LG' AND aliases IS NULL;
UPDATE teams SET aliases = ARRAY['빙그레', 'BE'] WHERE team_id = 'HH';
UPDATE teams SET aliases = ARRAY['한화', 'HH'] WHERE team_id = 'BE';
UPDATE teams SET aliases = ARRAY['SK', 'SK 와이번스', 'SL'] WHERE team_id = 'SSG';
UPDATE teams SET aliases = ARRAY['SSG', '랜더스'] WHERE team_id = 'SK';
UPDATE teams SET aliases = ARRAY['SSG', '랜더스'] WHERE team_id = 'SL';

-- 키움 franchise historical teams
UPDATE teams SET aliases = ARRAY['청보', 'CB'] WHERE team_id = 'HU';
UPDATE teams SET aliases = ARRAY['태평양', 'TP'] WHERE team_id = 'CB';
UPDATE teams SET aliases = ARRAY['현대', 'HU'] WHERE team_id = 'KI';
UPDATE teams SET aliases = ARRAY['히어로즈', 'KI'] WHERE team_id = 'WO';
UPDATE teams SET aliases = ARRAY['넥센', 'NX'] WHERE team_id IN ('SM', 'TP');
```

**Benefit**: Supports queries like:
```java
teamRepository.findByTeamIdOrAlias("두산") // Returns OB team
teamRepository.findByTeamIdOrAlias("해태") // Returns KIA team
```

---

### Phase 5: Handle Edge Cases

#### Issue A: LOT vs LT Discrepancy

**Current State**:
- Franchise current_code = "LOT"
- Team team_id = ? (need to verify)

**Investigation Needed**:
```sql
SELECT team_id, team_name
FROM teams
WHERE franchise_id = 2;
```

**Resolution**:
- If team_id = "LT", update to "LOT" for consistency
- Update any foreign key references in other tables (users.favorite_team, etc.)

#### Issue B: TP Team Handling

**Current State**: TP exists in teams table (franchise_id = 6, 키움)

**Options**:
1. **Keep TP** - It's a valid historical team (태평양 돌고래, 1996-1999)
2. **Remove TP** - If duplicate or not needed

**Recommendation**: Keep TP with is_active=false (it's historical data)

#### Issue C: International Teams

**Current State**: 22 international teams with franchise_id=NULL

**Strategy**:
```sql
-- Verify all international teams are inactive
UPDATE teams
SET is_active = false
WHERE franchise_id IS NULL;
```

**Categories**:
- National teams: AU, CA, CN, CU, JP, KR, etc.
- Special: ALLSTAR1, ALLSTAR2, EA, WE, OT

**Recommendation**: Keep all for historical completeness (국제대회, 올스타전 기록)

---

## Implementation Checklist

### Database Changes

- [ ] Run analysis script: `analyze_team_data.sql`
- [ ] Backup current teams table
- [ ] Execute Phase 1: Fix is_active flags
- [ ] Verify: SELECT COUNT(*) WHERE is_active=true (should be 10)
- [ ] Execute Phase 4: Populate aliases (optional)
- [ ] Execute Phase 5: Handle edge cases (LOT/LT, TP)

### Spring Boot Changes

- [ ] Update TeamEntity.java with new fields
- [ ] Handle PostgreSQL text[] array mapping
- [ ] Update TeamRepository with new query methods
- [ ] Update Team.java (cheerboard domain) if needed
- [ ] Test compilation: `./gradlew build`

### Testing

- [ ] Unit tests: TeamRepository queries
- [ ] Integration test: findByIsActiveTrue() returns 10 teams
- [ ] Integration test: findByTeamIdOrAlias() with aliases
- [ ] API test: GET /api/teams returns only active teams
- [ ] Verify frontend shows correct 10 teams

### Documentation

- [ ] Update API documentation with is_active filter
- [ ] Document aliases usage in team search
- [ ] Update database schema documentation

---

## Risk Assessment

| Risk | Severity | Mitigation |
|------|----------|------------|
| Foreign key violations from LOT/LT change | HIGH | Check all FKs first: users.favorite_team, predictions, etc. |
| PostgreSQL array type incompatibility | MEDIUM | Test on dev database first, consider string parsing fallback |
| Historical data loss | LOW | No deletions planned, only status updates |
| Frontend breaking changes | MEDIUM | Ensure API contract maintained, add is_active filter |

---

## Rollback Plan

If issues occur, rollback is simple:

```sql
-- Revert is_active changes
UPDATE teams SET is_active = true;

-- Clear aliases if causing issues
UPDATE teams SET aliases = NULL;
```

Then revert Java entity changes via git:
```bash
cd /Users/mac/project/KBO_platform/backend_bega/BEGA_PROJECT
git checkout src/main/java/com/example/demo/entity/TeamEntity.java
git checkout src/main/java/com/example/demo/repo/TeamRepository.java
```

---

## Success Criteria

1. **Database**:
   - ✅ 10 teams with is_active=true
   - ✅ 34 teams with is_active=false
   - ✅ All franchises have exactly 1 active team
   - ✅ aliases populated for historical teams (optional)

2. **Spring Boot**:
   - ✅ TeamEntity compiles without errors
   - ✅ JPA queries return correct active teams
   - ✅ All unit tests pass

3. **API**:
   - ✅ GET /api/teams returns 10 active teams only
   - ✅ Team lookup by alias works (optional)
   - ✅ Frontend displays correct current teams

4. **Data Integrity**:
   - ✅ No orphaned foreign keys
   - ✅ team_history references remain valid
   - ✅ User favorite teams still resolve correctly

---

## Timeline Estimate

- Phase 1 (Database): 30 minutes
- Phase 2 (Entity): 1 hour
- Phase 3 (Repository): 30 minutes
- Phase 4 (Aliases): 1 hour (optional)
- Phase 5 (Edge Cases): 1 hour
- Testing: 2 hours

**Total**: 4-6 hours for complete implementation and testing

---

## Questions for User

Before proceeding with implementation, please confirm:

### 1. Historical Teams Strategy
**Question**: Should we keep all historical teams in the teams table, or move them to team_history only?

**Option A** (Recommended): Keep all in teams table
- Pros: Maintains referential integrity, supports historical queries
- Cons: Larger teams table (44 vs 32 records)

**Option B**: Remove historical teams, keep only current + international
- Pros: Simpler teams table
- Cons: May break existing foreign keys, loses direct team entity access

### 2. LOT vs LT Code
**Question**: Verify the correct team_id for 롯데
- Franchise current_code says "LOT"
- Need to check if teams.team_id is "LT" or "LOT"
- If "LT", should we migrate to "LOT"?

### 3. TP Team
**Question**: TP (태평양) is back in the database
- Should we keep it (is_active=false) as historical team?
- Or remove it again?

### 4. aliases Priority
**Question**: How important is the aliases feature?
- High priority: Implement now with Phase 4
- Low priority: Skip for now, add later if needed

### 5. PostgreSQL Array Handling
**Question**: Preferred approach for text[] aliases column?
- Option A: Use hibernate-types library (requires dependency)
- Option B: Custom converter with String[] → Array conversion
- Option C: Simplified: Store as comma-separated string, parse in service layer

---

## Next Steps

Please review this plan and answer the questions above. Once confirmed, I'll:

1. Execute Phase 1 SQL fixes
2. Update Spring Boot entities and repositories
3. Run comprehensive tests
4. Provide migration script for production

**Ready to proceed?**
