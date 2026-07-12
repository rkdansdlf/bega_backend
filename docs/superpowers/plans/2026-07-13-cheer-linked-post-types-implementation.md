# Cheer Linked Post Types Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add privacy-safe, domain-linked `CHECKIN` and `RECRUITMENT` cheer posts created only from eligible Diary and Mate screens, with Oracle/PostgreSQL parity and current-state rendering.

**Architecture:** Store nullable `diary_id` and `party_id` foreign keys on `cheer_post`, enforce one active linked post per source in each database, and resolve linked source data in a cheer-owned backend service using page-level bulk queries. Keep the frontend contract generated from OpenAPI, use a strict four-value post-type allowlist, and load linked rendering through a cheer-feature lazy boundary whose first change is immediately checked against every bundle budget.

**Tech Stack:** Java 21, Spring Boot, Spring Data JPA, Flyway, Oracle, PostgreSQL, JUnit 5, React, TypeScript, React Query, Vite/Rollup, Node test runner, Cypress.

## Global Constraints

- Never add crawling, scraping, web-search repair, an external baseball API, or synthesized baseball facts.
- Missing or inconsistent internal baseball fields must surface `MANUAL_BASEBALL_DATA_REQUIRED` with the exact entity, ID, fields, and internal/manual import expectation.
- Preserve every unrelated dirty-worktree change in `bega_backend` and `bega_frontend`; stage only files named by the current task.
- Before editing a task file, run `git diff -- <file>` and merge around any existing change. If a machine-generated file already has overlapping uncommitted work that cannot be isolated safely, stop that task and resolve ownership before regeneration.
- Do not change the `전체`, `인기`, `팔로우`, or `라이브` feed segments.
- Do not expose diary memo, photos, ticket evidence, seat details, mood, or game result.
- Do not expose Mate members, reservation numbers, ticket images, or private application data.
- `CHECKIN` creation requires an owned `ATTENDED` diary with `ticketVerified=true`.
- `RECRUITMENT` creation requires a party hosted by the requester with `status=PENDING`.
- A non-deleted linked post is unique per source; soft deletion releases the source for re-share.
- Linked post type and source IDs are immutable after creation.
- Regenerate OpenAPI types; do not use handwritten casts or shadow types to bypass stale generated types.
- Run `npm run build` immediately after the first linked renderer extraction and inspect both bundle reports for all routes, not only cheer routes.
- At implementation start, invoke `test-driven-development`; every behavior task must show the red test before the minimal green implementation.
- Before claiming completion, use `kbo-release-verification` and `verification-before-completion` and report every command and result.

---

### Task 1: Add the cross-database linked-source schema

**Files:**
- Create after the version preflight: `BEGA_PROJECT/src/main/resources/db/migration/V167__add_cheer_linked_post_types.sql`
- Create after the version preflight: `BEGA_PROJECT/src/main/resources/db/migration_postgresql/V173__add_cheer_linked_post_types.sql`
- Create: `BEGA_PROJECT/src/test/java/com/example/cheerboard/CheerLinkedPostMigrationSqlTest.java`

**Interfaces:**
- Consumes: existing `cheer_post`, `bega_diary`, and `parties` tables; Oracle `deleted NUMBER(1)` and PostgreSQL `deleted BOOLEAN`.
- Produces: nullable `diary_id` and `party_id`, `ON DELETE SET NULL` foreign keys, ordinary FK indexes, active-source unique indexes, and type/reference check constraints.

- [ ] **Step 1: Reconfirm the migration versions before creating files**

Run from `bega_backend`:

```bash
rg --files BEGA_PROJECT/src/main/resources/db/migration | sed 's#.*/##' | sort -V | tail -5
rg --files BEGA_PROJECT/src/main/resources/db/migration_postgresql | sed 's#.*/##' | sort -V | tail -5
```

Expected at plan-writing time: Oracle ends at `V166__add_mate_party_list_participant_sort_indexes.sql` and PostgreSQL ends at `V172__add_mate_party_list_participant_sort_indexes.sql`. If either output has advanced, stop before creating files, replace `V167` or `V173` in this task and its test with the actual next unused version, then continue.

- [ ] **Step 2: Write the failing migration contract test**

Create `CheerLinkedPostMigrationSqlTest.java` with two tests that load the exact selected migration resources and assert these tokens:

```java
assertThat(postgresSql)
        .contains("diary_id bigint")
        .contains("party_id bigint")
        .contains("references bega_diary(id) on delete set null")
        .contains("references parties(id) on delete set null")
        .contains("uq_cheer_post_active_diary")
        .contains("where diary_id is not null and deleted = false")
        .contains("uq_cheer_post_active_party")
        .contains("where party_id is not null and deleted = false")
        .contains("ck_cheer_post_link_type");

assertThat(oracleSql)
        .contains("diary_id number(19)")
        .contains("party_id number(19)")
        .contains("on delete set null")
        .contains("uq_cheer_post_active_diary")
        .contains("when nvl(deleted, 0) = 0")
        .contains("uq_cheer_post_active_party")
        .contains("ck_cheer_post_link_type");
```

- [ ] **Step 3: Run the test and verify the resources are missing**

Run:

```bash
cd BEGA_PROJECT
./gradlew test --tests "*CheerLinkedPostMigrationSqlTest*"
```

Expected: FAIL because the two migration resources do not exist.

- [ ] **Step 4: Implement the PostgreSQL migration**

Use idempotent `ADD COLUMN IF NOT EXISTS`, guarded constraint blocks, and these exact index predicates:

```sql
ALTER TABLE cheer_post ADD COLUMN IF NOT EXISTS diary_id BIGINT;
ALTER TABLE cheer_post ADD COLUMN IF NOT EXISTS party_id BIGINT;

CREATE INDEX IF NOT EXISTS idx_cheer_post_diary ON cheer_post (diary_id);
CREATE INDEX IF NOT EXISTS idx_cheer_post_party ON cheer_post (party_id);

CREATE UNIQUE INDEX IF NOT EXISTS uq_cheer_post_active_diary
    ON cheer_post (diary_id)
    WHERE diary_id IS NOT NULL AND deleted = FALSE;

CREATE UNIQUE INDEX IF NOT EXISTS uq_cheer_post_active_party
    ON cheer_post (party_id)
    WHERE party_id IS NOT NULL AND deleted = FALSE;
```

Add named foreign keys with `ON DELETE SET NULL`, then add `ck_cheer_post_link_type` with this expression:

```sql
(posttype IN ('NORMAL', 'NOTICE') AND diary_id IS NULL AND party_id IS NULL)
OR (posttype = 'CHECKIN' AND party_id IS NULL)
OR (posttype = 'RECRUITMENT' AND diary_id IS NULL)
```

- [ ] **Step 5: Implement the Oracle migration**

Use the repository's `EXECUTE IMMEDIATE` plus named Oracle exception pattern. The core DDL must be:

```sql
ALTER TABLE cheer_post ADD (diary_id NUMBER(19));
ALTER TABLE cheer_post ADD (party_id NUMBER(19));
ALTER TABLE cheer_post ADD CONSTRAINT fk_cheer_post_diary
    FOREIGN KEY (diary_id) REFERENCES bega_diary(id) ON DELETE SET NULL;
ALTER TABLE cheer_post ADD CONSTRAINT fk_cheer_post_party
    FOREIGN KEY (party_id) REFERENCES parties(id) ON DELETE SET NULL;
CREATE INDEX idx_cheer_post_diary ON cheer_post(diary_id);
CREATE INDEX idx_cheer_post_party ON cheer_post(party_id);
```

