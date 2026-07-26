# Saved Entry Notes — Architecture Analysis & Implementation Plan

This document analyzes how CommaFeed structures its backend layers and provides a step-by-step plan for adding a **saved entry notes** feature (`EntryNote`).

---

## 1. Architecture Analysis

CommaFeed is a **Quarkus** application using **Jakarta EE** standards: JPA for persistence, CDI for dependency injection, and JAX-RS for REST. There is no Spring layer — all wiring uses `@Singleton`, constructor injection, and `@Path` resources.

### 1.1 Package layout

| Layer | Package | Example |
|-------|---------|---------|
| Entity | `com.commafeed.backend.model` | `FeedEntry`, `FeedEntryTag` |
| DAO | `com.commafeed.backend.dao` | `FeedEntryDAO`, `FeedEntryTagDAO` |
| Service | `com.commafeed.backend.service` | `FeedEntryService`, `FeedEntryTagService` |
| REST | `com.commafeed.frontend.resource` | `EntryREST`, `FeedREST` |
| Request DTO | `com.commafeed.frontend.model.request` | `TagRequest`, `StarRequest` |
| Response DTO | `com.commafeed.frontend.model` | `Entry`, `Entries` |
| Security | `com.commafeed.security` | `AuthenticationContext`, `Roles` |

All server-side code lives under `commafeed-server/src/main/java/com/commafeed/`.

### 1.2 Entity layer

Every persistent model extends `AbstractModel`, which provides a table-generated `Long id`:

```java
@MappedSuperclass
public abstract class AbstractModel implements Serializable {
    @Id
    @GeneratedValue(strategy = GenerationType.TABLE, generator = "gen")
    @TableGenerator(name = "gen", table = "hibernate_sequences", ...)
    private Long id;
}
```

**`FeedEntry`** (`FEEDENTRIES`) is a global, feed-scoped record — it has no direct user ownership. It holds RSS metadata (`guid`, `url`, `published`) and `@ManyToOne` links to `Feed` and `FeedEntryContent`. User-specific state lives in separate entities (`FeedEntryStatus`, `FeedEntryTag`).

**`Feed`** (`FEEDS`) is also global. It stores feed URL metadata, refresh timestamps, and error state. It is referenced by `FeedEntry` but never directly by user-scoped entities.

**`FeedEntryTag`** is the closest analog for `EntryNote`: a user-owned annotation on a feed entry.

```java
@Entity
@Table(name = "FEEDENTRYTAGS")
public class FeedEntryTag extends AbstractModel {
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "user_id")  private User user;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "entry_id") private FeedEntry entry;
    @Column(name = "name", length = 40)                                 private String name;
}
```

Conventions observed across entities:
- Lombok `@Getter` / `@Setter`
- `@Entity` + explicit `@Table(name = "...")` in uppercase
- Lazy `@ManyToOne` for associations
- Optional convenience constructor `(User, FeedEntry, …)`
- `@OneToMany(mappedBy = "entry", cascade = CascadeType.REMOVE)` on `FeedEntry` for child collections

### 1.3 DAO layer

All DAOs extend `GenericDAO<T extends AbstractModel>`:

```java
@Singleton
public abstract class GenericDAO<T extends AbstractModel> {
    protected JPAQueryFactory query() { ... }
    public void persist(T model) { ... }
    public T merge(T model) { ... }
    public T findById(Long id) { ... }
    public void delete(T object) { ... }
}
```

Each concrete DAO:
1. Is annotated `@Singleton`
2. Injects `EntityManager` via constructor and calls `super(entityManager, EntityClass.class)`
3. Uses **QueryDSL** (`Q*` classes generated at compile time) for custom queries

Example — `FeedEntryTagDAO`:

```java
@Singleton
public class FeedEntryTagDAO extends GenericDAO<FeedEntryTag> {
    private static final QFeedEntryTag TAG = QFeedEntryTag.feedEntryTag;

    public FeedEntryTagDAO(EntityManager entityManager) {
        super(entityManager, FeedEntryTag.class);
    }

    public List<FeedEntryTag> findByEntry(User user, FeedEntry entry) {
        return query().selectFrom(TAG)
            .where(TAG.user.eq(user), TAG.entry.eq(entry))
            .fetch();
    }

    public List<String> findByUser(User user) {
        return query().selectDistinct(TAG.name).from(TAG)
            .where(TAG.user.eq(user)).fetch();
    }
}
```

