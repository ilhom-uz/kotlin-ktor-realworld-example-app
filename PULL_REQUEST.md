# Article features (search / popular / stats) + toolchain, tests & CI

## Summary

This PR implements the Part 2 feature work **and** the surrounding plumbing the
exercise needs (a buildable project, a real test suite, and CI). The repository as
forked did not build and was a partial implementation — the `Article`, `Comment`, and
`Profile` controllers were empty stubs — so the work breaks down into four layers:

1. **Make it build & run** on a modern JDK.
2. **Implement the features** (I did all three options; they share one foundation).
3. **Test** — comprehensive tests for the new features, plus reviving the existing suite.
4. **CI** — JDK 17/21 matrix with caching, test reporting, and a bonus spec workflow.

> A detailed, narrative walkthrough of every issue and fix lives in
> [`KTOR_Progress.md`](KTOR_Progress.md). This document is the reviewer-facing summary.

**Result:** `./gradlew clean build` is green — **51 tests, 0 failures, 0 skipped.**

---

## Part 2 — Features

All three options are implemented, each following the existing
**controller → service → repository** pattern, with authentication where appropriate
and consistent `401 / 404 / 422` error handling.

| Option | Endpoint | Auth | Notes |
|--------|----------|------|-------|
| **A** | `GET /api/articles/feed/popular` | required | Articles sorted by favorite count (desc), `limit`/`offset` paging. |
| **B** | `GET /api/profiles/:username/stats` | optional | `{ "stats": { articlesCount, commentsCount, favoritesCount } }`; unknown user → 404. |
| **C** | `GET /api/articles/search?q=<term>` | required | Case-insensitive match on title + body; standard article-list shape; missing/blank `q` → 422. |

To make these work I built the previously-missing backend: `ArticleRepository`
(articles + tags + favorites), `CommentRepository`, and the `ArticleService` /
`CommentService` / `ProfileService` layers, then wired the three stubbed controllers.

---

## Key decisions & trade-offs

- **Toolchain modernization was unavoidable.** The project shipped with Gradle 4.10 /
  Kotlin 1.3 / Ktor 1.2.3 (2019) and a dependency that only existed on the now-defunct
  JCenter. It couldn't resolve dependencies, let alone run on JDK 17/21 (required by
  Part 4). Upgraded to **Gradle 8.7, Kotlin 1.9.24, Ktor 1.6.8, Kodein 6.5.5**, and
  pinned **H2 1.4.200** to match Exposed 0.17.14.
- **Ktor 1.6.8, not 2.x.** The last 1.x release keeps the same API, so the upgrade
  touched almost no application code. Ktor 2.x relocates packages and would have forced
  a rewrite of config/routing/controllers — out of scope for this exercise. Moving to
  2.x is a clean follow-up.
- **Test isolation via a fresh in-memory DB per `AppRule`.** The original setup shared
  one H2 database across the whole JVM (the connection pool was never closed), which
  made the legacy tests interfere with each other. Each `setup()` now uses a uniquely
  named in-memory DB, with schema creation centralized in `DbConfig`. This is what let
  me revive **all** previously-`@Ignore`d tests.
- **Public reads vs. authenticated writes.** Listing/reading articles and reading
  comments are public (optional auth, used only to compute `favorited`); creating,
  updating, favoriting, commenting, search, popular, and feed require auth. (I chose to
  require auth on `search`/`popular` for consistency with `feed` and to exercise the
  401 path; they could reasonably be public.)
- **In-memory filtering for search/popular.** Given the in-memory H2 dataset, `search`
  and `popular` read rows and filter/sort in Kotlin. This guarantees case-insensitive
  search and predictable ordering without fighting Exposed 0.17.14's operator typing. A
  production system would push this into SQL.

---

## Part 3 — Tests

26 new feature tests + 25 revived legacy tests = **51 total, all passing.**

- **`ArticleSearchControllerTest` (10):** title match, body match, case-insensitivity,
  standard list shape, empty result, `limit`, `offset`, 401, missing `q` → 422,
  blank `q` → 422.
- **`ArticlePopularControllerTest` (7):** two- and three-tier favorite ordering,
  favorite-count accuracy, unfavorite reordering, zero-favorite inclusion, `limit`,
  `offset`, 401.
- **`ProfileStatsControllerTest` (9):** combined counts, zero activity, per-user article
  isolation, favoriting other people's articles, unfavorite decrement, multiple
  comments, public read, authenticated read, unknown user → 404.

