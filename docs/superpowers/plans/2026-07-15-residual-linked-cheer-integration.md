# Residual Linked Cheer Integration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove the final frontend verification blockers, commit only the approved Mate contracts from the dirty backend worktree, and synchronize frontend OpenAPI types from the exact committed backend revision.

**Architecture:** Keep frontend fixes local to the failing guard/spec files. Partition the existing Mate backend work into payment, chat, and lifecycle/concurrency commits with exact path staging, leaving all unrelated dirty files untouched. Verify the final API contract from a clean checkout of the committed backend `HEAD` before regenerating frontend types.

**Tech Stack:** React 18, TypeScript, Vite, Cypress, Node test runner, Spring Boot 3, Java 21, Spring Data JPA, Gradle, springdoc-openapi, openapi-typescript.

## Global Constraints

- Do not add, restore, suggest, or generate external baseball crawling, scraping, web-search repair, or external baseball API request code.
- Preserve every unrelated user-owned modification in both worktrees.
- Do not stage the Spring Boot upgrade, dev ADB work, direct seat-view migrations, admin/KBO/leaderboard changes, or Cheer/Prediction test decomposition.
- Keep `reports/bundle-guard-report.json` and `reports/dist-assets-report.json` uncommitted.
- Generate frontend OpenAPI types only from the exact committed backend revision.

---

### Task 1: Close frontend verification blockers

**Files:**
- Modify: `bega_frontend/src/components/design-slop-guard.test.ts`
- Modify: `bega_frontend/cypress/e2e/cheer-mobile-nav.cy.ts`
- Modify: `bega_frontend/cypress/e2e/page-route-coverage.cy.ts`

**Interfaces:**
- Consumes: the current source-file guard and Cypress TypeScript program.
- Produces: a guard that recognizes the Gwangju source fixture and module-scoped Cypress helpers.

- [ ] **Step 1: Reproduce the existing failures**

Run:

```bash
cd /Users/mac/project/KBO_platform/bega_frontend
node --import tsx --test src/components/design-slop-guard.test.ts
npx tsc --noEmit
```

Expected: the guard rejects `StadiumGuideRuntimeSeatMaps.gwangju.ts`; TypeScript reports TS2451 for `pageResponse` in the two Cypress specs.

- [ ] **Step 2: Apply the minimal fixture and module-scope changes**

Add the fixture to `inlineSvgAllowedFiles`:

```ts
  'StadiumGuideRuntimeSeatMaps.gwangju.ts',
```

Add this immediately after each Cypress reference directive:

```ts
export {};
```

- [ ] **Step 3: Verify focused and broad frontend checks**

Run:

```bash
node --import tsx --test src/components/design-slop-guard.test.ts
npx tsc --noEmit
npm run test:unit
npm run build
```

Expected: all commands exit 0; bundle guard passes every route budget.

- [ ] **Step 4: Inspect generated bundle evidence**

Run:

```bash
node -e "const r=require('./reports/bundle-guard-report.json'); console.log(JSON.stringify({passed:r.passed, failures:r.failures ?? []}, null, 2))"
git status --short
```

Expected: bundle report indicates success, and the two generated reports remain modified but unstaged.

- [ ] **Step 5: Commit only the three source files**

```bash
git add src/components/design-slop-guard.test.ts cypress/e2e/cheer-mobile-nav.cy.ts cypress/e2e/page-route-coverage.cy.ts
git diff --cached --check
git commit -m "test: close frontend verification blockers"
```

### Task 2: Commit the Mate payment capability contract

