# Backend Boundary Hardening Design

## Context

The Spring Boot backend is a single Gradle module organized by feature packages. Its public HTTP surface is stable, but package imports reveal compile-time cycles such as `homepage <-> mate`, `media <-> mate`, and `common <-> auth`. AI HTTP integration is also duplicated across domain services, so domain code knows AI URLs, internal-token headers, HTTP clients, timeouts, and legacy endpoint aliases.

The backend worktree already contains substantial uncommitted performance and observability changes. This design treats that worktree as authoritative, preserves all existing changes, and separates the work into a low-overlap 1A phase and an overlap-sensitive 1B phase.

## Goals

1. Enforce explicit dependency direction with executable architecture tests.
2. Remove concrete `common -> auth` dependencies.
3. Centralize Backend-to-AI transport details behind capability-specific ports and outbound adapters.
4. Move repository coordination out of selected controllers and stop exposing persistence entities from those controller contracts.
5. Remove the `homepage <-> mate` and `media <-> mate` package cycles without changing public API behavior.
6. Preserve endpoint paths, HTTP status codes, response payloads, error codes, timeouts, fallbacks, security checks, and transaction semantics.

## Non-goals

- Splitting the Spring application into separately deployed services.
- Changing authentication, JWT, OAuth2, CORS, cookie, or authorization semantics.
- Changing database schemas or Flyway migrations.
- Changing Frontend, FastAPI, or public SSE contracts.
- Adding baseball crawlers, scraping, web-search repair, external baseball APIs, or synthesized baseball facts.
- Removing AI legacy aliases in this phase; adapters preserve the current fallback behavior until the later operational-cleanup phase.

## Chosen Approach

Use an incremental ports-and-adapters refactor backed by ArchUnit. Each refactor begins with a failing focused test, preserves behavior through characterization tests, and ends by adding a rule that prevents the removed dependency from returning.

This is preferred over a big-bang rewrite because each task remains independently reviewable and the current dirty worktree can be preserved. It is preferred over architecture tests alone because the plan removes the highest-value violations instead of permanently allow-listing them.

## Phase 1A: Low-overlap Boundary Work

### Architecture Test Foundation

Add ArchUnit test support to the existing backend test configuration and create focused rules rather than a repository-wide idealized rule that every existing package immediately violates.

The initial rules will enforce:

- classes under `..controller..` do not depend directly on `..repository..` for the controllers migrated in this phase;
- AI transport types (`WebClient`, `RestTemplate`, `AiServiceSettings`) are not used by the migrated domain services;
- `common.ratelimit` does not depend on `auth.service` implementations;
- no dependency from `homepage` to the concrete `mate.service.PartyService` after phase 1B;
- no dependency from `media` to mate repositories, entities, or services after phase 1B.

Rules are introduced alongside the refactor they protect so the test suite stays green at every commit.

### Common-to-Auth Dependency Inversion

`RateLimitAspect` currently calls `AuthSecurityMonitoringService` directly. Introduce a small reporting port owned by the common rate-limit boundary. The aspect emits only a transport-neutral rate-limit security event through that port. The auth package supplies the Spring implementation that maps the event to the existing monitoring service.

Dependency direction becomes:

```text
common.ratelimit -> common.ratelimit.port
auth adapter     -> common.ratelimit.port + auth monitoring service
```

If no reporter bean is available in a narrow test slice, a no-op default preserves current slice-test startup behavior. Event contents and fail-open/fail-closed behavior remain unchanged.

### AI Capability Ports and Outbound Adapters

Create separate capabilities rather than one generic untyped AI client:

- ticket vision analysis;
- seat-view classification;
- content moderation;
- RAG ingestion trigger.

Each consuming domain owns a narrow port expressed in domain request/response types. Implementations live under the backend AI infrastructure package and own:

- `AiServiceSettings` lookup;
- internal-token headers;
- `WebClient` or `RestTemplate` calls;
- timeout behavior;
- canonical `/ai/*` endpoint selection;
- existing legacy-path fallback where it already exists;
- upstream error mapping and logs.

Domain services retain validation and domain decisions but no longer construct URLs or HTTP requests. No capability is allowed to introduce an external baseball data source.

### Controller/Application Boundaries

Move persistence coordination from these controllers into focused services:

- stadium administration operations currently performed by `StadiumAdminController`;
- refresh-token rotation and repository coordination currently performed by `ReissueController`.

