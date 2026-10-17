# Boero API

Spring Boot API using Gradle, Java 21, JPA/PostgreSQL and Redis. Resolve exact versions from `build.gradle` and `gradle/wrapper/gradle-wrapper.properties`.

## Working scope and tooling

- Follow the user's requested scope and existing authorization. Diagnosis/review does not authorize implementation; message drafting does not authorize commits.
- Finish the requested outcome, including authorized verification and fixes caused by the change, without stopping after a first draft or repeatedly asking for the same approval. Stop for an unresolved scope, data-loss or access decision; report blockers and unverified behavior explicitly.
- Preserve unrelated worktree/index changes and local environment files. Commit and push only when requested.
- Local development uses `make dev` / `compose.yaml`; shared staging and production live in `boero-infra`. Copy `.env.dev.example` for local setup. Document new environment variables in the applicable example files, never real secrets.
- Retain staging/production profiles and Compose configuration. For deployment work, consult `boero-infra` operations docs and this checkout's `.github/workflows/`; verify the target host before claiming a live environment. Configuration and old provisioning notes are not runtime evidence. Do not deploy or change deployment policy unless requested.
- Spotless/google-java-format is the formatting authority. For changed Java files, use the existing formatter's scoped support where available and inspect the resulting diff. Do not reformat unrelated source for a documentation-only task.
- Commands: `./gradlew compileJava`, `./gradlew spotlessCheck`, `./gradlew fastTest`, `./gradlew integrationTest`, `./gradlew test`. Use only the checks relevant and authorized for the task; do not add or run tests on initiative.

## Java and domain boundaries

- Organize by feature/domain; repositories are named under `interfaces`, and orchestration services generally use `*UseCase`.
- Use Lombok `@RequiredArgsConstructor` for constructor injection and `@Slf4j` for logging. Prefer final dependencies/parameters in new code without broad mechanical rewrites.
- Entities own state transitions, calculations and intrinsic relationship invariants. Use intention-revealing methods and named factories when construction has invariants. Builders remain appropriate for DTOs, fixtures and entities without construction rules.
- An entity may inspect related state or call a side-effect-free related method; it must not arbitrarily mutate a related entity. Keep repository access, transactions, cache operations, notifications and cross-entity coordination in use cases/infrastructure.
- Avoid `@Data` and unrestricted class-level setters on entities with behavior. Do not add artificial behavior to reference catalogs or formal DDD machinery just for structure.
- Annotate transactional methods explicitly; use read-only transactions for reads. Class-level transactions are appropriate only if every method shares the contract.
- Keep readable conditions and useful explanations of invariants. Do not add redundant null checks, wrapper exceptions, one-use variables or abstractions solely to satisfy a generic style recipe.
- Always use braces for control-flow bodies, including single-statement guards. Separate validation, data preparation, I/O and return phases with blank lines; keep related statements together instead of spacing every line individually.

## Database migrations and lifecycle

- Create migrations only with `make migration <lowercase_snake_case_name>` from this repository. Edit the exact generated UTC-timestamped path; do not invent, copy or rename migration filenames.
- Never modify or rename a migration applied to a persistent environment. If correcting a filename in a disposable environment, use the approved recreation workflow; do not substitute Flyway repair, out-of-order execution or ignored migrations.
- Keep the shared `spring.flyway.out-of-order=true` policy: feature branches may merge after newer migrations have been deployed. This runs pending migrations without bypassing checksum validation; it does not authorize rewriting applied scripts or history. Verify both fresh installation and upgrades from existing histories, including SQL dependencies and destructive changes, before deployment.
- A persistent business invariant needs immediate entity/use-case validation and an appropriate database constraint for concurrency safety. Translate named constraint violations into application errors.
- For lifecycle-dependent deletion, lock the tenant-scoped root inside the transaction, validate lifecycle state, delete dependents in foreign-key order and delete the root last. Preserve admin/institutional authorization boundaries.
- Reusable natural identifiers on soft-deleted entities use active-row partial unique indexes. Creation checks and operational lookups must use the same predicate; history is accessed through an explicitly historical path or stable ID.
- When a task includes tests for persistence or destructive workflows, retain use-case coordination coverage and use PostgreSQL integration coverage for constraints, derived deletes, locks and full-cleanup versus rejected-deletion preservation. If test work is outside the requested scope, report the missing verification rather than silently adding it.

## HTTP, security and errors

- Controllers enforce method-security boundaries with the existing role/permission annotations. Use the `Version` enum and mapping `version` parameter; use `UnversionedRestController` only for intentionally unversioned endpoints.
- Preserve dual authentication: institutional document/institution credentials and platform email credentials. Preserve JWT refresh-family rotation and Redis-backed revocation.
- Use Java records for request/response payloads, with simple entity-to-response factories where useful. Controllers and payloads are the OpenAPI source of truth; do not maintain another handwritten specification or generate frontend types.
- Document serialized response fields as required; nullable values use `@Schema(nullable = true)`. Swagger/API docs stay disabled in staging and production.
- Application exceptions extend `ApplicationException` with an `ErrorCategory` and do not depend on Spring Web. Keep HTTP translation in `ApplicationExceptionHttpMapper` / `GlobalExceptionHandler`, using `ExceptionPayload`.
- Centralize error text in the existing domain `*Messages` classes.

## Task-specific guidance

For requested testing work, use the [testing skill](.agents/skills/spring-boot-testing/SKILL.md) for the repository's fixtures, test slices and Gradle tasks. Load only references needed by the task; routine Java or documentation edits do not require a testing guide.

## Logging

- Use parameterized structured messages: `[Context] Action, key: {}`. RequestLoggingFilter provides MDC requestId and the X-Request-Id response header.
- Never log passwords, JWTs, refresh tokens, authorization headers, cookies or secrets. Prefer internal identifiers over documents, emails or phone numbers.
- Unexpected errors get one ERROR with stack trace at GlobalExceptionHandler; propagate rather than logging again in internal layers.
- Expected validation/business errors do not get ERROR stacks or routine WARN logs. INFO records significant successful operations; DEBUG is for diagnostics and remains disabled for application packages in production.

## Commits

Use lowercase imperative Conventional Commit subjects. The [commit skill](.agents/skills/conventional-commit/SKILL.md) separates drafting from authorized staging/committing; it does not grant permission to publish.