`FeedEntryDAO` adds feed-specific queries (`findExisting`, capacity cleanup) but follows the same base pattern.

### 1.4 Service layer

Services are `@Singleton` beans with `@RequiredArgsConstructor` constructor injection. They orchestrate one or more DAOs and contain business rules. They do **not** carry `@Transactional` — transactions are opened at the REST boundary.

**`FeedEntryService`** handles entry lifecycle and user actions (mark read, star). Authorization is enforced by verifying the user has a subscription to the entry's feed:

```java
public void markEntry(User user, Long entryId, boolean read) {
    FeedEntry entry = feedEntryDAO.findById(entryId);
    if (entry == null) return;

    FeedSubscription sub = feedSubscriptionDAO.findByFeed(user, entry.getFeed());
    if (sub == null) return;   // user does not subscribe to this feed

    FeedEntryStatus status = feedEntryStatusDAO.getStatus(user, sub, entry);
    status.setRead(read);
    feedEntryStatusDAO.merge(status);
}
```

**`FeedEntryTagService`** is the closest analog for note logic — it loads the entry, diffs existing vs. requested tags, and persists/deletes accordingly. Missing entities cause a silent early return rather than an exception.

### 1.5 REST layer

REST resources are JAX-RS classes in `com.commafeed.frontend.resource`:

```java
@Path("/rest/entry")
@RolesAllowed(Roles.USER)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor
@Singleton
@Tag(name = "Feed entries")
public class EntryREST {
    private final AuthenticationContext authenticationContext;
    private final FeedEntryTagService feedEntryTagService;

    @Path("/tag")
    @POST
    @Transactional
    @Operation(summary = "Set feed entry tags")
    public Response tagEntry(@Valid TagRequest req) {
        User user = authenticationContext.getCurrentUser();
        feedEntryTagService.updateTags(user, req.getEntryId(), req.getTags());
        return Response.ok().build();
    }
}
```

Key patterns:
- Class-level `@RolesAllowed(Roles.USER)` guards all endpoints
- `@Transactional` on each endpoint method
- `@Valid` + `Preconditions.checkNotNull()` for input validation
- Current user obtained via `authenticationContext.getCurrentUser()`
- Request bodies are plain Lombok `@Data` DTOs in `frontend.model.request` with `@Schema` annotations
- Response bodies use static factory methods on DTOs (no MapStruct)
- OpenAPI documented with `@Operation` / `@Tag`

### 1.6 Database migrations

Schema changes use **Liquibase**. The master changelog is `commafeed-server/src/main/resources/migrations.xml`, which includes versioned files under `changelogs/`. Migrations run automatically at startup (`quarkus.liquibase.migrate-at-start=true`).

New tables follow the `FeedEntryTag` pattern in `db.changelog-1.4.xml`: `createTable`, foreign keys to `FEEDENTRIES` and `USERS`, and a composite index on `(user_id, entry_id, …)`.

### 1.7 Reference summary

| Concern | Best reference |
|---------|----------------|
| User-owned data on an entry | `FeedEntryTag` |
| Entry lookup + subscription check | `FeedEntryService.markEntry()` |
| REST endpoint shape | `EntryREST` (`/tag`, `/tags`) |
| Request DTO | `TagRequest` |
| Liquibase table creation | `db.changelog-1.4.xml` → `create-tags-table` |

---

## 2. Feature Design: `EntryNote`

### 2.1 Domain model

