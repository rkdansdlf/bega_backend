# Cheer Linked Post Types Design

## Context

The cheer-board visual redesign already covers the feed, detail page, responsive rails, bookmarks, composer, live tab, and public profiles. The remaining slice adds two domain-linked post types without changing the existing feed segments:

- `CHECKIN`, created from an eligible BegaDiary entry;
- `RECRUITMENT`, created from an eligible Mate party.

This slice crosses the Spring Boot backend and React frontend and adds equivalent Oracle and PostgreSQL Flyway migrations. The backend and frontend worktrees already contain unrelated uncommitted work. Implementation must preserve it and avoid broad formatting or cleanup.

All baseball facts used by this feature come from the internal database. The feature must not crawl, scrape, search for, synthesize, or request external baseball data.

## Goals

1. Add `CHECKIN` and `RECRUITMENT` to the existing cheer post type contract.
2. Permit linked posts only through the owner-facing Diary and Mate share actions.
3. Enforce source ownership and eligibility on the server at preview and creation time.
4. Keep at most one non-deleted cheer post per linked source while allowing re-share after soft deletion.
5. Preserve linked posts when the source is deleted and render an unavailable state.
6. Expose only the approved public-safe source fields.
7. Reflect current Mate party status without stale snapshots.
8. Preserve the current `all`, `popular`, `following`, and `live` feed segments.
9. Keep the Diary and Mate route bundles isolated from the cheer composer bundle.

## Non-goals

- Adding `CHECKIN` or `RECRUITMENT` filters or feed tabs.
- Allowing users to choose linked post types from the ordinary cheer composer.
- Automatically creating a cheer post when a diary or party is saved.
- Publishing diary memo, photos, ticket evidence, seat details, mood, or result.
- Publishing Mate members, reservation numbers, or ticket images.
- Making diary entries publicly browsable.
- Changing existing authentication, external-share, repost, or notification semantics beyond what the new types require.
- Adding external baseball data sources or automatic baseball-data repair.

## Chosen Architecture

Use direct nullable foreign keys on `cheer_post`, mapped as scalar IDs in `CheerPost`:

- `diary_id -> bega_diary.id ON DELETE SET NULL`;
- `party_id -> parties.id ON DELETE SET NULL`.

The database owns referential integrity, while the cheer service resolves linked records through the Diary and Mate repositories. Scalar ID mapping keeps the cheer entity independent of the source entities' lifecycle and allows page-level bulk resolution without implicit JPA traversal.

Extend `PostType` to:

```text
NORMAL
NOTICE
CHECKIN
RECRUITMENT
```

The linked type and source ID are immutable after creation. `UpdatePostReq` does not accept them.

## Database Design

At design time, the latest observed versions are Oracle `V166` and PostgreSQL `V172`, making these candidate filenames only:

- Oracle: `db/migration/V167__add_cheer_linked_post_types.sql`;
- PostgreSQL: `db/migration_postgresql/V173__add_cheer_linked_post_types.sql`.

These numbers are not reserved. Immediately before creating either migration, enumerate both migration directories, identify the actual highest versions, and choose the next unused version independently for each dialect. Do not copy the candidate numbers from this document without that preflight. Migration tests and documentation must use the final filenames selected at implementation time.

Each migration adds:

1. nullable `diary_id` and `party_id` columns;
2. foreign keys with `ON DELETE SET NULL`;
3. ordinary indexes on each FK column so parent deletion and source lookup do not depend on the conditional index;
4. an active-source unique index for each source;
5. a check constraint preventing a source from being attached to the wrong post type.

PostgreSQL uses partial unique indexes where `deleted = FALSE`. Oracle uses function-based unique indexes whose expression returns the source ID only when `deleted = 0`. This matches the repository's existing active simple-repost uniqueness pattern.

The check constraint permits a linked source to become `NULL` after `ON DELETE SET NULL`, but enforces these combinations whenever a reference exists:

- `NORMAL` and `NOTICE`: both IDs are `NULL`;
- `CHECKIN`: `party_id` is `NULL`;
- `RECRUITMENT`: `diary_id` is `NULL`.

Creation-time service validation supplies the stronger rule that a new linked post must have its required source ID. Existing rows need no backfill because they remain `NORMAL` or `NOTICE` with both references `NULL`.

## Creation and Lookup API

Keep `POST /api/cheer/posts` and extend `CreatePostReq` with nullable `diaryId` and `partyId` fields.

Valid request combinations are:

| Post type | `diaryId` | `partyId` | Additional rule |
|---|---:|---:|---|
| `NORMAL` | absent | absent | existing behavior |
| `NOTICE` | absent | absent | admin only |
| `CHECKIN` | required | absent | owned, attended, verified diary |
| `RECRUITMENT` | absent | required | hosted, pending party |