Controllers remain responsible for HTTP parsing, authentication context, validation, and `ResponseEntity` construction. Services own transactions and repository access. Response DTOs preserve the existing JSON field names and values. This is a structural refactor only; auth and endpoint semantics do not change.

## Phase 1B: Cycle Removal in Overlapping Areas

### Homepage-to-Mate Read Port

`HomePageFacadeService` must stop depending on `PartyService`. Define a homepage-owned read port representing only the featured-mate query required by the home bootstrap. The mate package implements the port using existing party query logic.

`FeaturedMateCardDto` remains the consumer-facing read model for this boundary. The port returns that read model, and no mate entity or repository type crosses into homepage.

Dependency direction becomes:

```text
homepage use case -> homepage read port
mate adapter      -> homepage read port
```

The existing home bootstrap concurrency, section timeout, cache behavior, metrics, manual-data fallback, ordering, and JSON response remain unchanged. The implementation must be merged around the current uncommitted `HomePageFacadeService` changes rather than replacing them.

### Media-to-Mate Maintenance Ports

`MediaMaintenanceService` currently reaches into diary, auth, cheerboard, mate, and profile persistence. In phase 1B, remove the mate-specific reverse dependency first by introducing maintenance-facing ports for:

- enumerating chat media references;
- validating or repairing chat image references through mate-owned behavior.

Mate implements these ports. Media orchestration consumes neutral maintenance records rather than `ChatMessage`, `ChatMessageRepository`, or `ChatImageService`. Existing dry-run/apply modes, report counts, object-key validation, and cleanup safety checks remain unchanged.

Broader diary/auth/profile maintenance coupling is recorded for a later boundary slice and is not silently allow-listed as resolved by this phase.

## Data Flow

### AI Call

```text
Controller or scheduler
  -> domain service
  -> domain AI capability port
  -> com.example.ai outbound adapter
  -> internal FastAPI service
```

### Home Bootstrap Featured Mate Section

```text
Home controller
  -> HomePageFacadeService
  -> FeaturedMateQuery port
  -> mate adapter
  -> PartyRepository and existing mapper
  -> FeaturedMateCardDto
```

### Media Maintenance Chat References

```text
Admin maintenance controller
  -> MediaMaintenanceService
  -> ChatMediaMaintenancePort
  -> mate adapter
  -> mate repository/service
  -> neutral maintenance records
```

## Error Handling and Compatibility

- Existing business exceptions and global mappings remain authoritative.
- AI adapters preserve current missing-configuration, timeout, upstream-status, empty-response, and legacy-fallback behavior.
- Rate-limit enforcement remains identical if monitoring fails; reporting must not change the decision path.
- Controller service extraction preserves transaction boundaries or makes them more explicit without expanding rollback scope.
- No automatic baseball-data repair is added. Missing or inconsistent baseball data continues to surface `MANUAL_BASEBALL_DATA_REQUIRED` where the current contract requires it.

## Testing Strategy

Every implementation task follows red-green-refactor:

1. Add a characterization or architecture test that fails for the intended reason.
2. Run the narrow test and capture the failure.
3. Implement the smallest complete boundary change.
4. Run focused unit/controller tests.
5. Run the corresponding ArchUnit rule.
6. Run the full backend test suite after 1A and again after 1B.

Required verification for the combined phase:

- targeted Gradle tests for each changed service/controller;
- all architecture tests;
- `./gradlew migrationSafetyCheck`;
- `./gradlew test`;
- `python3 scripts/validate_baseball_data_policy.py` from the workspace root;
- independent code review and security review;
- intentional-change audit confirming existing dirty files were not reverted or accidentally included.

## Delivery and Rollback

Each capability or cycle removal is a separate commit with its tests. No commit mixes unrelated pre-existing worktree changes. Because public contracts and schemas remain unchanged, rollback is performed by reverting the individual boundary commit without data migration.

## Success Criteria

Phase 1A is complete when:

- `RateLimitAspect` has no concrete auth-service dependency;
- migrated domain services contain no AI URL, internal-token, `WebClient`, `RestTemplate`, or legacy-path logic;
- selected controllers contain no repository access or persistence-entity response contract;
- focused ArchUnit and behavior tests pass.

Phase 1B is complete when:

- homepage has no dependency on `mate.service.PartyService`;
- mate supplies the featured-mate query through a homepage-owned port;
- media has no dependency on mate entity, repository, or service packages;
- home bootstrap and media-maintenance behavior tests pass;
- the full backend and policy verification gates pass.
