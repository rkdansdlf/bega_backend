# Mate Financial Integrity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Mate cancellation, settled-refund recovery, and administrator deletion race-safe and auditable.

**Architecture:** Sanitize the public cancellation boundary, persist an immutable refund intent, reconcile local results with Toss cancellation history, record settled payout recovery debt, offset future payouts, and pre-disable administrator deletion targets in a separately committed transaction. Internal system and host cancellation reasons remain server-owned.

**Tech Stack:** Java 21, Spring Boot, Spring Data JPA, Flyway, Oracle, PostgreSQL, JUnit 5, Mockito, AssertJ.

## Global Constraints

- Do not add external baseball crawling, scraping, web-search repair, or external baseball APIs.
- Preserve unrelated dirty worktree changes.
- Use Oracle migration `V169` and PostgreSQL migration `V175`, confirmed as the next available versions on 2026-07-15.
- Every production behavior change must be preceded by a focused failing test.
- Public API compatibility is preserved: `cancelReasonType` may still be parsed but is ignored for applicant cancellation.

---

### Task 1: Public applicant cancellation reason enforcement

**Files:**
- Modify: `BEGA_PROJECT/src/main/java/com/example/mate/dto/PartyApplicationDTO.java`
- Modify: `BEGA_PROJECT/src/main/java/com/example/mate/controller/PartyApplicationController.java`
- Modify: `BEGA_PROJECT/src/main/java/com/example/mate/service/PartyApplicationService.java`
- Test: `BEGA_PROJECT/src/test/java/com/example/mate/service/PartyApplicationServicePaymentPolicyTest.java`
- Test: `BEGA_PROJECT/src/test/java/com/example/mate/controller/PartyApplicationControllerTest.java`

**Interfaces:**
- Consumes: applicant-owned `PartyApplicationDTO.CancelRequest`.
- Produces: a server-owned `CancelRequest` with `BUYER_CHANGED_MIND` and a memo of at most 500 characters.

- [ ] Add a service test that submits `SYSTEM` and verifies `PaymentTransactionService.processCancellation` receives `BUYER_CHANGED_MIND` while preserving the memo.
- [ ] Run `./gradlew test --tests '*PartyApplicationServicePaymentPolicyTest*'` and verify the new assertion fails because `SYSTEM` is forwarded.
- [ ] Add `@Size(max = 500)` to `cancelMemo`, apply `@Valid` at the controller, and sanitize the request in `PartyApplicationService`.
- [ ] Re-run the service and controller tests and verify they pass.

### Task 2: Immutable refund intent and provider reconciliation

**Files:**
- Modify: `BEGA_PROJECT/src/main/java/com/example/mate/entity/PaymentTransaction.java`
- Modify: `BEGA_PROJECT/src/main/java/com/example/mate/dto/TossPaymentDTO.java`
- Modify: `BEGA_PROJECT/src/main/java/com/example/mate/service/PaymentTransactionService.java`
- Test: `BEGA_PROJECT/src/test/java/com/example/mate/service/PaymentTransactionServiceTest.java`

**Interfaces:**
- Produces: persisted `requestedRefundAmount`, `requestedFeeAmount`, `cancellationRequestedAt`, and `providerReconciledAt`.
- Consumes: `TossPaymentDTO.CancelResponse` or `ConfirmResponse` with `totalAmount`, `balanceAmount`, and `List<CancelDetail>`.
- `CancelDetail` contains `cancelAmount`, `cancelStatus`, `canceledAt`, and `transactionKey`.

- [ ] Add tests proving a retry reuses the first intent even when a new reason is supplied, an already-canceled provider response is looked up, actual cancellation amounts override estimates, and insufficient cancellation evidence fails closed.
- [ ] Run `./gradlew test --tests '*PaymentTransactionServiceTest*'` and verify failures are caused by missing immutable/reconciliation behavior.
- [ ] Add the entity and DTO fields plus helpers that create-once intent and calculate provider-canceled amount from completed cancellations or remaining balance.
- [ ] Update `processCancellation` to reconcile both immediate and lookup responses before setting `CANCELED`; persist `REFUND_FAILED` on contradictory evidence.
- [ ] Re-run the focused test until all cases pass.

### Task 3: Seller recovery ledger

**Files:**
- Create: `BEGA_PROJECT/src/main/java/com/example/mate/entity/SellerPayoutRecovery.java`
- Create: `BEGA_PROJECT/src/main/java/com/example/mate/entity/SellerRecoveryStatus.java`
- Create: `BEGA_PROJECT/src/main/java/com/example/mate/repository/SellerPayoutRecoveryRepository.java`
- Create: `BEGA_PROJECT/src/main/java/com/example/mate/service/SellerRecoveryService.java`
- Modify: `BEGA_PROJECT/src/main/java/com/example/mate/service/PaymentTransactionService.java`
- Test: `BEGA_PROJECT/src/test/java/com/example/mate/service/SellerRecoveryServiceTest.java`
- Test: `BEGA_PROJECT/src/test/java/com/example/mate/service/PaymentTransactionServiceTest.java`