For `CHECKIN` and `RECRUITMENT`, `shareMode` must be absent or `INTERNAL_REPOST`, and every external-source metadata field must be absent. The server rejects a forged external-share combination even though the linked composer hides those controls.

Add an authenticated linked-target lookup:

```text
GET /api/cheer/posts/linked?diaryId={id}
GET /api/cheer/posts/linked?partyId={id}
```

Exactly one query parameter is allowed. The endpoint validates ownership and current eligibility and returns:

```text
postId: number | null
preview: LinkedContentRes
```

The lookup method receives its own `@RateLimit(limit = 30, window = 60, key = "cheer:linked")`. The current rate-limit aspect is method-annotation based, so the existing create-post limit does not protect this new GET endpoint. The authenticated user ID remains part of the generated Redis key, and the custom key keeps linked-target reads separate from create-post buckets.

If `postId` is present, the client navigates to the existing cheer post. If it is absent, the safe preview is used by the composer.

Creation is idempotent per active linked source. The service checks for an active post before insertion. The database unique index resolves concurrent races. On a unique conflict, the service reloads the active post and returns its normal detail DTO without applying the losing request's body or images to the existing post. The controller returns `201 Created` for a new post and `200 OK` for an existing post; the response body shape is identical.

## Server-side Eligibility

### Check-in

A `CHECKIN` post requires all of the following at creation time:

- the diary exists;
- the authenticated user owns it;
- `type == ATTENDED`;
- `ticketVerified == true`;
- the internal game, date, matchup, cheering team, and stadium fields required by the preview are present and consistent.

The server performs the same checks when the lookup endpoint is called and again when the post is submitted. UI state is never trusted as authorization.

### Recruitment

A `RECRUITMENT` post requires all of the following at creation time:

- the party exists;
- the authenticated user is its host;
- `status == PENDING`;
- the internal date, matchup, stadium, and other required public fields are present and consistent.

## Linked Content Response

Add a discriminated `linkedContent` response to every cheer DTO shape that can render a post, including summary, detail, lightweight, and embedded-post DTOs.

```text
linkedContent:
  kind: CHECKIN | RECRUITMENT
  available: boolean
  unavailableReason: SOURCE_MISSING | SOURCE_INELIGIBLE | MANUAL_BASEBALL_DATA_REQUIRED | null
  checkin: CheckinLinkedContentRes | null
  recruitment: RecruitmentLinkedContentRes | null
```

The frontend represents this as a discriminated TypeScript union so an invalid combination cannot be rendered accidentally.

### Post Type Normalization

Update both response and creation normalization in `src/api/cheerApi.ts`. `normalizePostType` and `normalizeCreatePostType` must use an explicit four-value allowlist for `NORMAL`, `NOTICE`, `CHECKIN`, and `RECRUITMENT`.

An absent legacy value may retain the current `NORMAL` fallback. Any present value outside the four-value allowlist, including an empty string, is a contract error and must not be silently converted to `NORMAL`. Focused tests cover all four accepted values and the unknown-value failure path. This change must land in the same frontend delivery as linked-content rendering so a backend `CHECKIN` or `RECRUITMENT` response cannot be quietly downgraded.

### Public Check-in Fields

When available, expose only:

- game date;
- home team;
- away team;
- cheering team;
- stadium;
- verified status.

Do not expose the diary ID, memo, photos, ticket information, detailed seat data, mood, or game result. The check-in card has no public diary-detail link.

### Public Recruitment Fields

When available, expose only:

- party ID;
- game date and time;
- home and away teams;
- stadium and seat section;
- current and maximum participants;
- current party status and derived `recruiting` flag;
- description;
- existing public price, ticket-price, and reservation-deposit fields.

Do not expose members, reservation numbers, ticket images, or private application information. The client uses the existing Mate price-formatting rules.

## Source Lifecycle

Linked posts remain soft-deleted in the existing way. When a linked post becomes `deleted = true`, it leaves the conditional unique index and the source can be shared again.

Source state is resolved from the current internal database rather than stored as a snapshot:

- deleted diary or party: FK becomes `NULL`, `available = false`;
- diary no longer attended or verified: `available = false`;
- party `FAILED`: `available = false`;
- party `MATCHED`, `SELLING`, `SOLD`, `CHECKED_IN`, or `COMPLETED`: current data remains available, `recruiting = false`, and the Mate detail link remains enabled;
- party `PENDING`: `recruiting = true`.

Expected source unavailability does not delete or hide the cheer post. Unexpected repository or database failures are not converted into an unavailable source state.