An `EntryNote` is a short personal annotation a user attaches to a feed entry they can read (i.e., they subscribe to the entry's feed). Each note contains:

| Field | Type | Constraints |
|-------|------|-------------|
| `user` | `User` | required, FK → `USERS` |
| `entry` | `FeedEntry` | required, FK → `FEEDENTRIES` |
| `comment` | `String` | required, max 500 chars |
| `starRating` | `Integer` | required, 1–5 |
| `updated` | `Instant` | set on create/update |

**One note per user per entry** — enforced by a unique index on `(user_id, entry_id)`. A POST either creates a new note or updates the existing one (upsert).

### 2.2 API contract

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/rest/entry/note` | Create or update a note on an entry |
| `GET` | `/rest/entry/notes` | List all notes for the current user |

**POST request body** (`EntryNoteRequest`):
```json
{
  "entryId": 12345,
  "comment": "Great article on async patterns",
  "starRating": 4
}
```

**GET response** (`List<EntryNote>`):
```json
[
  {
    "id": 1,
    "entryId": 12345,
    "comment": "Great article on async patterns",
    "starRating": 4,
    "updated": "2026-07-26T13:00:00Z"
  }
]
```

---

## 3. Step-by-Step Implementation Plan

### Step 1 — Liquibase migration

**File:** `commafeed-server/src/main/resources/changelogs/db.changelog-7.3.xml`

Create a new changelog with a `create-entry-notes-table` changeset:

```xml
<changeSet author="..." id="create-entry-notes-table">
    <createTable tableName="ENTRYNOTES">
        <column name="id" type="BIGINT">
            <constraints nullable="false" primaryKey="true"/>
        </column>
        <column name="user_id" type="BIGINT">
            <constraints nullable="false"/>
        </column>
        <column name="entry_id" type="BIGINT">
            <constraints nullable="false"/>
        </column>
        <column name="comment" type="VARCHAR(500)">
            <constraints nullable="false"/>
        </column>
        <column name="star_rating" type="INTEGER">
            <constraints nullable="false"/>
        </column>
        <column name="updated" type="${timestamp_type}">
            <constraints nullable="false"/>
        </column>
    </createTable>

    <addForeignKeyConstraint constraintName="fk_entrynote_entry_id"
        baseTableName="ENTRYNOTES" baseColumnNames="entry_id"
        referencedTableName="FEEDENTRIES" referencedColumnNames="id"/>

    <addForeignKeyConstraint constraintName="fk_entrynote_user_id"
        baseTableName="ENTRYNOTES" baseColumnNames="user_id"
        referencedTableName="USERS" referencedColumnNames="id"/>

    <createIndex tableName="ENTRYNOTES" indexName="entrynote_user_entry_index" unique="true">
        <column name="user_id"/>
        <column name="entry_id"/>
    </createIndex>
</changeSet>
```

**File:** `commafeed-server/src/main/resources/migrations.xml`

Add:
```xml
<include file="changelogs/db.changelog-7.3.xml" />
```

---

### Step 2 — JPA entity

**File:** `commafeed-server/src/main/java/com/commafeed/backend/model/EntryNote.java`

```java
@Entity
@Table(name = "ENTRYNOTES")
@Getter @Setter
public class EntryNote extends AbstractModel {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entry_id", nullable = false)
    private FeedEntry entry;

    @Column(name = "comment", length = 500, nullable = false)
    private String comment;

    @Column(name = "star_rating", nullable = false)
    private int starRating;

    @Column(name = "updated", nullable = false)
    private Instant updated;

    public EntryNote() {}

    public EntryNote(User user, FeedEntry entry, String comment, int starRating) {
        this.user = user;
        this.entry = entry;
        this.comment = comment;
        this.starRating = starRating;
        this.updated = Instant.now();
    }
}
```

Optionally add the inverse mapping on `FeedEntry`:

```java
@OneToMany(mappedBy = "entry", cascade = CascadeType.REMOVE)
private Set<EntryNote> notes;
```

---

### Step 3 — DAO

**File:** `commafeed-server/src/main/java/com/commafeed/backend/dao/EntryNoteDAO.java`

```java
@Singleton
public class EntryNoteDAO extends GenericDAO<EntryNote> {

    private static final QEntryNote NOTE = QEntryNote.entryNote;

    public EntryNoteDAO(EntityManager entityManager) {
        super(entityManager, EntryNote.class);
    }

    public EntryNote findByUserAndEntry(User user, FeedEntry entry) {
        return query().selectFrom(NOTE)
            .where(NOTE.user.eq(user), NOTE.entry.eq(entry))
            .fetchOne();
    }

    public List<EntryNote> findByUser(User user) {
        return query().selectFrom(NOTE)
            .where(NOTE.user.eq(user))
            .orderBy(NOTE.updated.desc())
            .fetch();
    }
}
```

After adding the entity, run a Maven compile to generate `QEntryNote` via the existing `querydsl-apt` annotation processor.

---

### Step 4 — Request and response DTOs

**File:** `commafeed-server/src/main/java/com/commafeed/frontend/model/request/EntryNoteRequest.java`

```java
@Data
@Schema(description = "Entry Note Request")
public class EntryNoteRequest implements Serializable {

    @NotNull
    @Schema(description = "entry id", required = true)
    private Long entryId;

    @NotBlank
    @Size(max = 500)
    @Schema(description = "note comment", required = true)
    private String comment;

    @Min(1) @Max(5)
    @Schema(description = "star rating (1-5)", required = true)
    private int starRating;
}
```

**File:** `commafeed-server/src/main/java/com/commafeed/frontend/model/EntryNote.java`

> Note: name the response DTO `EntryNote` in the frontend package (matching the pattern where frontend DTOs share domain names but live in a different package), or use `EntryNoteDetails` if a naming conflict arises with the entity.

```java
@Data
@RegisterForReflection
@Schema(description = "Entry Note")
public class EntryNote implements Serializable {

    private Long id;
    private Long entryId;
    private String comment;
    private int starRating;
    private Instant updated;

    public static EntryNote build(com.commafeed.backend.model.EntryNote note) {
        EntryNote dto = new EntryNote();
        dto.setId(note.getId());
        dto.setEntryId(note.getEntry().getId());
        dto.setComment(note.getComment());
        dto.setStarRating(note.getStarRating());
        dto.setUpdated(note.getUpdated());
        return dto;
    }
}
```

---

### Step 5 — Service

**File:** `commafeed-server/src/main/java/com/commafeed/backend/service/EntryNoteService.java`

```java
@RequiredArgsConstructor
@Singleton
public class EntryNoteService {

    private final FeedEntryDAO feedEntryDAO;
    private final FeedSubscriptionDAO feedSubscriptionDAO;
    private final EntryNoteDAO entryNoteDAO;

    /**
     * Create or update a note on an entry.
     * Returns null if the entry does not exist or the user is not subscribed.
     */
    public com.commafeed.backend.model.EntryNote saveNote(
            User user, Long entryId, String comment, int starRating) {

        FeedEntry entry = feedEntryDAO.findById(entryId);
        if (entry == null) return null;

        FeedSubscription sub = feedSubscriptionDAO.findByFeed(user, entry.getFeed());
        if (sub == null) return null;

        EntryNote existing = entryNoteDAO.findByUserAndEntry(user, entry);
        if (existing != null) {
            existing.setComment(comment);
            existing.setStarRating(starRating);
            existing.setUpdated(Instant.now());
            return entryNoteDAO.merge(existing);
        }

        EntryNote note = new EntryNote(user, entry, comment, starRating);
        entryNoteDAO.persist(note);
        return note;
    }

    public List<com.commafeed.backend.model.EntryNote> findNotesForUser(User user) {
        return entryNoteDAO.findByUser(user);
    }
}
```

Business rules enforced here:
1. Entry must exist
2. User must have a subscription to the entry's feed (same check as `FeedEntryService.markEntry`)
3. Upsert semantics — one note per user per entry

---

### Step 6 — REST resource

**File:** `commafeed-server/src/main/java/com/commafeed/frontend/resource/EntryNoteREST.java`

Create a dedicated resource class (keeps `EntryREST` focused on mark/star/tag operations):

```java
@Path("/rest/entry")
@RolesAllowed(Roles.USER)
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@RequiredArgsConstructor
@Singleton
@Tag(name = "Entry notes")
public class EntryNoteREST {

    private final AuthenticationContext authenticationContext;
    private final EntryNoteService entryNoteService;

    @Path("/note")
    @POST
    @Transactional
    @Operation(summary = "Create or update a note on a feed entry")
    public Response saveNote(@Valid EntryNoteRequest req) {
        Preconditions.checkNotNull(req);
        Preconditions.checkNotNull(req.getEntryId());

        User user = authenticationContext.getCurrentUser();
        com.commafeed.backend.model.EntryNote note = entryNoteService.saveNote(
                user, req.getEntryId(), req.getComment(), req.getStarRating());

        if (note == null) {
            return Response.status(Response.Status.NOT_FOUND).build();
        }
        return Response.ok(EntryNote.build(note)).build();
    }

    @Path("/notes")
    @GET
    @Transactional
    @Operation(summary = "List all notes for the current user")
    public Response getNotes() {
        User user = authenticationContext.getCurrentUser();
        List<EntryNote> notes = entryNoteService.findNotesForUser(user).stream()
                .map(EntryNote::build)
                .toList();
        return Response.ok(notes).build();
    }
}
```

No explicit registration is needed — Quarkus CDI auto-discovers `@Singleton` JAX-RS resources on the classpath.

---

### Step 7 — Build and verify QueryDSL generation

```bash
cd commafeed-server
mvn compile -DskipTests
```

Confirm that `target/generated-sources/annotations/com/commafeed/backend/model/QEntryNote.java` is generated. If the build fails, verify the entity is in the same package tree scanned by the `querydsl-apt` processor (configured in `pom.xml`).

---

### Step 8 — Integration test (recommended)

**File:** `commafeed-server/src/test/java/com/commafeed/integration/rest/EntryNoteIT.java`

Follow the pattern in `FeedIT.java`:
- Extend `BaseIT`, use `@QuarkusTest`
- Authenticate with `RestAssured.preemptive().basic(...)`
- Subscribe to a feed and wait for entries to appear
- `POST /rest/entry/note` with an `EntryNoteRequest` → assert `200` and response body
- `GET /rest/entry/notes` → assert the note is in the list
- `POST` again with updated comment → assert upsert (same `id`, new comment)
- `POST` with an entry the user does not subscribe to → assert `404`

---

## 4. File Checklist

| # | Action | File |
|---|--------|------|
| 1 | Create | `changelogs/db.changelog-7.3.xml` |
| 2 | Edit | `migrations.xml` (add include) |
| 3 | Create | `backend/model/EntryNote.java` |
| 4 | Edit (optional) | `backend/model/FeedEntry.java` (add `notes` collection) |
| 5 | Create | `backend/dao/EntryNoteDAO.java` |
| 6 | Create | `backend/service/EntryNoteService.java` |
| 7 | Create | `frontend/model/request/EntryNoteRequest.java` |
| 8 | Create | `frontend/model/EntryNote.java` (response DTO) |
| 9 | Create | `frontend/resource/EntryNoteREST.java` |
| 10 | Create (recommended) | `test/.../integration/rest/EntryNoteIT.java` |

---

## 5. Design Decisions & Notes

**Why `FeedEntryTag` and not `FeedEntry`/`Feed` as the primary reference?**
`FeedEntry` and `Feed` are global, feed-scoped entities with no user ownership. Notes are user-scoped overlays on entries — the same pattern as tags, stars, and read status.

**Why a separate REST class instead of adding to `EntryREST`?**
The feature spec calls for a new REST resource class. Both classes can share the `/rest/entry` base path — JAX-RS merges endpoints from all resource beans at runtime.

**Why upsert instead of always insert?**
The unique `(user_id, entry_id)` index prevents duplicate notes. Upsert gives intuitive "save my note on this entry" semantics without requiring the client to track note IDs.

**Why check subscription in the service?**
This matches `FeedEntryService.markEntry()` — a user can only interact with entries from feeds they subscribe to. The check belongs in the service, not the REST layer, so it is reusable and testable.

**Stack compliance**
- JAX-RS: `@Path`, `@GET`, `@POST`, `@Produces`, `@Consumes`
- CDI: `@Singleton`, constructor injection via Lombok `@RequiredArgsConstructor`
- JPA: `@Entity`, `@Table`, `@ManyToOne`, `@Column`
- Transactions: `@Transactional` on REST methods only
- No Spring annotations (`@RestController`, `@Autowired`, etc.)

## Level 2: LLM "Rewrite this entry" Endpoint

### 1. Why this section looks different from Level 1
 
Level 1 (`EntryNote`) copies an existing vertical slice (`FeedEntryTag`) and adapts it — the
plan there is mostly *pattern discovery*. Level 2 has no analog anywhere in CommaFeed: there is
no existing "call an external HTTP API and return its result" slice to copy. So this plan is
mostly *new design*, constrained by the project's existing layering rules (REST → Service →
DAO) rather than by an existing sibling class. Where Level 1's plan says "do what
`FeedEntryTag`/`FeedEntryTagService` does", this plan has to justify each choice from first
principles and cross-check it against the closest available reference (`EntryREST`).
 
### 2. Design
 
**Two services, not one.** The LLM call is split into two layers:
 
| Class | Responsibility | Analogy in the project |
|---|---|---|
| `LlmRewriteService` | Pure HTTP client to an OpenAI-compatible chat-completions endpoint. Knows nothing about `FeedEntry`. Maps transport/HTTP failures to `WebApplicationException` (`BAD_GATEWAY` for non-200 responses, `GATEWAY_TIMEOUT` for interruption, `INTERNAL_SERVER_ERROR` for anything else). | Comparable to how `FeedRefreshEngine` isolates "talk to an external feed URL" from the entities that use the result. |
| `EntryRewriteService` | Business logic: load the `FeedEntry` via `FeedEntryDAO`, pick `title` or `content` from `FeedEntryContent`, validate it isn't blank, then delegate the actual rewrite to `LlmRewriteService`. Throws `NOT_FOUND`/`BAD_REQUEST` for entry-level problems. | Mirrors `FeedEntryService`/`FeedSubscriptionService`: a `@Singleton` service, constructor injection, no Lombok on the constructor, orchestrating one or more DAOs/services and enforcing business rules — matches the pattern confirmed against `FeedSubscriptionService`. |
 
This mirrors the project's own separation of "external I/O client" vs. "business rule that
uses the client" — the same reason `FeedRefreshEngine` is not folded into `FeedEntryService`.
 
**Why `HttpClient` and not a new dependency.** No new library needed for a single
JSON-over-HTTPS POST; keeps the dependency surface unchanged, which matters since this task
only touches `commafeed-server`.
 
**Why OpenAI-compatible request shape.** Lets a reviewer point `app.llm.url` at a local Ollama
instance (`http://localhost:11434/v1/chat/completions`) or any free-tier hosted provider
(Groq, Together, etc.) without code changes — only configuration.
 
**Why REST does not talk to `FeedEntryDAO` directly (correction from first draft).** The first
implementation had `EntryRewriteREST` calling `FeedEntryDAO.findById(...)` and reading
`entry.getContent()` inline. This broke the project's REST → Service → DAO rule (see
`DECISIONS.md`, entry on this). Fixed by moving all of that into `EntryRewriteService`; the
REST class now only maps the validated request DTO to a service call and the service result to
a response DTO — exactly the shape of `EntryREST.markEntry()`.
 