**Interfaces:**
- `recordSettledRefund(PaymentTransaction tx, int originalPaidAmount)` creates or returns one recovery for the source payment.
- `reserveOffset(long sellerId, int availableAmount)` locks outstanding rows in ID order and returns `RecoveryOffsetResult(offsetAmount)`.

- [ ] Add failing tests for one recovery per settled refund, correct debt amount, partial recovery, full recovery, and idempotent repeat calls.
- [ ] Run the focused tests and verify the entity/service behavior is absent.
- [ ] Implement the entity, repository locks, service calculations, and invoke recovery recording only after provider reconciliation of a completed settlement.
- [ ] Re-run focused tests and verify they pass.

### Task 4: Future payout hold and offset

**Files:**
- Modify: `BEGA_PROJECT/src/main/java/com/example/mate/entity/PayoutTransaction.java`
- Modify: `BEGA_PROJECT/src/main/java/com/example/mate/service/PayoutService.java`
- Test: `BEGA_PROJECT/src/test/java/com/example/mate/service/PayoutServiceTest.java`

**Interfaces:**
- Consumes: `SellerRecoveryService.reserveOffset` for a new payout.
- Persists: `PayoutTransaction.recoveryOffsetAmount`; retries reuse it.
- Produces: gateway amount `max(0, payment.netAmount - recoveryOffsetAmount)`.

- [ ] Add failing tests for full offset without a gateway call, partial offset gateway amount, and failed-provider retry without a second recovery reservation.
- [ ] Run `./gradlew test --tests '*PayoutServiceTest*'` and verify expected failures.
- [ ] Apply/reuse recovery offsets before gateway execution, complete full offsets locally with `RECOVERY_OFFSET`, and send only the remainder for partial offsets.
- [ ] Re-run focused tests and verify they pass.

### Task 5: Cross-database migrations

**Files:**
- Create: `BEGA_PROJECT/src/main/resources/db/migration/V169__add_mate_refund_intent_and_seller_recovery.sql`
- Create: `BEGA_PROJECT/src/main/resources/db/migration_postgresql/V175__add_mate_refund_intent_and_seller_recovery.sql`

**Interfaces:**
- Schema matches the JPA fields from Tasks 2-4.
- Unique source payment ID makes recovery creation idempotent.

- [ ] Add both migrations with nullable historical refund-intent columns, non-null default payout offset, recovery constraints, and outstanding-debt indexes.
- [ ] Run `./gradlew migrationSafetyCheck` and correct only migration-specific failures.

### Task 6: Administrator deletion pre-disable transaction

**Files:**
- Create: `BEGA_PROJECT/src/main/java/com/example/admin/service/AdminUserDeletionPreparationService.java`
- Modify carefully: `BEGA_PROJECT/src/main/java/com/example/admin/service/AdminService.java`
- Create: `BEGA_PROJECT/src/test/java/com/example/admin/service/AdminUserDeletionPreparationServiceTest.java`
- Create or modify: `BEGA_PROJECT/src/test/java/com/example/admin/service/AdminServiceTest.java`

**Interfaces:**
- `disableForDeletion(Long userId)` runs with `Propagation.REQUIRES_NEW`, locks through `UserRepository.findByIdForWrite`, disables once, and returns the persisted user ID.
- `AdminService.deleteUser` calls preparation before any related-data lookup or Party cleanup.

- [ ] Add failing tests for write-lock usage, idempotent token-version increment, and preparation-before-cleanup ordering.
- [ ] Run the two focused test classes and verify the failures describe the old one-transaction behavior.
- [ ] Implement the preparation service, invoke it first, and remove the duplicate token-version increment from cleanup while preserving all unrelated edits in `AdminService.java`.
- [ ] Re-run focused admin/auth tests and verify they pass.

### Task 7: Review and verification

**Files:**
- Audit every file changed by Tasks 1-6.

**Interfaces:**
- No public baseball-data or external baseball request behavior is added.

- [ ] Run `./gradlew test --tests 'com.example.mate.*'`.
- [ ] Run targeted admin/auth tests affected by deletion.
- [ ] Run `./gradlew migrationSafetyCheck`.
- [ ] Run `./gradlew test`.
- [ ] Run `python3 scripts/validate_baseball_data_policy.py` from the workspace root.
- [ ] Review the final diff for secrets, unintended files, migration alignment, transaction lock order, and immutable-intent enforcement.
- [ ] Request code and security review, apply approved in-scope fixes through new failing tests, and repeat affected verification.