Create function-based unique indexes using these expressions:

```sql
CASE WHEN NVL(deleted, 0) = 0 AND diary_id IS NOT NULL THEN diary_id END
CASE WHEN NVL(deleted, 0) = 0 AND party_id IS NOT NULL THEN party_id END
```

Add the same `ck_cheer_post_link_type` logic as PostgreSQL. Allow linked IDs to be null after the parent is deleted.

- [ ] **Step 6: Run schema tests and the migration gate**

Run:

```bash
./gradlew test --tests "*CheerLinkedPostMigrationSqlTest*" --tests "*FlywayMigrationVersionUniquenessTest*"
./gradlew migrationSafetyCheck
```

Expected: PASS; no duplicate migration version in either dialect.

- [ ] **Step 7: Commit only the schema task**

```bash
git add BEGA_PROJECT/src/main/resources/db/migration/V167__add_cheer_linked_post_types.sql BEGA_PROJECT/src/main/resources/db/migration_postgresql/V173__add_cheer_linked_post_types.sql BEGA_PROJECT/src/test/java/com/example/cheerboard/CheerLinkedPostMigrationSqlTest.java
git commit -m "feat: add linked cheer post schema"
```

Use the version-adjusted paths if Step 1 selected different numbers.

---

### Task 2: Define the backend post type and DTO contract

**Files:**
- Modify: `BEGA_PROJECT/src/main/java/com/example/cheerboard/domain/PostType.java`
- Modify: `BEGA_PROJECT/src/main/java/com/example/cheerboard/domain/CheerPost.java`
- Modify: `BEGA_PROJECT/src/main/java/com/example/cheerboard/dto/CreatePostReq.java`
- Create: `BEGA_PROJECT/src/main/java/com/example/cheerboard/dto/CheckinLinkedContentRes.java`
- Create: `BEGA_PROJECT/src/main/java/com/example/cheerboard/dto/RecruitmentLinkedContentRes.java`
- Create: `BEGA_PROJECT/src/main/java/com/example/cheerboard/dto/LinkedContentKind.java`
- Create: `BEGA_PROJECT/src/main/java/com/example/cheerboard/dto/LinkedContentUnavailableReason.java`
- Create: `BEGA_PROJECT/src/main/java/com/example/cheerboard/dto/LinkedContentRes.java`
- Create: `BEGA_PROJECT/src/main/java/com/example/cheerboard/dto/LinkedPostLookupRes.java`
- Modify: `BEGA_PROJECT/src/main/java/com/example/cheerboard/dto/PostSummaryRes.java`
- Modify: `BEGA_PROJECT/src/main/java/com/example/cheerboard/dto/PostDetailRes.java`
- Modify: `BEGA_PROJECT/src/main/java/com/example/cheerboard/dto/PostLightweightSummaryRes.java`
- Modify: `BEGA_PROJECT/src/main/java/com/example/cheerboard/dto/EmbeddedPostDto.java`
- Modify: `BEGA_PROJECT/src/test/java/com/example/cheerboard/dto/CheerDtoSerializationTest.java`

**Interfaces:**
- Consumes: Task 1 columns.
- Produces: `PostType.CHECKIN`, `PostType.RECRUITMENT`, scalar source IDs, and the OpenAPI-visible linked-content records used by every later task.

- [ ] **Step 1: Add failing DTO serialization tests**

Add tests that construct both available variants and assert the exact JSON boundary:

```java
JsonNode checkinNode = objectMapper.readTree(checkinJson);
assertThat(checkinNode.path("kind").asText()).isEqualTo("CHECKIN");
assertThat(checkinNode.path("available").asBoolean()).isTrue();
assertThat(checkinNode.path("checkin").path("gameDate").asText()).isEqualTo("2026-07-13");
assertThat(checkinJson).doesNotContain("memo", "photo", "ticket", "seatRow", "diaryId");

JsonNode recruitmentNode = objectMapper.readTree(recruitmentJson);
assertThat(recruitmentNode.path("kind").asText()).isEqualTo("RECRUITMENT");
assertThat(recruitmentNode.path("recruitment").path("partyId").asLong()).isEqualTo(44L);
assertThat(recruitmentNode.path("recruitment").path("recruiting").asBoolean()).isTrue();
assertThat(recruitmentJson).doesNotContain("members", "reservationNumber", "ticketImageUrl");
```

- [ ] **Step 2: Run the DTO test and verify missing types**

Run:

```bash
cd BEGA_PROJECT
./gradlew test --tests "*CheerDtoSerializationTest*"
```

Expected: compile failure because linked DTOs and enum values do not exist.

- [ ] **Step 3: Extend the entity and request contract**

Add enum values and scalar mappings:

```java
public enum PostType {
    NORMAL,
    NOTICE,
    CHECKIN,
    RECRUITMENT
}

@Column(name = "diary_id")
private Long diaryId;

@Column(name = "party_id")
private Long partyId;
```

Append `Long diaryId, Long partyId` to `CreatePostReq`. Update its compatibility constructor so ordinary callers still supply null for both fields.

- [ ] **Step 4: Create the linked response records**

Use these signatures:

```java
public record CheckinLinkedContentRes(
        LocalDate gameDate,
        String homeTeam,
        String awayTeam,
        String cheeringTeam,
        String stadium,
        boolean verified) {}

public record RecruitmentLinkedContentRes(
        Long partyId,
        LocalDate gameDate,
        LocalTime gameTime,
        String homeTeam,
        String awayTeam,
        String stadium,
        String section,
        Integer currentParticipants,
        Integer maxParticipants,
        String status,
        boolean recruiting,
        String description,
        Integer price,
        Integer ticketPrice,
        Integer reservationDepositAmount) {}

public enum LinkedContentKind { CHECKIN, RECRUITMENT }

public enum LinkedContentUnavailableReason {
    SOURCE_MISSING,
    SOURCE_INELIGIBLE,
    MANUAL_BASEBALL_DATA_REQUIRED
}

public record LinkedContentRes(
        LinkedContentKind kind,
        boolean available,
        LinkedContentUnavailableReason unavailableReason,
        CheckinLinkedContentRes checkin,
        RecruitmentLinkedContentRes recruitment) {}

public record LinkedPostLookupRes(Long postId, LinkedContentRes preview) {}
```

Add factories `availableCheckin`, `availableRecruitment`, and `unavailable(kind, reason)` so callers cannot construct mixed variants.

- [ ] **Step 5: Add linked content to every post DTO**

Append `LinkedContentRes linkedContent` to `PostSummaryRes` and `PostDetailRes`. Add `String postType` and `LinkedContentRes linkedContent` to `PostLightweightSummaryRes`. Add `String postType` and `LinkedContentRes linkedContent` to `EmbeddedPostDto`.

Update every local factory in those records to pass the correct post type and `null` linked content for legacy callers. Do not remove compatibility factories used by existing tests.

- [ ] **Step 6: Run DTO and compile tests**

Run:

```bash
./gradlew test --tests "*CheerDtoSerializationTest*" --tests "*PostDtoMapperTest*"
./gradlew compileJava
```