## Read Performance

Introduce a cheer-owned linked-content resolver. For a feed page, it collects source IDs from both top-level posts and embedded originals, then performs:

- one bulk Diary query with required game data;
- one bulk Party query.

It maps the results by ID and supplies them to DTO mapping. Detail lookup may use a single-source query. No per-post source query is permitted. Linked state is resolved after any stable post caching layer so Mate status changes are not hidden by a stale linked-content snapshot.

## Frontend Entry Flow

The Diary read view adds `응원석에 공유` beside `수정하기` and `삭제`. It is enabled only for an attended, ticket-verified entry.

The Mate detail action area keeps the existing friend-share control and adds a separate `응원석에 공유` action visible only to the host of a `PENDING` party.

Both entry points call the linked-target lookup. An existing `postId` navigates directly to `/cheer/{postId}`. A new target navigates to:

```text
/cheer/write?postType=CHECKIN&diaryId={id}
/cheer/write?postType=RECRUITMENT&partyId={id}
```

The cheer route validates and reloads the safe preview. This avoids importing the cheer composer into the Diary or Mate route bundles and protects the existing bundle budgets.

Direct URL manipulation cannot bypass ownership or eligibility checks. A missing, unauthorized, or no-longer-eligible target does not open a writable linked composer.

## OpenAPI Contract Regeneration

Backend DTO and endpoint changes must be reflected in the generated frontend contract. After the backend OpenAPI document exposes the final request and response shapes:

1. inspect the current generated-file diff so unrelated in-progress schema work is not lost;
2. run `npm run api:types` against the updated backend `/v3/api-docs`;
3. use generated request and response types in the cheer API adapter;
4. run `npm run api:types:check` against the same backend schema;
5. reject handwritten casts or local shadow interfaces used only to bypass stale generated types.

The generated `src/api/generated/openapi.ts` change is a required deliverable, not a follow-up task. If unrelated backend schema changes are present concurrently, regenerate from the complete current schema and audit the resulting diff rather than overwriting or deleting those changes.

## Composer Behavior

For linked posts, the existing composer:

- renders a fixed linked preview above the body field;
- does not expose a type selector or source picker;
- hides the share-mode selector and external-source inputs;
- uses the internal share mode;
- requires a user-authored body under the existing non-blank contract;
- retains ordinary user-selected cheer-post image attachments;
- never imports diary photos automatically.

If eligibility changes between preview and submission, the server rejects creation. The modal preserves the draft body, disables resubmission for the stale target, and displays the domain error.

## Card and Detail Rendering

Replace the single cheer badge constant with a type map for:

- `NORMAL` -> `응원`;
- `NOTICE` -> `공지`;
- `CHECKIN` -> `인증`;
- `RECRUITMENT` -> `모집`.

Check-in uses an emerald semantic palette and recruitment uses a violet semantic palette. Light and dark text/background pairs must meet a 4.5:1 contrast ratio.

A cheer-owned linked-content component renders consistently in feed cards, detail pages, bookmarks, profiles, hot content, and embedded reposts. It remains behind a cheer-feature-owned lazy boundary rather than moving into an eager app-wide shared chunk. Routes that render cheer posts may load that feature chunk; unrelated routes must not.

The component boundary is provisional until its first production build. This repository has previously allowed a small cheer helper extraction to be coalesced with the `ImageGrid` chunk, removing the expected `ImageGrid-*.js` artifact and pushing unrelated routes such as `/login` and `/stadium` over their gzip budgets. Therefore, immediately after the first linked-content extraction or import fan-out:

1. run `npm run build`;
2. inspect `reports/bundle-guard-report.json` and `reports/dist-assets-report.json` directly;
3. verify every route budget, including routes unrelated to cheer;
4. verify that expected lazy chunk identities such as `ImageGrid-*.js` have not disappeared or been merged unexpectedly;
5. compare route gzip deltas rather than checking only whether cheer routes pass.

Do not assume `manualChunks` is a safe repair. If extraction perturbs unrelated routes, first keep the substantial renderer behind a cheer-owned lazy runtime and use thin route-local adapters. Small badge mappings may remain route-local if that avoids cross-route chunk coupling. Any `manualChunks` change requires a second full-route report audit.

Feed cards clamp the recruitment description to two lines. Detail pages show the full description.

Rendering states are:

- check-in available: matchup, date, stadium, cheering team, and verified marker;
- recruitment pending: approved fields and active `파티 보기` action;
- recruitment closed: current status, approved fields, and enabled `파티 보기` action;
- unavailable: `원본을 확인할 수 없음` with no navigation.

