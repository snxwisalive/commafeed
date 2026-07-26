# AI Workflow Decisions

## 1. Level 1 Planning & Architecture  
**AI Proposal:** Cursor proposed the full REST -> Service -> DAO -> Entity Slice.
**My Review:** Approved. The AI correctly identified that `FeedEntryTag` is the right conceptual model (user-scoped overlay) rather than modifying `FeedEntry` directly. It also correctly adhered to JAX-RS (`@Path`, `@POST`) and CDI (`@Singleton`) instead of defaulting to Spring Boot.

## 2. Handling Tool Lockout & Context Migration
**Issue:** While generating the Level 1 slice, Cursor IDE hit its hard token limit and locked me out, leaving files half-generated.
**Action Taken:** Instead of rolling back or forcing the tool, I kept the generated code, used the approved `PLAN.md` as a strict specification, and migrated the workflow to a web-based LLM to generate the remaining files chunk-by-chunk. This decoupled the architecture plan from the specific generation tool.

## 3. Level 2 REST Resource Bypassing the Service Layer
**AI Proposal:** The initial `EntryRewriteREST` injected `FeedEntryDAO` directly and read
`entry.getContent().getTitle()`/`getContent()` inline inside the REST method, then called
`LlmRewriteService` straight from there.
**My Review:** Rejected. This breaks the project's own REST -> Service -> DAO layering - the
exact rule the brief asks not to fight, and the same rule I had just followed for Level 1. A
REST class should not know how to load or interpret an entity; that belongs in a Service.
**Action Taken:** Introduced a new `EntryRewriteService` that owns entry lookup, target-field
extraction, and validation, and delegates the actual LLM call to `LlmRewriteService`. Reduced
`EntryRewriteREST` to pure request/response mapping, matching the shape of `EntryREST.markEntry()`.

## 4. Level 2 REST Resource Missing Project Conventions
**AI Proposal:** The initial `EntryRewriteREST` used `@Path("/entry")` (no `/rest` prefix), had
no `@RolesAllowed`, no `@Transactional`, and no OpenAPI annotations.
**My Review:** Rejected after diffing against the real `EntryREST` class in the codebase.
Missing `@RolesAllowed(Roles.USER)` meant the LLM endpoint was reachable without
authentication - a real security gap, not a cosmetic one, since it would let anyone burn the
configured LLM API key. The wrong path also meant the endpoint sat outside the project's REST
namespace.
**Action Taken:** Added `@Path("/rest/entry")`, `@RolesAllowed(Roles.USER)`,
`@Transactional`, `@Operation`/`@Tag` to match every other resource in
`frontend.resource`.

## 5. Level 2 Integration Test Assumptions About Auth and Routing
**AI Proposal:** The first `EntryRewriteIT` called `RestAssured.given().post("/entry/{id}/...")`
with no authentication and no base-path handling, assuming the test would hit the endpoint
directly and that `@RolesAllowed` would be bypassed automatically in a `@QuarkusTest`.
**My Review:** Wrong on both counts - running the suite gave `404` (wrong path, once the
`/rest` prefix was added to the real resource) and then `401` (no auth) once the path was
fixed. The project does not bypass security in `@QuarkusTest`; every real `*IT` class logs in
with real Basic Auth against a user created via `initialSetup(...)` in `BaseIT`.
**Action Taken:** Rewrote `EntryRewriteIT` to extend `BaseIT`, reuse `initialSetup(...)` in its
own `@BeforeEach`, add `.auth().preemptive().basic(TestConstants.ADMIN_USERNAME, ...)` to every
request, and use `rest/...` relative paths (no leading slash), matching the exact pattern of
every other integration test in the project instead of inventing a new one.