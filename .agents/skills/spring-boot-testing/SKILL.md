---
name: spring-boot-testing
description: Design, implement or diagnose requested Bemol API tests with the existing Gradle and fixture setup.
---

# Bemol API testing

Use Boot-managed JUnit Jupiter, Mockito and the existing fixtures; resolve versions and task configuration from `build.gradle`. This skill does not authorize adding or running tests, starting databases, or changing dependencies beyond the user's request.

Choose plain unit tests for entity/use-case logic, `@WebMvcTest` for HTTP/security, `@DataJpaTest` for repositories and `@SpringBootTest` when a full context is necessary. Use `@MockitoBean` for mocked Spring dependencies. Existing MockMvc assertions are valid; a tutorial is not a reason to migrate them.

Read only the reference needed:

| Task | Reference |
| --- | --- |
| Choose between unit, slice and integration tests | [Slice overview](references/test-slices-overview.md) |
| Controller behavior and authorization | [WebMvcTest](references/webmvctest.md); [existing MockMvc assertions](references/mockmvc-classic.md) or [MockMvcTester](references/mockmvc-tester.md) |
| Queries, constraints and transactions | [DataJpaTest](references/datajpatest.md), [PostgreSQL containers](references/testcontainers-jdbc.md) |
| HTTP client behavior | [RestClientTest](references/restclienttest.md), [RestTestClient](references/resttestclient.md) |
| Mock Spring dependencies | [MockitoBean](references/mockitobean.md) |
| Assertions | [Scalar assertions](references/assertj-basics.md), [collection assertions](references/assertj-collections.md) |
| Diagnose slow test startup | [Context caching](references/context-caching.md) |
| Requested Spring Boot test migration | [Boot 4 migration](references/sb4-migration.md) |
| Deliberate use of an existing fixture-generation dependency | [Instancio](references/instancio.md) |

Use fixtures under `src/test/java/ar/edu/utn/frvm/typeit/bemol_api/support` and feature-specific test factories. Keep lifecycle dates, identifiers and tenant relationships deterministic; avoid reflection and business logic in tests.

When execution is authorized, use the narrowest relevant Gradle task: `fastTest` excludes integration tests; `integrationTest` selects `@IntegrationTest` cases; `test` runs the complete suite. Use `--tests` for a focused class/method. Check configuration before assuming isolation from external systems.

Fix and rerun failures caused by the requested change within the authorized scope. Report unrelated failures separately. Check PostgreSQL behavior with PostgreSQL integration tests when required by the repository contract; an H2 slice does not establish locking or foreign-key behavior in PostgreSQL.

If coverage reporting is requested, use Gradle and the agreed target rather than inventing a threshold. Keep independently useful refactors outside the test task unless a concrete blocker requires a scope decision.