Expected: PASS after updating constructor call sites to supply the new trailing fields.

- [ ] **Step 7: Commit the backend contract**

```bash
git add BEGA_PROJECT/src/main/java/com/example/cheerboard/domain/PostType.java BEGA_PROJECT/src/main/java/com/example/cheerboard/domain/CheerPost.java BEGA_PROJECT/src/main/java/com/example/cheerboard/dto/CreatePostReq.java BEGA_PROJECT/src/main/java/com/example/cheerboard/dto/CheckinLinkedContentRes.java BEGA_PROJECT/src/main/java/com/example/cheerboard/dto/RecruitmentLinkedContentRes.java BEGA_PROJECT/src/main/java/com/example/cheerboard/dto/LinkedContentKind.java BEGA_PROJECT/src/main/java/com/example/cheerboard/dto/LinkedContentUnavailableReason.java BEGA_PROJECT/src/main/java/com/example/cheerboard/dto/LinkedContentRes.java BEGA_PROJECT/src/main/java/com/example/cheerboard/dto/LinkedPostLookupRes.java BEGA_PROJECT/src/main/java/com/example/cheerboard/dto/PostSummaryRes.java BEGA_PROJECT/src/main/java/com/example/cheerboard/dto/PostDetailRes.java BEGA_PROJECT/src/main/java/com/example/cheerboard/dto/PostLightweightSummaryRes.java BEGA_PROJECT/src/main/java/com/example/cheerboard/dto/EmbeddedPostDto.java BEGA_PROJECT/src/test/java/com/example/cheerboard/dto/CheerDtoSerializationTest.java
git commit -m "feat: define linked cheer post contract"
```

---

### Task 3: Implement linked-source eligibility and bulk resolution

**Files:**
- Create: `BEGA_PROJECT/src/main/java/com/example/cheerboard/service/CheerLinkedPostService.java`
- Modify: `BEGA_PROJECT/src/main/java/com/example/BegaDiary/Repository/BegaDiaryRepository.java`
- Modify: `BEGA_PROJECT/src/main/java/com/example/mate/repository/PartyRepository.java`
- Modify: `BEGA_PROJECT/src/main/java/com/example/cheerboard/repo/CheerPostRepo.java`
- Create: `BEGA_PROJECT/src/test/java/com/example/cheerboard/service/CheerLinkedPostServiceTest.java`

**Interfaces:**
- Consumes: `LinkedContentRes`, `LinkedPostLookupRes`, `BegaDiary`, `Party`, and scalar IDs from Task 2.
- Produces: `validateCreate(PostType, CreatePostReq, UserEntity)`, `lookup`, `findActivePost`, `resolveOne`, and `resolveForPosts` for Tasks 4 and 5.

- [ ] **Step 1: Write failing policy tests**

Cover these exact cases with Mockito fixtures:

```java
assertThat(service.validateCreate(PostType.CHECKIN, checkinRequest(diaryId), owner).diaryId()).isEqualTo(diaryId);
assertThatThrownBy(() -> service.validateCreate(PostType.CHECKIN, checkinRequest(diaryId), stranger))
        .isInstanceOf(NotFoundBusinessException.class)
        .hasMessageContaining("다이어리");
assertThatThrownBy(() -> service.validateCreate(PostType.CHECKIN, checkinRequest(scheduledDiaryId), owner))
        .isInstanceOf(ConflictBusinessException.class)
        .extracting("code").isEqualTo("CHECKIN_NOT_SHAREABLE");
assertThatThrownBy(() -> service.validateCreate(PostType.RECRUITMENT, recruitmentRequest(partyId), nonHost))
        .extracting("code").isEqualTo("PARTY_HOST_REQUIRED");
assertThatThrownBy(() -> service.validateCreate(PostType.RECRUITMENT, recruitmentRequest(matchedPartyId), host))
        .extracting("code").isEqualTo("PARTY_NOT_RECRUITING");
```

Also assert that a diary with missing matchup or stadium throws `ManualBaseballDataRequiredException` and that its `ManualBaseballDataRequest.missingItems` names the exact missing fields.

- [ ] **Step 2: Run the service test and verify the service is absent**

Run:

```bash
cd BEGA_PROJECT
./gradlew test --tests "*CheerLinkedPostServiceTest*"
```

Expected: compile failure because `CheerLinkedPostService` does not exist.

- [ ] **Step 3: Add bulk repository methods**

Add these explicit methods:

```java
@EntityGraph(attributePaths = {"user", "game"})
@Query("SELECT d FROM BegaDiary d WHERE d.id IN :ids")
List<BegaDiary> findAllByIdInWithOwnerAndGame(@Param("ids") Collection<Long> ids);

List<Party> findByIdIn(Collection<Long> ids);

@EntityGraph(attributePaths = {"author", "team"})
Optional<CheerPost> findFirstByDiaryIdAndDeletedFalse(Long diaryId);

@EntityGraph(attributePaths = {"author", "team"})
Optional<CheerPost> findFirstByPartyIdAndDeletedFalse(Long partyId);
```

- [ ] **Step 4: Implement request validation and manual-data errors**

Create `CheerLinkedPostService.ValidatedTarget(PostType postType, Long diaryId, Long partyId)`. Task 4 parses the request into an effective type first, preserving the existing non-admin `NOTICE -> NORMAL` behavior. `validateCreate` receives that effective type:

```java
public ValidatedTarget validateCreate(PostType effectiveType, CreatePostReq req, UserEntity actor) {
    return switch (effectiveType) {
        case CHECKIN -> validateCheckin(req, actor);
        case RECRUITMENT -> validateRecruitment(req, actor);
        case NORMAL, NOTICE -> validateUnlinked(req);
    };
}
```

For linked types, reject external share modes and any source metadata. Build `ManualBaseballDataRequiredException` with:

```java
new ManualBaseballDataRequest(
        "cheer-linked:" + sourceType + ":" + sourceId,
        missingItems,
        "내부 game/bega_diary/parties 데이터 또는 운영자 제공 수동 데이터가 필요합니다.",
        true)
```

Never call HTTP, browser, search, crawler, or public baseball API code.

Use the existing common exception hierarchy with these exact HTTP/code pairs: `400 INVALID_LINKED_POST_REQUEST`, `404 DIARY_NOT_FOUND`, `409 CHECKIN_NOT_SHAREABLE`, `404 PARTY_NOT_FOUND`, `403 PARTY_HOST_REQUIRED`, and `409 PARTY_NOT_RECRUITING`.

- [ ] **Step 5: Implement lookup and current-state mapping**

`lookup(diaryId, partyId, actor)` must require exactly one ID, validate ownership and eligibility, load the active cheer post ID, and return a safe preview.

`resolveOne` and `resolveForPosts` must map:

```text
missing FK/source -> available=false, SOURCE_MISSING
diary not ATTENDED or no longer verified -> available=false, SOURCE_INELIGIBLE
party FAILED -> available=false, SOURCE_INELIGIBLE
party PENDING -> available=true, recruiting=true
party MATCHED/SELLING/SOLD/CHECKED_IN/COMPLETED -> available=true, recruiting=false
missing baseball fields on reads -> available=false, MANUAL_BASEBALL_DATA_REQUIRED
```

Return a `Map<Long, LinkedContentRes>` keyed by cheer post ID. Include linked embedded originals supplied in the input collection.

