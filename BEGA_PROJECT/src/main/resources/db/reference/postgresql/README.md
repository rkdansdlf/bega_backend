# PostgreSQL Historical Reference Bundles

This is a reference-only resource location. It is not a Flyway migration
location and must never be added to `spring.flyway.locations` or
`POSTGRES_REFERENCE_LOCATIONS`.

No authoritative pre-V61 baseline bundle is currently present. The reference
runner must therefore fail with `AUTHORITATIVE_BASELINE_ARTIFACT_MISSING`
before it creates a Flyway object or opens a database connection.

An accepted bundle has this exact shape:

```text
db/reference/postgresql/<baseline-id>/
  baseline.sql
  baseline.properties
  ownership.tsv
```

`baseline.sql` must be a schema-only PostgreSQL artifact from an independent,
trusted historical or operator-provided source. It must contain `parties`,
`party_applications`, and the complete prerequisite closure for migrations
through V60. `party_applications` must not contain `payment_key` or `order_id`.
It must contain no rows, credentials, roles, passwords, ACL restoration, owner
restoration, V61-or-later objects, or production endpoints.

`baseline.properties` must seal the following contract:

```properties
status=AUTHORITATIVE_BASELINE_ACCEPTED
baseline.version=1
baseline.description=<< Flyway Baseline >>
artifact.sha256=<sha256-of-baseline.sql>
ownership.matrix.sha256=<sha256-of-ownership.tsv>
schema.fingerprint.version=pg-catalog-v1
schema.fingerprint.sha256=<expected-live-schema-fingerprint>
source.type=<trusted-source-type>
source.owner=<source-owner>
source.timestamp=<source-timestamp-or-empty>
source.commit=<source-commit-or-empty>
extraction.method=<schema-only-extraction-method>
reviewer.operator.approval=<approval-reference>
schema.only=true
contains.business.data=false
contains.credentials=false
contains.acl=false
required.object.closure.complete=true
unknown.owner.count=0
ownership.overlap.count=0
derived.only.from.current.source=false
party_applications.payment_key=false
party_applications.order_id=false
```

At least one of `source.timestamp` and `source.commit` must be populated.

`ownership.tsv` starts with this exact header:

```text
object_type	object_name	owner_category
```

Every object must have one of these owner categories:

```text
backend_postgresql_baseline
backend_versioned_migration
ai_runtime_infrastructure
other_infrastructure
future_schema
manual_baseball_dependency
```

The same object may appear only once. Unknown owner categories are rejected.
`public.parties` and `public.party_applications` must both be classified as
relations.