**Files:**
- Create: `bega_backend/BEGA_PROJECT/src/main/java/com/example/mate/dto/MatePaymentCapabilityDTO.java`
- Modify: `bega_backend/BEGA_PROJECT/src/main/java/com/example/mate/controller/PaymentController.java`
- Modify: `bega_backend/BEGA_PROJECT/src/main/java/com/example/mate/entity/MatePaymentMode.java`
- Modify: `bega_backend/BEGA_PROJECT/src/main/java/com/example/mate/service/MatePaymentModeService.java`
- Modify: `bega_backend/BEGA_PROJECT/src/main/java/com/example/mate/service/PartyApplicationService.java`
- Modify: `bega_backend/BEGA_PROJECT/src/main/resources/application.yml`
- Modify: `bega_backend/BEGA_PROJECT/src/test/java/com/example/mate/controller/PaymentControllerTest.java`
- Modify: `bega_backend/BEGA_PROJECT/src/test/java/com/example/mate/entity/MatePaymentModeTest.java`
- Modify: `bega_backend/BEGA_PROJECT/src/test/java/com/example/mate/service/PartyApplicationServicePaymentPolicyTest.java`
- Create: `bega_backend/BEGA_PROJECT/src/test/java/com/example/mate/service/MatePaymentModeServiceTest.java`

**Interfaces:**
- Consumes: `mate.payment.mode`, payment selling/payout gates, and the existing `/api/payments` controller.
- Produces: `GET /api/payments/capability` returning `MatePaymentCapabilityResponse`, plus `MatePaymentModeService.isInAppPayment()`.

- [ ] **Step 1: Verify the existing focused tests**

Run:

```bash
cd /Users/mac/project/KBO_platform/bega_backend/BEGA_PROJECT
./gradlew test --tests '*PaymentControllerTest' --tests '*MatePaymentModeTest' --tests '*MatePaymentModeServiceTest' --tests '*PartyApplicationServicePaymentPolicyTest'
```

Expected: selected tests pass. These user-owned changes already include their tests; do not rewrite or broaden them.

- [ ] **Step 2: Stage the non-overlapping payment contract paths**

```bash
cd /Users/mac/project/KBO_platform/bega_backend
git add BEGA_PROJECT/src/main/java/com/example/mate/dto/MatePaymentCapabilityDTO.java BEGA_PROJECT/src/main/java/com/example/mate/controller/PaymentController.java BEGA_PROJECT/src/main/java/com/example/mate/entity/MatePaymentMode.java BEGA_PROJECT/src/main/java/com/example/mate/service/MatePaymentModeService.java BEGA_PROJECT/src/main/resources/application.yml BEGA_PROJECT/src/test/java/com/example/mate/controller/PaymentControllerTest.java BEGA_PROJECT/src/test/java/com/example/mate/entity/MatePaymentModeTest.java BEGA_PROJECT/src/test/java/com/example/mate/service/MatePaymentModeServiceTest.java
```

Expected: only the eight listed non-overlapping payment paths are staged.

- [ ] **Step 3: Stage only the payment hunks from overlapping files**

Run `git add -p` for the two files below. In `PartyApplicationService.java`, accept only the hunk that changes `isTossTest()` to `isInAppPayment()`. In `PartyApplicationServicePaymentPolicyTest.java`, accept only the hunk that stubs `isInAppPayment()`; reject the locking lookup hunks.

```bash
git add -p BEGA_PROJECT/src/main/java/com/example/mate/service/PartyApplicationService.java
git add -p BEGA_PROJECT/src/test/java/com/example/mate/service/PartyApplicationServicePaymentPolicyTest.java
git diff --cached --check
git diff --cached --name-only
git diff --cached -- BEGA_PROJECT/src/main/java/com/example/mate/service/PartyApplicationService.java BEGA_PROJECT/src/test/java/com/example/mate/service/PartyApplicationServicePaymentPolicyTest.java
```

Expected: the cached diff contains exactly the two payment-mode hunks described above and no pessimistic-lock changes.

- [ ] **Step 4: Commit the payment contract**

```bash
git commit -m "feat: expose Mate payment capability"
```

### Task 3: Commit bounded chat history pagination