- [ ] **Step 6: Run policy tests**

Run:

```bash
./gradlew test --tests "*CheerLinkedPostServiceTest*"
```

Expected: PASS for ownership, status, privacy, unavailable-state, and manual-data cases.

- [ ] **Step 7: Commit the linked-source service**

```bash
git add BEGA_PROJECT/src/main/java/com/example/cheerboard/service/CheerLinkedPostService.java BEGA_PROJECT/src/main/java/com/example/BegaDiary/Repository/BegaDiaryRepository.java BEGA_PROJECT/src/main/java/com/example/mate/repository/PartyRepository.java BEGA_PROJECT/src/main/java/com/example/cheerboard/repo/CheerPostRepo.java BEGA_PROJECT/src/test/java/com/example/cheerboard/service/CheerLinkedPostServiceTest.java
git commit -m "feat: validate linked cheer post sources"
```

---

### Task 4: Add idempotent creation, lookup HTTP API, and rate limiting

**Files:**
- Create: `BEGA_PROJECT/src/main/java/com/example/cheerboard/service/CheerPostCreationOutcome.java`
- Create: `BEGA_PROJECT/src/main/java/com/example/cheerboard/service/CheerPostCreationResult.java`
- Modify: `BEGA_PROJECT/src/main/java/com/example/cheerboard/service/CheerPostService.java`
- Modify: `BEGA_PROJECT/src/main/java/com/example/cheerboard/service/CheerService.java`
- Modify: `BEGA_PROJECT/src/main/java/com/example/cheerboard/controller/CheerController.java`
- Modify: `BEGA_PROJECT/src/test/java/com/example/cheerboard/service/CheerPostServiceTest.java`
- Modify: `BEGA_PROJECT/src/test/java/com/example/cheerboard/service/CheerServiceTest.java`
- Modify: `BEGA_PROJECT/src/test/java/com/example/cheerboard/controller/CheerControllerTest.java`
- Modify: `BEGA_PROJECT/src/test/java/com/example/cheerboard/integration/CheerRateLimitIntegrationTest.java`
- Modify: `BEGA_PROJECT/src/test/java/com/example/cheerboard/integration/CheerEdgeCaseIntegrationTest.java`
- Modify: `BEGA_PROJECT/src/test/java/com/example/cheerboard/integration/CheerConcurrencyIntegrationTest.java`

**Interfaces:**
- Consumes: Task 3 `validateCreate`, `lookup`, and active-post repository methods.
- Produces: `GET /api/cheer/posts/linked`, idempotent `POST /api/cheer/posts`, `201` for new posts, and `200` for an existing active linked post.

- [ ] **Step 1: Write failing creation and controller tests**

Add tests for:

```java
assertThat(service.createPost(validCheckinReq, owner).created()).isTrue();
assertThat(service.createPost(validCheckinReq, owner).post().getDiaryId()).isEqualTo(diaryId);
assertThat(service.createPost(validCheckinReq, owner).post().getPartyId()).isNull();

assertThat(controller.create(validRecruitmentReq).getStatusCode()).isEqualTo(HttpStatus.CREATED);
assertThat(controller.create(repeatedRecruitmentReq).getStatusCode()).isEqualTo(HttpStatus.OK);
```

Add a raw JSON update test containing `postType`, `diaryId`, and `partyId`; assert the persisted entity retains its original type and IDs after the update response.

Add a soft-delete/re-share test and a two-thread linked-create test. The concurrent test must assert both callers receive the same post ID and `postRepo.findFirstByDiaryIdAndDeletedFalse(diaryId)` identifies exactly one active row.

- [ ] **Step 2: Run focused tests and verify failure**

Run:

```bash
cd BEGA_PROJECT
./gradlew test --tests "*CheerPostServiceTest*" --tests "*CheerServiceTest*" --tests "*CheerControllerTest*" --tests "*CheerEdgeCaseIntegrationTest*"
```

Expected: compile or assertion failure because creation outcomes, lookup API, and source assignment are absent.

- [ ] **Step 3: Implement transactional creation outcomes**

Use these records:

```java
public record CheerPostCreationOutcome(CheerPost post, boolean created) {}
public record CheerPostCreationResult(PostDetailRes post, boolean created) {}
```

`CheerPostService.createPost` remains transactional, parses the effective type, validates the linked target through `validateCreate(effectiveType, req, actor)`, returns an existing active post before insertion, and sets only the validated scalar ID on a new entity. Let `DataIntegrityViolationException` escape the transactional service; do not query after a unique violation inside the failed transaction.

Remove the outer `@Transactional` from `CheerService.createPost`. Catch `DataIntegrityViolationException` only after the `CheerPostService` transaction has rolled back, reload the active source through the entity-graph repository method, and rethrow if no matching active post exists. This prevents PostgreSQL's aborted transaction from breaking duplicate recovery.

- [ ] **Step 4: Preserve existing type semantics and reject forged combinations**

Implement `determinePostType` with these rules:

```text
null/blank/NORMAL -> NORMAL
NOTICE from ROLE_ADMIN -> NOTICE
NOTICE from non-admin -> NORMAL (existing behavior)
CHECKIN -> CHECKIN
RECRUITMENT -> RECRUITMENT
anything else -> INVALID_LINKED_POST_REQUEST
```

For linked types, permit only absent or `INTERNAL_REPOST` share mode and reject all external metadata. Do not add linked fields to `UpdatePostReq`.

- [ ] **Step 5: Add the linked lookup and dynamic create status**

Add:

```java
@GetMapping("/posts/linked")
@PreAuthorize("isAuthenticated()")
@RateLimit(limit = 30, window = 60, key = "cheer:linked")
public LinkedPostLookupRes linked(
        @RequestParam(required = false) Long diaryId,
        @RequestParam(required = false) Long partyId) {
    return svc.lookupLinkedPost(diaryId, partyId);
}

@PostMapping("/posts")
@PreAuthorize("isAuthenticated()")
@RateLimit(limit = 5, window = 60)
public ResponseEntity<PostDetailRes> create(@Valid @RequestBody CreatePostReq req) {
    CheerPostCreationResult result = svc.createPost(req);
    return ResponseEntity.status(result.created() ? HttpStatus.CREATED : HttpStatus.OK)
            .body(result.post());
}
```

Remove the fixed `@ResponseStatus(HttpStatus.CREATED)` from create.

Add OpenAPI responses for both `200` and `201`, each with `PostDetailRes` content, so regenerated frontend types describe idempotent creation accurately.

- [ ] **Step 6: Add rate-limit coverage**

Extend `CheerRateLimitIntegrationTest` so the mocked `RateLimitService` returns true 30 times and false on the 31st linked lookup. Assert requests 1-30 are not `429`, request 31 is `429`, and the captured key contains `cheer:linked` but not the create endpoint bucket.

- [ ] **Step 7: Run creation, controller, immutability, and rate-limit tests**

Run:

```bash
./gradlew test --tests "*CheerPostServiceTest*" --tests "*CheerServiceTest*" --tests "*CheerControllerTest*" --tests "*CheerEdgeCaseIntegrationTest*" --tests "*CheerConcurrencyIntegrationTest*" --tests "*CheerRateLimitIntegrationTest*"
```

Expected: PASS, including duplicate recovery after the writer transaction rolls back.