### 3. API contract
 
`POST /rest/entry/{id}/generate-alternative`
 
Request (`GenerateAlternativeRequest`):
```json
{ "target": "title", "prompt": "rewrite this headline for a technical audience" }
```
 
Response (`GenerateAlternativeResponse`), `200 OK`:
```json
{
  "originalEntryId": "12345",
  "target": "title",
  "prompt": "rewrite this headline for a technical audience",
  "generatedAlternative": "..."
}
```
 
### 4. Error handling table
 
| Condition | Detected in | Status | Notes |
|---|---|---|---|
| Entry does not exist | `EntryRewriteService.generateAlternative` | `404 NOT_FOUND` | thrown as `WebApplicationException`, handled by JAX-RS default (no custom `ExceptionMapper` needed — confirmed no mapper for `WebApplicationException` in `ExceptionMappers.java`) |
| Requested `target` field is blank/missing on the entry | `EntryRewriteService.generateAlternative` | `400 BAD_REQUEST` | e.g. entry has no content body but `target=content` was requested |
| Request body fails bean validation (`target` not `title`/`content`, `prompt` blank) | JAX-RS `@Valid` → `ValidationException` | `400 BAD_REQUEST` | handled by the project's existing `ExceptionMappers.validationFailed` |
| LLM endpoint returns non-200 | `LlmRewriteService.rewrite` | `502 BAD_GATEWAY` | logged server-side (`log.error`), no stack trace or upstream body leaked to client |
| LLM call interrupted / timed out | `LlmRewriteService.rewrite` | `504 GATEWAY_TIMEOUT` | |
| Any other unexpected failure in the LLM call | `LlmRewriteService.rewrite` | `500 INTERNAL_SERVER_ERROR` | catch-all, still no stack trace leaked |
| Unauthenticated request | `@RolesAllowed(Roles.USER)` on `EntryRewriteREST` | `401` | matches every other resource in `frontend.resource` |
 
