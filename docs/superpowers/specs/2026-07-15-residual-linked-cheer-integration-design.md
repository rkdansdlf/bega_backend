# Residual Linked Cheer Integration Design

## Goal

Close the two unrelated frontend verification blockers, preserve and isolate the approved Mate backend contracts already present in the dirty worktree, and regenerate the frontend OpenAPI types from an exact committed backend revision.

## Scope

### Frontend blocker fixes

- Treat `StadiumGuideRuntimeSeatMaps.gwangju.ts` as a source fixture in the inline-SVG guard. The file contains documentation strings such as `<path>`; it does not render a hand-written application icon.
- Add module markers to `cheer-mobile-nav.cy.ts` and `page-route-coverage.cy.ts` so their file-local `pageResponse` constants do not collide in the TypeScript program.
- Keep generated bundle reports uncommitted.

### Backend commit boundaries

Create three independently reviewable commits from the existing user-owned Mate changes:

1. Payment capability contract: payment mode enum/service, capability DTO and endpoint, configuration keys, and focused tests.
2. Chat history pagination contract: bounded cursor parameters, repository queries, service ordering, and focused tests.
3. Party lifecycle and concurrency protection: pessimistic locks for participant/application mutations, host status-transition restrictions, and focused tests.

Do not stage or modify the unrelated Spring Boot upgrade, dev ADB profile/topology work, direct seat-view migrations, admin/KBO/leaderboard changes, or Cheer/Prediction test decomposition.

## Contract integration

After the three backend commits exist, use that exact backend `HEAD` to produce the OpenAPI document in a clean temporary checkout. Regenerate and check the frontend generated API types from that document. The frontend must consume the committed contract rather than a server started from the remaining dirty backend worktree.

## Verification

- Frontend: the previously failing design guard, TypeScript check, focused linked-cheer tests, production build, and bundle report inspection.
- Backend: focused Mate controller/service/entity tests after each commit, then migration safety and the full test suite from the committed revision.
- Repository policy: `scripts/validate_baseball_data_policy.py` must pass. No external baseball data lookup, crawling, scraping, repair, or API code is introduced.
- Review: run frontend-focused and cross-service/security review on the final diffs.

## Failure handling

If a selected backend file depends on an out-of-scope dirty change, stop instead of broadening the staged commit. If a clean-revision verification differs from dirty-worktree verification, the clean revision is authoritative and the missing dependency must be made explicit before continuing.