**Files:**
- Modify: `bega_backend/BEGA_PROJECT/src/main/java/com/example/mate/controller/ChatMessageController.java`
- Modify: `bega_backend/BEGA_PROJECT/src/main/java/com/example/mate/repository/ChatMessageRepository.java`
- Modify: `bega_backend/BEGA_PROJECT/src/main/java/com/example/mate/service/ChatMessageService.java`
- Modify: `bega_backend/BEGA_PROJECT/src/test/java/com/example/mate/controller/ChatMessageControllerTest.java`
- Modify: `bega_backend/BEGA_PROJECT/src/test/java/com/example/mate/service/ChatMessageServiceTest.java`

**Interfaces:**
- Consumes: authenticated party membership and stored message IDs.
- Produces: `GET /api/chat/party/{partyId}?limit=50&beforeId=<id>` with a clamped 1..100 page size and chronological response ordering.

- [ ] **Step 1: Verify focused chat tests**

```bash
cd /Users/mac/project/KBO_platform/bega_backend/BEGA_PROJECT
./gradlew test --tests '*ChatMessageControllerTest' --tests '*ChatMessageServiceTest'
```

Expected: selected tests pass, including default and `beforeId` repository selection.

- [ ] **Step 2: Stage and audit only chat paths**

```bash
cd /Users/mac/project/KBO_platform/bega_backend
git add BEGA_PROJECT/src/main/java/com/example/mate/controller/ChatMessageController.java BEGA_PROJECT/src/main/java/com/example/mate/repository/ChatMessageRepository.java BEGA_PROJECT/src/main/java/com/example/mate/service/ChatMessageService.java BEGA_PROJECT/src/test/java/com/example/mate/controller/ChatMessageControllerTest.java BEGA_PROJECT/src/test/java/com/example/mate/service/ChatMessageServiceTest.java
git diff --cached --check
git diff --cached --name-only
```

Expected: only the five chat paths are staged.

- [ ] **Step 3: Commit chat pagination**

```bash
git commit -m "feat: paginate Mate chat history"
```

### Task 4: Commit party lifecycle and concurrency protection

**Files:**
- Modify: `bega_backend/BEGA_PROJECT/src/main/java/com/example/mate/repository/PartyApplicationRepository.java`
- Modify: `bega_backend/BEGA_PROJECT/src/main/java/com/example/mate/repository/PartyRepository.java`
- Modify: `bega_backend/BEGA_PROJECT/src/main/java/com/example/mate/service/PartyApplicationService.java`
- Modify: `bega_backend/BEGA_PROJECT/src/main/java/com/example/mate/service/PartyService.java`
- Modify: `bega_backend/BEGA_PROJECT/src/test/java/com/example/mate/service/PartyApplicationServicePaymentPolicyTest.java`
- Modify: `bega_backend/BEGA_PROJECT/src/test/java/com/example/mate/service/PartyServiceTest.java`

**Interfaces:**
- Consumes: party/application IDs within transactional mutation methods.
- Produces: pessimistically locked mutation reads and rejection of host-driven lifecycle status changes except the existing SELLING transition.

- [ ] **Step 1: Verify focused lifecycle tests**

```bash
cd /Users/mac/project/KBO_platform/bega_backend/BEGA_PROJECT
./gradlew test --tests '*PartyApplicationService*Test' --tests '*PartyServiceTest'
```

Expected: selected tests pass, including locked application lookup, participant bounds, and lifecycle status rejection.

- [ ] **Step 2: Stage the remaining lifecycle hunks carefully**

`PartyApplicationService.java` and `PartyApplicationServicePaymentPolicyTest.java` were partly committed in Task 2. Stage their remaining working-tree hunks together with the four other lifecycle files:

```bash
cd /Users/mac/project/KBO_platform/bega_backend
git add BEGA_PROJECT/src/main/java/com/example/mate/repository/PartyApplicationRepository.java BEGA_PROJECT/src/main/java/com/example/mate/repository/PartyRepository.java BEGA_PROJECT/src/main/java/com/example/mate/service/PartyApplicationService.java BEGA_PROJECT/src/main/java/com/example/mate/service/PartyService.java BEGA_PROJECT/src/test/java/com/example/mate/service/PartyApplicationServicePaymentPolicyTest.java BEGA_PROJECT/src/test/java/com/example/mate/service/PartyServiceTest.java
git diff --cached --check
git diff --cached --name-only
```