- [ ] **Step 8: Commit the creation/API task**

```bash
git add BEGA_PROJECT/src/main/java/com/example/cheerboard/service/CheerPostCreationOutcome.java BEGA_PROJECT/src/main/java/com/example/cheerboard/service/CheerPostCreationResult.java BEGA_PROJECT/src/main/java/com/example/cheerboard/service/CheerPostService.java BEGA_PROJECT/src/main/java/com/example/cheerboard/service/CheerService.java BEGA_PROJECT/src/main/java/com/example/cheerboard/controller/CheerController.java BEGA_PROJECT/src/test/java/com/example/cheerboard/service/CheerPostServiceTest.java BEGA_PROJECT/src/test/java/com/example/cheerboard/service/CheerServiceTest.java BEGA_PROJECT/src/test/java/com/example/cheerboard/controller/CheerControllerTest.java BEGA_PROJECT/src/test/java/com/example/cheerboard/integration/CheerRateLimitIntegrationTest.java BEGA_PROJECT/src/test/java/com/example/cheerboard/integration/CheerEdgeCaseIntegrationTest.java BEGA_PROJECT/src/test/java/com/example/cheerboard/integration/CheerConcurrencyIntegrationTest.java
git commit -m "feat: create linked cheer posts idempotently"
```

---

### Task 5: Enrich every backend post response without N+1 queries

**Files:**
- Modify: `BEGA_PROJECT/src/main/java/com/example/cheerboard/service/PostDtoMapper.java`
- Modify: `BEGA_PROJECT/src/main/java/com/example/cheerboard/service/CheerFeedService.java`
- Modify: `BEGA_PROJECT/src/main/java/com/example/cheerboard/service/CheerService.java`
- Modify: `BEGA_PROJECT/src/test/java/com/example/cheerboard/service/PostDtoMapperTest.java`
- Modify: `BEGA_PROJECT/src/test/java/com/example/cheerboard/service/CheerFeedServiceTest.java`
- Modify: `BEGA_PROJECT/src/test/java/com/example/cheerboard/integration/CheerQueryCountIntegrationTest.java`

**Interfaces:**
- Consumes: Task 3 `resolveForPosts` and Task 2 response fields.
- Produces: linked content in summary, detail, lightweight, bookmarks, profile, hot, simple repost, and quote embedded DTOs.

- [ ] **Step 1: Write failing mapper and feed tests**

Add mapper assertions:

```java
assertThat(summary.linkedContent()).isEqualTo(checkinContent);
assertThat(detail.linkedContent()).isEqualTo(recruitmentContent);
assertThat(summary.originalPost().postType()).isEqualTo("CHECKIN");
assertThat(summary.originalPost().linkedContent()).isEqualTo(checkinContent);
```

Add a feed test with two linked top-level posts and one linked embedded original. Verify `resolveForPosts` is called once with all linked candidates.

- [ ] **Step 2: Run tests and verify linked fields are null**

Run:

```bash
cd BEGA_PROJECT
./gradlew test --tests "*PostDtoMapperTest*" --tests "*CheerFeedServiceTest*"
```

Expected: FAIL because mapper methods do not accept the linked-content map.

- [ ] **Step 3: Pass pre-resolved content into the mapper**

Add a final `Map<Long, LinkedContentRes> linkedContentByPostId` parameter to the fully-prefetched mapper overloads. Use:

```java
LinkedContentRes linkedContent = linkedContentByPostId.get(post.getId());
LinkedContentRes originalLinkedContent = original == null
        ? null
        : linkedContentByPostId.get(original.getId());
```

Set `postType` and `linkedContent` on `EmbeddedPostDto`; set linked content on summary, detail, and lightweight responses. Legacy overloads pass `Collections.emptyMap()`.

- [ ] **Step 4: Resolve linked sources once per response collection**

In `CheerFeedService.mapPostSummaries`, flatten top-level posts plus non-null repost originals and call `linkedPostService.resolveForPosts` synchronously once before optional Redis/media enrichments. Do not put this call behind the existing fallback future: an unexpected DB failure must fail the request rather than become a fake unavailable source.

In `listLightweight`, perform the same single bulk resolution. In `CheerService.reconstructPostDetailRes`, resolve the post and embedded original together before mapping.

- [ ] **Step 5: Add bounded-query integration coverage**

Extend `CheerQueryCountIntegrationTest` with actual Diary and Party repositories and linked fixtures. Measure a page with one linked source and a page with multiple linked sources; assert the larger page adds no per-post source queries:

```java
assertThat(manyLinkedQueryCount)
        .isLessThanOrEqualTo(oneLinkedQueryCount + 2L);
```

The two allowed statements are one bulk Diary query and one bulk Party query. Do not merely raise the global query budget without this size-comparison assertion.

- [ ] **Step 6: Run mapper, feed, and query-count tests**

Run:

```bash
./gradlew test --tests "*PostDtoMapperTest*" --tests "*CheerFeedServiceTest*" --tests "*CheerQueryCountIntegrationTest*"
```

Expected: PASS with linked content on top-level and embedded DTOs and bounded query growth.

- [ ] **Step 7: Commit backend read enrichment**

```bash
git add BEGA_PROJECT/src/main/java/com/example/cheerboard/service/PostDtoMapper.java BEGA_PROJECT/src/main/java/com/example/cheerboard/service/CheerFeedService.java BEGA_PROJECT/src/main/java/com/example/cheerboard/service/CheerService.java BEGA_PROJECT/src/test/java/com/example/cheerboard/service/PostDtoMapperTest.java BEGA_PROJECT/src/test/java/com/example/cheerboard/service/CheerFeedServiceTest.java BEGA_PROJECT/src/test/java/com/example/cheerboard/integration/CheerQueryCountIntegrationTest.java
git commit -m "feat: enrich cheer posts with linked content"
```

---

### Task 6: Regenerate OpenAPI and make frontend post types strict

**Files:**
- Modify through generation: `src/api/generated/openapi.ts`
- Modify: `src/api/cheerApi.ts`
- Modify: `src/api/cheerApi.test.ts`
- Modify: `src/utils/cheerSubmit.ts`
- Modify: `src/utils/cheerSubmit.test.ts`

**Interfaces:**
- Consumes: final backend `/v3/api-docs` from Tasks 2-5.
- Produces: generated `CreatePostReq`, `PostDetailRes`, `PostSummaryRes`, `EmbeddedPostDto`, strict `CheerPostType`, linked-content unions, `fetchLinkedPostTarget`, and linked create payloads.

- [ ] **Step 1: Record the current generated-file state**

Run from `bega_frontend`:

```bash
git diff -- src/api/generated/openapi.ts
git status --short src/api/generated/openapi.ts
```

Keep this output in the execution log. Do not restore or overwrite unrelated generated changes.

- [ ] **Step 2: Write failing normalization and request tests**

In `cheerApi.test.ts`, mock responses for all four types and assert exact preservation. Add:

```typescript
for (const postType of ['NORMAL', 'NOTICE', 'CHECKIN', 'RECRUITMENT'] as const) {
  assert.equal((await fetchPostDetail(1)).postType, postType);
}
await assert.rejects(() => fetchPostDetail(1), /UNKNOWN_CHEER_POST_TYPE/);
```

