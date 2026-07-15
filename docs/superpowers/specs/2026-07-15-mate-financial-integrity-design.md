# Mate Financial Integrity Design

## Context

The current Mate cancellation path accepts `CancelReasonType` from the public applicant request, so a buyer can select an internal reason and bypass the buyer-cancellation fee. Refund retries also recompute the policy from the latest request and treat Toss `ALREADY_CANCELED_*` errors as success without reading the provider's actual cancellation history. When settlement has already completed, the payment is labelled `REFUNDED_AFTER_SETTLEMENT`, but no seller debt is recorded or recovered. Finally, immediate administrator deletion disables the user in the same transaction that later acquires Party locks, leaving an application-creation race and creating an unsafe User-to-Party lock order if fixed in place.

## Goals

1. Public applicant cancellation always uses `BUYER_CHANGED_MIND`; the client may submit only a bounded memo.
2. The first refund request creates an immutable cancellation intent containing reason, memo, requested refund, fee, policy, and request time.
3. Both successful cancellation responses and `ALREADY_CANCELED_*` retries reconcile against the Toss Payment object's completed cancellation history before local state becomes `CANCELED`.
4. A refund after completed settlement creates one durable seller recovery debt per payment transaction.
5. Later payouts for that seller are held against outstanding recovery debt before any provider payout request.
6. Administrator deletion commits account disablement before Party cleanup begins and is idempotent on retry.

## Non-goals

- Calling a provider-specific payout reversal API that is not part of the current payout gateway contract.
- Adding a user-facing recovery-management UI or manual write-off endpoint.
- Changing internal host rejection or lifecycle `SYSTEM` refund reasons.
- Changing baseball schedules, teams, games, or adding any external baseball-data path.
- Refactoring the existing external payment call/outbox architecture outside the approved integrity fixes.

## Chosen Architecture

### Public Cancellation Boundary

`PartyApplicationService.cancelApplication` sanitizes every applicant request into a new `CancelRequest` with `BUYER_CHANGED_MIND` and the validated memo. Host rejection and lifecycle automation continue to call `PaymentTransactionService` directly with their server-owned reasons. The DTO keeps `cancelReasonType` for backward-compatible JSON parsing, but it is not authoritative on the public applicant path.

### Immutable Cancellation Intent and Provider Reconciliation

`PaymentTransaction` gains nullable `requestedRefundAmount`, `requestedFeeAmount`, `cancellationRequestedAt`, and `providerReconciledAt` fields. On the first cancellation attempt, the service computes and persists the policy snapshot. A retry reuses that snapshot and never overwrites its reason, memo, amounts, or policy.

Toss cancellation and lookup DTOs expose `balanceAmount` and `cancels`. The actual cumulative canceled amount is the sum of completed cancellation entries, with `totalAmount - balanceAmount` as a compatible fallback. If the immediate cancel response is incomplete, or Toss reports that the payment was already canceled, the service performs `getPayment(paymentKey)`. Local `CANCELED` is written only when the provider record proves that the immutable requested amount has been canceled. The local refund and fee values are derived from provider truth, bounded to the original gross amount.

### Seller Recovery Ledger and Payout Holds

Create `seller_payout_recoveries`, unique by source payment transaction. Each row stores seller, optional original payout transaction, original paid amount, recovery amount, recovered amount, status, and timestamps. A settled refund records debt equal to `max(0, original paid amount - post-cancellation seller entitlement)`.

`payout_transactions.recovery_offset_amount` stores the amount reserved from that payout. Before a new provider payout, `SellerRecoveryService.reserveOffset` locks outstanding seller recoveries in ID order, applies up to the new payout's net amount, and persists the offset on the payout. Retries reuse the saved offset and never double-apply it. A fully offset payout completes locally with provider reference `RECOVERY_OFFSET`; a partial offset sends only the remainder to the existing payout gateway.

### Administrator Deletion Boundary

`AdminUserDeletionPreparationService.disableForDeletion` runs in `REQUIRES_NEW`, locks the user row, disables the account, increments the token version only on the first transition, clears the lock expiry, and commits. `AdminService.deleteUser` invokes this boundary before reading or deleting related data. Its existing cleanup transaction then acquires Party locks only after the User lock has been released. Cleanup failures leave the account disabled and safe to retry.

## Error Handling

- Missing or contradictory provider cancellation evidence leaves the transaction in `REFUND_FAILED` and raises a domain failure; it is never normalized to success.
- Provider cancellation amounts larger than gross are clamped to gross; amounts smaller than the immutable requested refund are rejected.
- Recovery recording is idempotent by a database unique constraint on source payment transaction.
- Payout retries use the persisted recovery offset, so provider failures do not consume recovery debt twice.
- Repeated administrator deletion preparation does not increment `tokenVersion` again for an already-disabled user.

## Database Changes

- Oracle next available migration: `V169__add_mate_refund_intent_and_seller_recovery.sql`.
- PostgreSQL next available migration: `V175__add_mate_refund_intent_and_seller_recovery.sql`.
- Add refund-intent/reconciliation columns to `payment_transactions`.
- Add `recovery_offset_amount` to `payout_transactions`, default `0`, not null.
- Create `seller_payout_recoveries` and indexes for outstanding seller debt.

## Testing

Each behavior follows red-green-refactor. Focused tests cover public reason sanitization, immutable retries, provider reconciliation, insufficient provider evidence, idempotent recovery creation, full and partial payout offsets, retry offset reuse, and administrator pre-disable ordering/idempotency. Verification includes the Mate test suite, admin/auth targeted tests, migration safety, the full backend suite, the baseball-data policy gate, and independent code/security review.