### 5. Step-by-step (as implemented)
 
1. Add LLM config keys to `application.properties`: `app.llm.url`, `app.llm.api-key`,
   `app.llm.model`, `app.llm.timeout-seconds` (default `15`). Key stays out of source control —
   placeholder value only, real key supplied via env/`.env` at runtime.
2. `GenerateAlternativeRequest` / `GenerateAlternativeResponse` DTOs in
   `frontend.model.request` / `frontend.model`, matching the project's `@Data` + `@Schema`
   (+ `@RegisterForReflection` on the response, matching other response DTOs) convention.
3. `LlmRewriteService` (`backend.service`, `@ApplicationScoped`): builds the OpenAI-style
   payload, sends it via `HttpClient`, parses `choices[0].message.content`, maps failures per
   the error table above.
4. `EntryRewriteService` (`backend.service`, `@Singleton`, plain constructor — matches
   `FeedSubscriptionService`): loads the entry via `FeedEntryDAO`, extracts `title`/`content`,
   validates, delegates to `LlmRewriteService`.
5. `EntryRewriteREST` (`frontend.resource`): `@Path("/rest/entry")`,
   `@RolesAllowed(Roles.USER)`, `@Transactional` on the endpoint method, `@Operation`/`@Tag`
   for OpenAPI parity with `EntryREST`. Only maps `GenerateAlternativeRequest` →
   `EntryRewriteService` → `GenerateAlternativeResponse`.