Expected: only the six lifecycle paths are staged. Stop if any payment hunk remained unexpectedly after Task 2.

- [ ] **Step 3: Commit lifecycle protection**

```bash
git commit -m "fix: serialize Mate lifecycle mutations"
```

### Task 5: Synchronize the exact committed OpenAPI contract

**Files:**
- Modify if generated output changes: `bega_frontend/src/api/generated/openapi.ts`

**Interfaces:**
- Consumes: `/v3/api-docs` from the committed backend `HEAD` only.
- Produces: deterministic generated TypeScript operations for payment capability and chat query parameters.

- [ ] **Step 1: Create a clean local clone at the exact backend revision**

```bash
backend_sha=$(git -C /Users/mac/project/KBO_platform/bega_backend rev-parse HEAD)
backend_clone=$(mktemp -d /tmp/bega-backend-openapi.XXXXXX)
git clone --shared /Users/mac/project/KBO_platform/bega_backend "$backend_clone"
git -C "$backend_clone" checkout --detach "$backend_sha"
git -C "$backend_clone" status --short
```

Expected: clean detached checkout at `backend_sha`. The temporary directory contains none of the remaining dirty worktree changes.

- [ ] **Step 2: Start the clean backend schema endpoint**

```bash
cd "$backend_clone/BEGA_PROJECT"
./gradlew bootRun --args='--server.port=18080'
```

Expected: application starts and `http://127.0.0.1:18080/v3/api-docs` returns 200. Keep this process running only for the next two steps.

- [ ] **Step 3: Regenerate and immediately check frontend types**

```bash
cd /Users/mac/project/KBO_platform/bega_frontend
OPENAPI_SCHEMA_URL=http://127.0.0.1:18080/v3/api-docs npm run api:types
OPENAPI_SCHEMA_URL=http://127.0.0.1:18080/v3/api-docs npm run api:types:check
```

Expected: generation succeeds and the immediate check reports `OpenAPI generated types are up to date.`

- [ ] **Step 4: Commit generated contract only if changed**

```bash
git diff -- src/api/generated/openapi.ts
git add src/api/generated/openapi.ts
git diff --cached --check
git commit -m "chore: sync Mate OpenAPI contracts"
```

Expected: only `src/api/generated/openapi.ts` is committed. If there is no diff, skip the commit.

### Task 6: Run final cross-service verification

**Files:**
- Inspect: `bega_frontend/reports/bundle-guard-report.json`
- Inspect: all final committed diffs and both worktree statuses.

**Interfaces:**
- Consumes: final committed backend and frontend heads.
- Produces: release evidence while preserving unrelated dirty changes.

- [ ] **Step 1: Verify backend committed revision**

Run from the clean backend clone after updating it to the final backend `HEAD`:

```bash
./gradlew migrationSafetyCheck
./gradlew test
```

Expected: both commands exit 0.

- [ ] **Step 2: Verify frontend**

```bash
cd /Users/mac/project/KBO_platform/bega_frontend
npx tsc --noEmit
npm run test:unit
npm run build
```

Expected: all commands exit 0 and all bundle budgets pass.

- [ ] **Step 3: Validate repository baseball-data policy**

```bash
cd /Users/mac/project/KBO_platform
python3 scripts/validate_baseball_data_policy.py
```

Expected: policy validation exits 0 with no forbidden external baseball-data path.

- [ ] **Step 4: Audit final statuses and commits**

```bash
git -C /Users/mac/project/KBO_platform/bega_backend status --short
git -C /Users/mac/project/KBO_platform/bega_backend log -6 --oneline
git -C /Users/mac/project/KBO_platform/bega_frontend status --short
git -C /Users/mac/project/KBO_platform/bega_frontend log -6 --oneline
```

Expected: only the explicitly excluded backend changes and generated frontend reports remain dirty; approved source changes are committed.
