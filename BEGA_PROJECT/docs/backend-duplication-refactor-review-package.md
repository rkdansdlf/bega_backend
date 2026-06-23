# Backend Duplication Refactor Review Package

Date: 2026-06-18

## Summary

This package verifies the backend duplication cleanup already present in the
worktree. It does not add another refactor pass.

The review scope is limited to:

- Shared account/auth helpers: `AccountStatusUtil`, `AuthenticatedUserIds`.
- Shared cheer write/repost helpers: `CheerAuthorWriteGuard`,
  `CheerRepostTargetResolver`, `CheerRepostConstraintDetector`.
- Shared image validation helper: `ImageValidationSupport`.
- Repository duplicate cleanup around cheer write locks, team lookup queries,
  and recent user achievements.
- Flyway duplicate-body guard tests and migration resource sync.

Out of scope:

- Additional `GameRepository` native SQL restructuring.
- Frontend or AI service changes.
- HTTP API shape changes, request/response DTO changes, and auth policy changes.
- Deleting historical Flyway migrations.

## Helper Responsibilities

- `AccountStatusUtil` owns account usability and token-version comparison rules
  used by auth refresh, filters, user services, and cheer write guards.
- `AuthenticatedUserIds` centralizes controller null-authentication checks while
  preserving each caller's existing exception type and message contract.
- `CheerAuthorWriteGuard` centralizes resolving the current write author,
  principal/user-id mismatch checks, token-version validation, and locked user
  reloading for cheer writes.
- `CheerRepostTargetResolver` centralizes repost target traversal for actions
  that must apply to the original target post.
- `CheerRepostConstraintDetector` centralizes duplicate repost constraint
  detection for service and exception-handler paths.
- `ImageValidationSupport` centralizes filename, extension, MIME, file-size, and
  pixel-bound validation. Profile validation keeps its profile-specific policy
  through the profile validator.

## Verification Results

Source sanity:

- PASS: `git -C /Users/mac/project/KBO_platform/bega_backend/BEGA_PROJECT diff --check`
- PASS: `rg "findByUserIdWithAchievement|findByIdForImageWrite|findByIdForInteractionWrite" src/main/java src/test/java`
  returned no matches.

Fast gates:

- PASS: `./gradlew compileJava`
- PASS: `./gradlew migrationSafetyCheck`

Targeted tests:

- PASS: `./gradlew test --tests "*AccountStatusUtilTest" --tests "*JWTFilterTokenTypeTest" --tests "*ReissueControllerTest" --tests "*UserServiceTest"`
- PASS: `./gradlew test --tests "*CheerPostServiceTest" --tests "*CheerInteractionServiceTest" --tests "*CheerCommentServiceTest" --tests "*CheerRepostConstraintDetectorTest"`
- PASS: `./gradlew test --tests "*ImageValidationSupportTest" --tests "*ImageValidatorTest" --tests "*ProfileImageValidatorTest"`
- PASS: `./gradlew test --tests "*AchievementServiceTest" --tests "*GameRepositoryQueryShapeTest" --tests "*FlywayMigrationVersionUniquenessTest"`
- PASS: `./gradlew test --tests "*ChatMessageControllerTest" --tests "*CheckInRecordControllerTest" --tests "*MateSearchTermControllerTest" --tests "*PartyApplicationControllerTest" --tests "*PartyReviewControllerTest" --tests "*PaymentControllerTest" --tests "*AccountSecurityControllerTest" --tests "*MypageControllerTest"`

Accepted warnings:

- Gradle deprecation warning for future Gradle 9 compatibility.
- JVM class-data-sharing warning from the test runtime.
- Incubating Gradle problems report notice.

## Flyway Policy

Historical Flyway migrations remain in place even when their contents overlap.
The cleanup is enforced through duplicate-body/version guard tests and resource
sync checks instead of deleting migration history.

## Dirty Worktree Note

The backend worktree contains additional dirty files outside this focused review
package. This document records verification for the backend duplication cleanup
scope only. Unrelated dirty files were not reverted or normalized.

## Review Decision

The focused backend duplication refactor verification gate is green. The change
is ready for review as a scoped package, with `GameRepository` native SQL
restructuring left for a separate follow-up plan.