6. `EntryRewriteIT` extends `BaseIT` (reuses `initialSetup(...)` instead of duplicating HTTP
   setup), uses `@InjectMock LlmRewriteService` to test three cases: success, entry not found,
   LLM failure mapped to `502`. Requests authenticate via
   `RestAssured.given().auth().preemptive().basic(TestConstants.ADMIN_USERNAME, ...)`, matching
   every other IT in the project, and use `rest/entry/...` paths without a leading slash.
### 6. File checklist
 
| # | Action | File |
|---|---|---|
| 1 | Edit | `application.properties` (LLM config keys) |
| 2 | Create | `frontend/model/request/GenerateAlternativeRequest.java` |
| 3 | Create | `frontend/model/GenerateAlternativeResponse.java` |
| 4 | Create | `backend/service/LlmRewriteService.java` |
| 5 | Create | `backend/service/EntryRewriteService.java` |
| 6 | Create | `frontend/resource/EntryRewriteREST.java` |
| 7 | Create | `test/.../integration/rest/EntryRewriteIT.java` |
 
### 7. Known limitation / next step
 
The LLM API key currently sits as a placeholder default in `application.properties`
(`dummy-key-replace-in-env`). For a real deployment this should be required (no default) and
fail fast at startup if unset, rather than failing per-request with a generic `502`. Left as-is
for this exercise since a hard startup failure would block reviewers from running the rest of
the app without an LLM key configured.