For creation, inspect the request body and assert `CHECKIN + diaryId` and `RECRUITMENT + partyId` are sent unchanged. Assert an empty or unknown present type rejects before `fetch` is called.

- [ ] **Step 3: Run tests and verify silent downgrade**

Run:

```bash
node --import tsx --test src/api/cheerApi.test.ts src/utils/cheerSubmit.test.ts
```

Expected: FAIL because current normalization maps every non-`NOTICE` value to `NORMAL` and submit types exclude linked posts.

- [ ] **Step 4: Regenerate from the updated backend schema**

Start the updated backend on the documented local port, then run:

```bash
OPENAPI_SCHEMA_URL=http://localhost:8080/v3/api-docs npm run api:types
OPENAPI_SCHEMA_URL=http://localhost:8080/v3/api-docs npm run api:types:check
```

Expected: both commands succeed, and generated schemas contain `diaryId`, `partyId`, `linkedContent`, `CHECKIN`, and `RECRUITMENT`. Audit the complete generated diff before continuing. If `src/api/generated/openapi.ts` was dirty in Step 1 and the regenerated output overlaps that work, stop before staging and resolve ownership of the generated file.

- [ ] **Step 5: Implement generated, strict API types**

Derive wire types from generated components:

```typescript
import type { components } from './generated/openapi';

export type CheerPostType = 'NORMAL' | 'NOTICE' | 'CHECKIN' | 'RECRUITMENT';
type CreatePostWireRequest = components['schemas']['CreatePostReq'];
type PostSummaryWire = components['schemas']['PostSummaryRes'];
type PostDetailWire = components['schemas']['PostDetailRes'];
type EmbeddedPostWire = components['schemas']['EmbeddedPostDto'];
type LinkedLookupWire = components['schemas']['LinkedPostLookupRes'];
```

Define `LinkedContent` as a TypeScript discriminated union using the generated nested schemas. Replace both normalizers with one allowlist implementation:

```typescript
const isCheerPostType = (value: string): value is CheerPostType =>
  value === 'NORMAL' ||
  value === 'NOTICE' ||
  value === 'CHECKIN' ||
  value === 'RECRUITMENT';

const normalizePostType = (value?: string | null): CheerPostType => {
  if (value == null) return 'NORMAL';
  if (isCheerPostType(value)) return value;
  throw new Error(`UNKNOWN_CHEER_POST_TYPE:${value}`);
};
```

Do not add a cast that turns `linkedContent` into an ungenerated local interface. Update `createPost`, `submitCheerPost`, and their payloads to accept `diaryId` and `partyId`. Add:

```typescript
export const fetchLinkedPostTarget = (params: { diaryId?: number; partyId?: number }) =>
  privateGet<LinkedLookupWire>('/cheer/posts/linked', { params });
```

- [ ] **Step 6: Run frontend API tests and OpenAPI freshness check**

Run:

```bash
node --import tsx --test src/api/cheerApi.test.ts src/utils/cheerSubmit.test.ts
OPENAPI_SCHEMA_URL=http://localhost:8080/v3/api-docs npm run api:types:check
```

Expected: PASS with no silent downgrade and no stale generated contract.

- [ ] **Step 7: Commit the generated contract and adapter**

```bash
git add src/api/generated/openapi.ts src/api/cheerApi.ts src/api/cheerApi.test.ts src/utils/cheerSubmit.ts src/utils/cheerSubmit.test.ts
git commit -m "feat: consume linked cheer post contract"
```

Commit the generated file only after the Step 1 ownership check is resolved. Do not fold a pre-existing unrelated generated diff into this commit.

---

### Task 7: Render linked cards and run the immediate full-route bundle gate

**Files:**
- Create: `src/components/cheer/CheerLinkedContentCard.tsx`
- Create: `src/components/cheer/CheerLinkedContentCard.test.tsx`
- Modify: `src/components/CheerCard.tsx`
- Modify: `src/components/CheerDetailArticleRuntime.tsx`
- Modify: `src/components/EmbeddedPost.tsx`
- Modify: `src/components/CheerDetailEmbeddedPostRuntime.tsx`
- Modify only if the build proves it necessary: `vite.config.ts`
- Verify generated reports: `reports/bundle-guard-report.json`
- Verify generated reports: `reports/dist-assets-report.json`

**Interfaces:**
- Consumes: Task 6 `LinkedContent` and `CheerPostType`.
- Produces: one cheer-feature renderer with `compact` and `detail` variants; consistent badges and unavailable states across every post surface.

- [ ] **Step 1: Write failing renderer tests**

Render with `renderToStaticMarkup` and assert:

```typescript
assert.match(checkinHtml, /인증 완료/);
assert.match(checkinHtml, /LG.*두산/);
assert.doesNotMatch(checkinHtml, /memo|ticket|seatRow/);
assert.match(recruitingHtml, /모집 중/);
assert.match(recruitingHtml, /파티 보기/);
assert.match(closedHtml, /모집 마감/);
assert.match(unavailableHtml, /원본을 확인할 수 없음/);
assert.doesNotMatch(unavailableHtml, /href=/);
```

- [ ] **Step 2: Run the test and verify the component is missing**

Run:

```bash
node --import tsx --test src/components/cheer/CheerLinkedContentCard.test.tsx
```

Expected: module-not-found failure.

- [ ] **Step 3: Implement the linked renderer**

Use a pure component signature:

```typescript
interface CheerLinkedContentCardProps {
  linkedContent: LinkedContent;
  variant: 'compact' | 'detail';
}
```

Use `--cheer-sub-card`, `--cheer-chip-bg`, and `--cheer-line-10`. Clamp recruitment description to two lines only in `compact`. Render `/mate/{partyId}` only when recruitment is available. Check-in never links to a diary route.

- [ ] **Step 4: Add type badges and embedded rendering**

Preserve existing `응원` and `공지` styling. Add route-local mappings:

```typescript
CHECKIN: 'bg-emerald-100 text-emerald-800 dark:bg-emerald-950/60 dark:text-emerald-200'
RECRUITMENT: 'bg-violet-100 text-violet-800 dark:bg-violet-950/60 dark:text-violet-200'
```

For simple reposts, use the original post's type and linked content. For quote reposts, render linked content inside the embedded original. `CheerCard` already covers feed, bookmarks, profiles, and hot lists; do not add duplicate surface-specific renderers there.

- [ ] **Step 5: Run renderer tests**

Run:

```bash
node --import tsx --test src/components/cheer/CheerLinkedContentCard.test.tsx src/api/cheerApi.test.ts
```

Expected: PASS.

- [ ] **Step 6: Immediately run and inspect the full-route bundle gate**

Run now, before any composer or Diary/Mate imports are added:

```bash
npm run build
git diff -- reports/bundle-guard-report.json reports/dist-assets-report.json
rg -n 'login|stadium|cheer|ImageGrid|budget|exceeded|fail' reports/bundle-guard-report.json reports/dist-assets-report.json
```

Expected: build and bundle guard PASS; `/login`, `/stadium`, and every other route remain within budget; an expected `ImageGrid-*.js` artifact still appears in the asset report. If a shared chunk merges unexpectedly, keep the substantial renderer behind a cheer-owned lazy runtime and use thin route-local adapters. Do not add `manualChunks` before rerunning and reviewing the entire report.

- [ ] **Step 7: Commit renderer source after report review**

