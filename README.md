# CommaFeed — AI-Native Trainee Test Task

This is a fork of [CommaFeed](https://github.com/Athou/commafeed), a self-hosted RSS reader,
extended with two new features as part of the FAC.71.01 AI-Native Java Trainee test task.
Only `commafeed-server` was touched; `commafeed-client` is untouched.

**Fork:** https://github.com/snxwisalive/commafeed

**Demo video:** https://youtu.be/8Evd20IqxIY

## What was added

- **Level 1 — Saved entry notes**: a user can attach a short comment + 1-5 star rating to a
  feed entry they've read. New `EntryNote` entity, `EntryNoteDAO`, `EntryNoteService`,
  `EntryNoteREST` (`POST /rest/entry/note`, `GET /rest/entry/notes`), following the existing
  `FeedEntryTag` vertical slice pattern.
- **Level 2 — LLM "rewrite this entry"**: `POST /rest/entry/{id}/generate-alternative` sends
  an entry's title or content plus a free-text instruction to an LLM and returns the generated
  alternative. New `LlmRewriteService` (HTTP client to an OpenAI-compatible endpoint),
  `EntryRewriteService` (business logic/validation), `EntryRewriteREST`.
- **Level 4 (extra credit)** — done: integration tests for both new endpoints
  (`EntryNoteIT`, `EntryRewriteIT`). Chosen over Level 3 because it directly exercises the code
  that was reviewed hardest during this task (the REST↔Service boundary corrections described
  in `DECISIONS.md`), with low risk of destabilizing the existing feed-refresh pipeline.
- **Level 3 (keyword-match notifications)** — skipped. Wiring a new notification channel into
  the existing feed-refresh flow (`FeedRefreshEngine`/`FeedSubscriptionService`) carries real
  risk of breaking core functionality for a feature explicitly marked "extra credit, do not
  force it." Given the time budget, Level 4 offered a better effort/value trade-off.

## Setup & run

Requires no external database — uses the embedded H2 profile.

```bash
# from the repository root, one-time full build (also builds commafeed-client,
# required once so the Maven reactor has classes to work with)
mvn install -DskipTests

# then run only the server in dev mode
cd commafeed-server
mvn quarkus:dev
```

In dev mode (`mvn quarkus:dev`), the server listens on `http://localhost:8083`
(`%dev.quarkus.http.port=8083` in `application.properties`). All curl examples below use this
port. Note that the plain (non-dev) profile uses `8082`, and the test profile uses `8085`
(`quarkus.http.test-port`) — this is separate from what you'll hit manually.

### First-time setup: create the admin user

CommaFeed starts with an empty database. Create the first user once:

```bash
curl -X POST http://localhost:8083/rest/user/initialSetup \
  -H "Content-Type: application/json" \
  -d '{"name":"admin","password":"admin","email":"admin@commafeed.com"}'
```

### Subscribe to a feed to get a real entry ID

```bash
curl -u admin:admin -X POST http://localhost:8083/rest/feed/subscribe \
  -H "Content-Type: application/json" \
  -d '{"url":"https://news.ycombinator.com/rss","title":"HN"}'
# -> returns a numeric subscriptionId

curl -u admin:admin "http://localhost:8083/rest/feed/entries?id=<subscriptionId>"
# -> entries[].id gives you a real entry id to use below
```

## Level 1 — Saved entry notes

### `POST /rest/entry/note`

Create or update (upsert) a note on an entry. One note per user per entry.

```bash
curl -u admin:admin -X POST http://localhost:8083/rest/entry/note \
  -H "Content-Type: application/json" \
  -d '{"entryId":2,"comment":"Great article on origami PCBs","starRating":4}'
```

Response (`200 OK`):
```json
{"id":1,"entryId":2,"comment":"Great article on origami PCBs","starRating":4,"updated":1785083688140}
```

### `GET /rest/entry/notes`

List all notes for the current user.

```bash
curl -u admin:admin http://localhost:8083/rest/entry/notes
```

Response (`200 OK`):
```json
[{"id":1,"entryId":2,"comment":"Great article on origami PCBs","starRating":4,"updated":1785083688140}]
```

If the entry doesn't exist, or the user isn't subscribed to its feed, `POST /rest/entry/note`
returns `404 Not Found`.

## Level 2 — LLM "rewrite this entry"

### `POST /rest/entry/{id}/generate-alternative`

```bash
curl -u admin:admin -X POST http://localhost:8083/rest/entry/2/generate-alternative \
  -H "Content-Type: application/json" \
  -d '{"target":"title","prompt":"rewrite this headline for a technical audience"}'
```

Response (`200 OK`):
```json
{
  "originalEntryId": "2",
  "target": "title",
  "prompt": "rewrite this headline for a technical audience",
  "generatedAlternative": "Fabrication of an Origami-Style Printed Circuit Board (PCB)"
}
```

**Error handling:**

| Condition | Status |
|---|---|
| Entry doesn't exist | `404 Not Found` |
| Requested `target` field is blank/missing on the entry | `400 Bad Request` |
| Invalid request body (`target` not `title`/`content`, blank `prompt`) | `400 Bad Request` |
| LLM endpoint returns a non-200 response | `502 Bad Gateway` |
| LLM request times out / is interrupted | `504 Gateway Timeout` |
| Any other failure calling the LLM | `500 Internal Server Error` |

No stack traces are ever leaked to the client; failures are logged server-side only.

### LLM configuration

Configured via `app.llm.*` in `application.properties` (or override via environment
variables — never commit a real key):

```properties
app.llm.url=https://api.groq.com/openai/v1/chat/completions
app.llm.api-key=dummy-key-replace-in-env
app.llm.model=openai/gpt-oss-20b
app.llm.timeout-seconds=15
```

Tested against [Groq](https://console.groq.com) (free tier). The endpoint is OpenAI-compatible,
so pointing `app.llm.url` at a local Ollama instance
(`http://localhost:11434/v1/chat/completions`) also works, provided the model name is adjusted
accordingly. **Note:** Groq deprecates models fairly often — `llama3-8b-8192` and later
`llama-3.1-8b-instant`/`llama-3.3-70b-versatile` were all deprecated during this task. Check
[console.groq.com/docs/models](https://console.groq.com/docs/models) for the current list
before running this yourself.

## My AI workflow

**Note on agent configuration:** no separate `.cursorrules`/`CLAUDE.md` file was used for this
task. Conventions were fed to the AI directly through the plan documents (`PLAN.md`) instead —
the architecture analysis there (package layout, layer responsibilities, reference classes) is
what would normally live in an agent-config file, just committed as a plan rather than a
standing rules file.

- **Level 1** was built with **Cursor**, prompted with the full REST→Service→DAO→Entity layer
  analysis up front (see `PLAN.md`), so the AI worked from an approved spec rather than
  prompt-and-pray. Midway through generation Cursor hit its context/token limit and locked the
  session; rather than restart from scratch, I kept the already-generated files, treated the
  approved `PLAN.md` as the source of truth, and finished the remaining files with a
  general-purpose LLM chat interface, chunk by chunk (see `DECISIONS.md`, entry 2).
- **Level 2** was built with **Claude**, iteratively: I fed it the CommaFeed conventions
  discovered during Level 1's analysis, had it draft the LLM service and REST resource, then
  reviewed every file against the actual reference class (`EntryREST`) before accepting it.
  Three real corrections came out of this review — REST bypassing the Service layer, missing
  `@RolesAllowed`/wrong `@Path`, and integration tests assuming security bypass that doesn't
  exist in this project — all logged honestly in `DECISIONS.md`.
- **Managing context on a large, unfamiliar codebase**: rather than pasting the whole repo, I
  had the AI (and did myself) build a small reference table up front — which existing class is
  the closest analog for each new piece (`FeedEntryTag` for notes, `EntryREST` for the REST
  shape, `FeedSubscriptionService` for service-layer conventions) — and fed only those specific
  files into context when writing or reviewing each new file, instead of the whole repo at once.
- **Keeping token usage under control**: plan-first (`PLAN.md`) meant most of the "understand
  the codebase" cost was paid once, up front, rather than repeatedly re-explaining conventions
  to the AI across many small prompts.

See `DECISIONS.md` for the full log of AI proposals that were reviewed and corrected — the
single artifact I'd point a reviewer to first.

## What's left unfinished / next steps

- Level 3 (keyword-match notifications) was intentionally skipped — see rationale above.
- The `app.llm.api-key` default in `application.properties` is a placeholder
  (`dummy-key-replace-in-env`). In a real deployment this should have no default and fail fast
  at startup if unset, rather than failing per-request with a generic `502`. Left as-is here so
  reviewers can run the rest of the app without needing an LLM key configured.
- Caching of generated alternatives and rate-limiting on the LLM endpoint were considered as
  Level 4 candidates but not implemented, in favor of integration tests (see rationale above).