All tests follow the existing `AppRule` (boots the full app) + Unirest style in
`src/test/kotlin/io/realworld/app/web/controllers/`.

### Fixing the existing suite
Every controller test was `@Ignore`d and didn't reflect the code. After implementing
the backend I un-ignored all of them and fixed the four root causes behind the
failures (shared DB → isolation; public reads wrongly requiring auth → routing fix;
`GET /api/tags` always empty → populate the `Tags` table on article create; a genuine
token bug in the legacy `feed` test). Net: **0 skipped tests.**

### Agent experience (how this PR was built with an AI agent)

This work was done with **Claude Code** as the coding agent, driven interactively.

**Prompts / workflow I used:**
- Started broad — *"work on this exercise"* — then had the agent **explore the repo
  first** and surface that it was a non-building, partially-stubbed project before
  writing any code. That up-front reconnaissance changed the whole plan.
- Drove it in **small, verifiable steps** with a build-fix loop: *"give me the build
  command" → paste the error → "why is it failing and how do we fix it" → "do it"*.
  Each dependency/toolchain failure was fixed and re-verified before moving on.
- For features: *"implement all three options"*, then *"write tests to fully cover
  them"*, then *"un-ignore the legacy tests and fix what breaks"*, then *"add the CI"*.

**What worked well:**
- The agent was strong at **root-causing cascading failures** (dead JCenter dep →
  Kotlin metadata mismatch → H2/Exposed runtime incompatibility) and explaining *why*,
  not just patching symptoms.
- It diagnosed the **shared-in-memory-DB** issue behind ~18 test failures and fixed it
  with one architectural change rather than 18 band-aids.
- It validated behavior end-to-end (booting the server, cur\-ing endpoints) before
  trusting the tests.

**What I had to steer / correct manually:**
- Keeping scope honest: the agent initially wanted to pick a single feature; I had it
  confirm the repo state first, which revealed the real work.
- Shell quirks (zsh not word-splitting variables, glob-expanding `?` in URLs) produced
  misleading smoke-test output until corrected — a reminder that agent "verification"
  needs careful reading.
- Deliberately constraining the Ktor upgrade to 1.6.8 (vs. the agent's instinct to take
  the newest) to avoid an out-of-scope 2.x migration.

**How I'd improve the agent workflow for a customer:**
- Have the agent produce a **short repo-assessment + plan** before changing code, so
  scope surprises (like "this doesn't build") surface immediately.
- Pin a **definition of done** per step (build green, tests green, endpoint curl\-verified)
  so the agent self-checks rather than declaring success early.
- Capture the **decision log / trade-offs** as it goes (this PR + `KTOR_Progress.md`),
  which makes the AI-assisted work auditable and reviewable.

---

## Part 4 — CI/CD

- **[`.github/workflows/ci.yml`](.github/workflows/ci.yml):** build & test on every push
  / PR to `main`/`master`, a **JDK 17 + 21 matrix** (`fail-fast: false`), **Gradle
  caching** via `gradle/actions/setup-gradle`, and **JUnit reporting** via
  `mikepenz/action-junit-report` (per-JDK check + uploaded HTML report).
- **[`.github/workflows/api-spec.yml`](.github/workflows/api-spec.yml) (bonus):** builds
  the runnable distribution, starts the server, waits for readiness, and runs the
  RealWorld Postman collection (`spec-api/run-api-tests.sh`) via newman. The newman step
  is `continue-on-error` (informational) — see deviations below.

---

## Known deviations & follow-ups (non-blocking)

- **`author` is serialized as the full `User`** (includes `email`, and `password`/`token`
  as `null`) rather than a RealWorld `Profile` (`username/bio/image/following`). It's the
  pre-existing domain shape and all tests pass, but mapping author → `Profile` is a
  worthwhile cleanup (and avoids surfacing an email field).
- **Timestamps are epoch-millis numbers**, not ISO-8601 strings — the reason the
  `api-spec` newman run is informational rather than required.
- **README badges/tips are stale** (Travis, `jvmTarget = 16`); the project now targets 17
  for the JDK 17/21 matrix.

---

## How to verify

```bash
export JAVA_HOME=$(/usr/libexec/java_home -v 21 2>/dev/null || echo /opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home)
./gradlew clean build   # compiles + runs all 51 tests
./gradlew run           # http://localhost:8080  (try GET /api/tags)
```