```bash
git add src/components/cheer/CheerLinkedContentCard.tsx src/components/cheer/CheerLinkedContentCard.test.tsx src/components/CheerCard.tsx src/components/CheerDetailArticleRuntime.tsx src/components/EmbeddedPost.tsx src/components/CheerDetailEmbeddedPostRuntime.tsx
git commit -m "feat: render linked cheer post cards"
```

Include `vite.config.ts` only if the verified solution changed it and all route budgets still pass. Stage either report only when it was clean at task start and the new diff belongs entirely to this task; otherwise leave the pre-existing report change untouched and record the inspected result in the execution log.

---

### Task 8: Add the locked linked-target composer route

**Files:**
- Modify: `src/components/CheerRuntime.tsx`
- Modify: `src/components/cheer/CheerPresentation.ts`
- Modify: `src/components/cheer/CheerPresentation.test.ts`
- Modify: `src/components/CheerComposerRuntime.tsx`
- Modify: `src/components/CheerWriteModal.tsx`
- Create: `src/components/cheer/CheerLinkedComposer.test.tsx`
- Modify: `src/components/CheerRuntime.test.ts`
- Modify: `src/utils/cheerSubmit.ts`
- Modify: `src/utils/cheerSubmit.test.ts`

**Interfaces:**
- Consumes: Task 6 `fetchLinkedPostTarget`, create payloads, and Task 7 renderer.
- Produces: `/cheer/write?postType=CHECKIN&diaryId=...` and `/cheer/write?postType=RECRUITMENT&partyId=...` with a fixed preview, required body, draft preservation, and navigation to the returned post.

- [ ] **Step 1: Write failing route and modal tests**

Cover:

```typescript
assert.deepEqual(parseLinkedTarget('CHECKIN', '12', null), {
  postType: 'CHECKIN', diaryId: 12,
});
assert.deepEqual(parseLinkedTarget('RECRUITMENT', null, '44'), {
  postType: 'RECRUITMENT', partyId: 44,
});
assert.equal(parseLinkedTarget('CHECKIN', '12', '44'), null);
```

Render the linked modal and assert the preview exists, share-mode and external-source controls do not exist, and the submit button remains disabled for a blank body.

Keep the existing `CheerRuntime` source assertion for the exact `전체`, `인기`, `팔로우`, and `라이브` segment list and assert no `CHECKIN` or `RECRUITMENT` feed tab is introduced.

- [ ] **Step 2: Run tests and verify linked route state is absent**

Run:

```bash
node --import tsx --test src/components/CheerRuntime.test.ts src/components/cheer/CheerPresentation.test.ts src/components/cheer/CheerLinkedComposer.test.tsx src/utils/cheerSubmit.test.ts
```

Expected: FAIL because route parsing and linked modal props do not exist.

- [ ] **Step 3: Parse and validate the route target**

Export a pure `parseLinkedTarget` from `src/components/cheer/CheerPresentation.ts`. It accepts only the two exact type/ID combinations above and rejects mixed, missing, non-numeric, zero, or negative IDs.

`CheerRuntime` passes the parsed request to `CheerComposerRuntime`. `CheerComposerRuntime` calls `fetchLinkedPostTarget` after authentication. If `postId` is present, navigate to `/cheer/{postId}` without opening the modal. Otherwise store `preview` and open the modal.

- [ ] **Step 4: Lock linked composer behavior**

Add optional props:

```typescript
interface CheerWriteModalProps {
  linkedContent?: LinkedContent;
  linkedPostType?: Extract<CheerPostType, 'CHECKIN' | 'RECRUITMENT'>;
}
```

When linked, render `CheerLinkedContentCard`, force `INTERNAL_REPOST`, hide share-mode and external fields, keep image attachment controls, and include `diaryId` or `partyId` in the submit payload. Never import diary photos.

- [ ] **Step 5: Preserve drafts on stale-target errors and navigate after success**

In the modal submission callback, rethrow after `handleCreateSubmitFailure(error)` so `CheerWriteModal.handleSubmit` does not clear content or close on `CHECKIN_NOT_SHAREABLE`, `PARTY_NOT_RECRUITING`, `DIARY_NOT_FOUND`, `PARTY_NOT_FOUND`, or `MANUAL_BASEBALL_DATA_REQUIRED`.

On successful linked creation, navigate to `/cheer/{result.created.id}`. This also handles a concurrent duplicate response that returned the existing post with HTTP 200. Remove the optimistic linked cache entry before navigation so it cannot duplicate an already-present post.

- [ ] **Step 6: Run composer tests**

Run:

```bash
node --import tsx --test src/components/CheerRuntime.test.ts src/components/cheer/CheerPresentation.test.ts src/components/cheer/CheerLinkedComposer.test.tsx src/utils/cheerSubmit.test.ts src/api/cheerApi.test.ts
```

Expected: PASS for valid routes, locked controls, required body, stale draft preservation, and returned-post navigation.

- [ ] **Step 7: Commit the linked composer**

```bash
git add src/components/CheerRuntime.tsx src/components/cheer/CheerPresentation.ts src/components/cheer/CheerPresentation.test.ts src/components/CheerComposerRuntime.tsx src/components/CheerWriteModal.tsx src/components/cheer/CheerLinkedComposer.test.tsx src/components/CheerRuntime.test.ts src/utils/cheerSubmit.ts src/utils/cheerSubmit.test.ts
git commit -m "feat: compose linked cheer posts"
```

---

### Task 9: Add Diary and Mate share entry points

**Files:**
- Modify: `src/components/mypage/DiaryformRuntime.tsx`
- Modify: `src/components/MateDetailRuntime.tsx`
- Modify: `src/components/MateDetailContentRuntime.tsx`
- Modify: `src/components/MateDetailActionSection.tsx`
- Create: `src/components/cheer/CheerLinkedEntryActions.ts`
- Create: `src/components/cheer/CheerLinkedEntryActions.test.ts`

**Interfaces:**
- Consumes: Task 6 lookup API and Task 8 route format.
- Produces: owner-only Diary and host-only pending-party share actions while preserving the existing friend-share action.

- [ ] **Step 1: Write failing entry-action tests**

Create and test pure eligibility helpers in `CheerLinkedEntryActions.ts`:

```typescript
export const canShareDiaryToCheer = (
  diary: Pick<DiaryEntry, 'type' | 'ticketVerified'>,
) => diary.type === 'attended' && diary.ticketVerified === true;

export const canSharePartyToCheer = (
  input: { isHost: boolean; status: PartyStatus },
) => input.isHost && input.status === 'PENDING';

assert.equal(canShareDiaryToCheer({ type: 'attended', ticketVerified: true }), true);
assert.equal(canShareDiaryToCheer({ type: 'scheduled', ticketVerified: true }), false);
assert.equal(canShareDiaryToCheer({ type: 'attended', ticketVerified: false }), false);
assert.equal(canSharePartyToCheer({ isHost: true, status: 'PENDING' }), true);
assert.equal(canSharePartyToCheer({ isHost: false, status: 'PENDING' }), false);
assert.equal(canSharePartyToCheer({ isHost: true, status: 'MATCHED' }), false);
```

Also assert `MateDetailActionSection` still contains the existing friend-share label and a separate cheer-share label.

- [ ] **Step 2: Run tests and verify helpers/actions are absent**