The existing `전체`, `인기`, `팔로우`, and `라이브` feed segments do not change.

## Error Contract

Use explicit domain error codes:

- `INVALID_LINKED_POST_REQUEST` for an invalid type/reference combination;
- `DIARY_NOT_FOUND` when the diary is absent or not owned by the requester;
- `CHECKIN_NOT_SHAREABLE` when the diary is not attended and verified;
- `PARTY_NOT_FOUND` when the party is absent;
- `PARTY_HOST_REQUIRED` when the requester is not the host;
- `PARTY_NOT_RECRUITING` when the party is not pending;
- `MANUAL_BASEBALL_DATA_REQUIRED` when required internal baseball fields are missing or inconsistent.

`MANUAL_BASEBALL_DATA_REQUIRED` names the target entity, target ID, missing or inconsistent fields, and expected internal/manual import path. The implementation must request operator-provided data and must not attempt external lookup or synthesized repair.

## Testing Strategy

Implementation follows red-green-refactor. Add a failing focused test before each behavior change.

Backend coverage includes:

1. all valid and invalid type/reference combinations;
2. existing admin notice behavior;
3. diary ownership, attended type, and ticket verification;
4. party host and pending-status requirements;
5. privacy-safe DTO fields;
6. top-level and embedded linked-content propagation;
7. existing-post idempotency and unique-conflict race recovery;
8. soft-delete followed by re-share;
9. source deletion and eligibility/status transitions;
10. bulk resolution without source-query growth per post;
11. controller `201` versus `200` behavior;
12. forged update JSON containing `postType`, `diaryId`, or `partyId` does not change the persisted linked type or references;
13. the linked-target lookup has the intended rate-limit annotation, uses a separate key, and returns `429` after its configured limit;
14. Oracle and PostgreSQL migration structure and safety.

Frontend unit and component coverage includes:

1. explicit preservation of all four post types during response and creation normalization, plus rejection of any present value outside the allowlist, including an empty string;
2. Diary and Mate share-action visibility;
3. direct navigation for an existing linked post;
4. linked composer initialization and locked preview;
5. required body and stale-target errors;
6. check-in, recruiting, closed, and unavailable card states;
7. privacy-field absence in rendered output;
8. linked content in embedded reposts.

Targeted Cypress coverage runs against the production build to avoid the known Vite-dev/headless dynamic-import interaction. It covers one complete Diary share flow, one Mate host share flow, existing-post navigation, ineligible controls, and unavailable-source rendering.

## Verification

Before completion, run and report:

- targeted backend JUnit tests;
- `./gradlew migrationSafetyCheck`;
- broader backend tests proportional to the final blast radius;
- relevant frontend unit/component tests;
- `npm run api:types` followed by `npm run api:types:check` against the same updated backend schema;
- `npm run build`, including bundle guard;
- direct review of `reports/bundle-guard-report.json` and `reports/dist-assets-report.json` for every route and expected lazy chunks;
- targeted production-build Cypress scenarios;
- `python3 scripts/validate_baseball_data_policy.py` from the workspace root;
- cross-service release verification.

`scripts/validate_baseball_data_policy.py` is confirmed to exist in the workspace at design-review time. No task to create a replacement validator is required. If it is missing at implementation time, stop and restore or explicitly replace the internal policy gate before completion rather than silently skipping the check.

No completion claim is valid if either dialect migration, OpenAPI generation check, any route bundle budget, expected lazy-chunk audit, or the baseball-data policy check fails.

## Rollout and Rollback

Deploy the database migration before or with the backend version that understands the new nullable columns. Old rows and ordinary post creation remain compatible.

The frontend may deploy after the backend contract is available. Unknown post types must not be silently normalized in the updated frontend.

Rollback of application code leaves nullable source columns and indexes in place. Do not drop populated columns during an emergency rollback. A later forward migration may remove them only after an explicit data-retention decision.

## Success Criteria

The slice is complete when:

- eligible owners can share Diary and Mate sources only through their domain screens;
- server-side validation blocks forged or stale requests;
- concurrent requests produce one active linked post;
- deleting a linked post permits re-share;
- deleting or invalidating a source preserves the post with an unavailable card;
- Mate state transitions update the recruitment card without a snapshot refresh job;
- approved fields render consistently across every cheer surface and no private fields leak;
- generated OpenAPI types include the linked request and response contract without cast-based bypasses;
- forged update fields cannot mutate linked type or source references;
- linked-target reads are rate limited independently from post creation;
- feed segments and existing post types retain their current behavior;
- all route bundle budgets stay green and expected lazy chunks remain present;
- Oracle, PostgreSQL, backend, frontend, Cypress, release, and baseball-policy verification pass.