Run:

```bash
node --import tsx --test src/components/cheer/CheerLinkedEntryActions.test.ts
```

Expected: FAIL because entry eligibility and handlers do not exist.

- [ ] **Step 3: Add the Diary read-mode action**

In `DiaryViewSection`, use `useNavigate`. Pass an `onShareToCheer` callback into `DiaryReadMode`. Render `응원석에 공유` beside `수정하기` and `삭제`.

The handler dynamically imports `fetchLinkedPostTarget`, calls it with `selectedDiary.id`, navigates to an existing `/cheer/{postId}`, or navigates to `/cheer/write?postType=CHECKIN&diaryId={id}`. Disable the control unless `selectedDiary.type === 'attended' && selectedDiary.ticketVerified === true`.

- [ ] **Step 4: Add the Mate host action without replacing friend share**

In `MateDetailRuntime`, create `handleShareToCheer` with the same lookup-first behavior for `party.id`. Thread `onShareToCheer` through `MateDetailContentRuntime` to `MateDetailActionSection`.

Render the separate button only when `isHost && party.status === 'PENDING'`. Keep `onShare` and the existing `친구에게 공유` control unchanged.

- [ ] **Step 5: Run entry-action and related Mate tests**

Run:

```bash
node --import tsx --test src/components/cheer/CheerLinkedEntryActions.test.ts src/api/diary.test.ts src/api/mate.test.ts
```

Expected: PASS.

- [ ] **Step 6: Re-run the full bundle guard after cross-route imports**

Run:

```bash
npm run build
git diff -- reports/bundle-guard-report.json reports/dist-assets-report.json
rg -n 'login|stadium|cheer|mate|mypage|ImageGrid|budget|exceeded|fail' reports/bundle-guard-report.json reports/dist-assets-report.json
```

Expected: every route stays within budget and expected lazy chunks remain. The lookup API should be dynamically imported by Diary/Mate handlers so it does not pull the full cheer runtime into their initial chunks.

- [ ] **Step 7: Commit the two entry points after report review**

```bash
git add src/components/mypage/DiaryformRuntime.tsx src/components/MateDetailRuntime.tsx src/components/MateDetailContentRuntime.tsx src/components/MateDetailActionSection.tsx src/components/cheer/CheerLinkedEntryActions.ts src/components/cheer/CheerLinkedEntryActions.test.ts
git commit -m "feat: share diary and mate entries to cheer"
```

---

### Task 10: Add production-build E2E coverage and run release verification

**Files:**
- Create: `cypress/e2e/cheer-linked-posts.cy.ts`
- Modify only when a new named script is required: `package.json`
- Verify: `reports/bundle-guard-report.json`
- Verify: `reports/dist-assets-report.json`

**Interfaces:**
- Consumes: the complete backend and frontend behavior from Tasks 1-9.
- Produces: user-flow regression coverage and the final evidence needed for release readiness.

- [ ] **Step 1: Write the failing Cypress scenarios**

Create fixtures/intercepts inside the spec for these flows:

```text
1. Eligible attended+verified Diary -> lookup has no post -> linked modal -> required body -> create -> /cheer/{id}.
2. Existing Diary linked post -> direct /cheer/{id}, no modal.
3. Host PENDING party -> separate cheer share action -> preview includes description and prices -> create.
4. Non-host or non-PENDING party -> cheer share action absent; friend share remains.
5. CHECKIN source missing/ineligible -> unavailable card with no link.
6. RECRUITMENT MATCHED -> 모집 마감 with enabled party link.
7. RECRUITMENT FAILED -> unavailable card with no link.
8. Embedded linked original retains its 인증/모집 badge and linked card.
```

- [ ] **Step 2: Run the spec once and confirm it fails for an expected missing behavior**

Serve the current production build:

```bash
npm run build
npm run preview -- --host 127.0.0.1 --port 4173
```

In a second shell:

```bash
CYPRESS_BASE_URL=http://127.0.0.1:4173 npx cypress run --spec "cypress/e2e/cheer-linked-posts.cy.ts"
```

Expected: if the spec passes immediately, record the green result and do not manufacture a failure. If it exposes a genuine missing behavior, keep the assertion and fix the product or selector in Step 3. Do not switch to `npm run dev` to work around a failure.

- [ ] **Step 3: Complete selectors and mocks without weakening assertions**

Use stable `data-testid` values:

```text
diary-share-to-cheer
mate-share-to-cheer
cheer-linked-preview
cheer-linked-unavailable
cheer-linked-party-link
```

Intercept only internal project endpoints. Do not add network calls for baseball facts.

- [ ] **Step 4: Run the targeted backend verification**

From `bega_backend/BEGA_PROJECT`:

```bash
./gradlew test --tests "*CheerLinkedPostMigrationSqlTest*" --tests "*CheerLinkedPostServiceTest*" --tests "*CheerPostServiceTest*" --tests "*CheerServiceTest*" --tests "*CheerControllerTest*" --tests "*PostDtoMapperTest*" --tests "*CheerFeedServiceTest*" --tests "*CheerQueryCountIntegrationTest*" --tests "*CheerRateLimitIntegrationTest*" --tests "*CheerEdgeCaseIntegrationTest*" --tests "*CheerConcurrencyIntegrationTest*"
./gradlew migrationSafetyCheck
./gradlew test
```

Expected: PASS.

- [ ] **Step 5: Run frontend contract, unit, build, and E2E verification**

From `bega_frontend` with the same backend schema used for generation:

```bash
OPENAPI_SCHEMA_URL=http://localhost:8080/v3/api-docs npm run api:types:check
node --import tsx --test src/api/cheerApi.test.ts src/utils/cheerSubmit.test.ts src/components/cheer/CheerLinkedContentCard.test.tsx src/components/cheer/CheerLinkedComposer.test.tsx src/components/cheer/CheerLinkedEntryActions.test.ts src/components/cheer/CheerPresentation.test.ts src/components/CheerRuntime.test.ts
npm run test:unit
npm run build
CYPRESS_BASE_URL=http://127.0.0.1:4173 npx cypress run --spec "cypress/e2e/cheer-linked-posts.cy.ts"
```

Expected: PASS. Open both report files after the build and confirm no route budget failure and no missing expected lazy chunk.

- [ ] **Step 6: Run the baseball-data policy gate**

From `/Users/mac/project/KBO_platform`:

```bash
python3 scripts/validate_baseball_data_policy.py
```

Expected: PASS with no external baseball domains, clients, crawlers, scraping dependencies, search repair, or synthesized fallback.

- [ ] **Step 7: Run cross-service release verification**

Invoke `kbo-release-verification` and `verification-before-completion`. Dispatch the repository-configured `code-reviewer` for the cross-service diff and `security-reviewer` for source ownership, DTO privacy, rate limiting, and forged update behavior. Resolve every blocking finding, rerun affected tests, confirm both dialect files use the actual selected versions, confirm OpenAPI generated types are fresh, and report the exact outputs of Steps 4-6.

- [ ] **Step 8: Commit the E2E and final verification assets**

```bash
git add cypress/e2e/cheer-linked-posts.cy.ts
git commit -m "test: cover linked cheer post flows"
```

Add `package.json` only if a new production-preview test script was actually introduced and used successfully. Stage report files only if they were clean before this task and the final diff is exclusively caused by this feature.
