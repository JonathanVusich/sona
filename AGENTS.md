# AGENTS.md

Guidance for AI agents (and humans) working in this repository.

## What Sona is

Sona is a self-hosted music server for the public self-hosting community (competing with Navidrome/Jellyfin
and Lidarr/Roon). Users upload audio files. Sona fingerprints them and reads their tags, resolves them to
canonical [MusicBrainz](https://musicbrainz.org) entities (recording / release / release group / artist),
records the result in Postgres, and moves each file into its own library, laid out by Sona's database IDs,
where it is served to clients (Subsonic API first).

**Read [docs/VISION.md](docs/VISION.md) before making design decisions.** It records the product
decisions (file ownership, dedupe, matching strategy, scale target of 500k+ tracks, auth, roadmap) and
the open questions. If a decision isn't covered there, ask rather than guess.

[docs/GAPS.md](docs/GAPS.md) lists data Sona doesn't store yet. Leave such fields null and add the gap there; don't
fill them from related rows.

The project is early-stage. Expect stubs, `TODO`s, and pieces that are wired up but not yet called by
anything (there is no HTTP upload endpoint or background worker driving ingestion yet).

## Repository layout

```
settings.gradle.kts      Gradle multi-project root: includes `server` and `metadata`
gradle/wrapper/          Root Gradle wrapper (Gradle 9.5)
server/                  The main Spring Boot application (where nearly all work happens)
  build.gradle.kts       Dependencies, Flyway + jOOQ codegen configuration
  application.properties Spring config; imports config.yaml from the working directory
  config.yaml            App settings: library folders, MusicBrainz base URL
  docker-compose.yml     Local Postgres (db `sona`, user `postgres`)
  src/main/resources/db/migration/   Flyway migrations (V0..Vn)
metadata/                Separate, mostly-empty Spring Boot app (stub search controller); not on the critical path
web/                     SolidJS frontend (Vite). Signs in against the server; the library itself is still mocked
docs/pull_request_template.md
```

## Architecture (server)

All code lives under `org.sona`. The ingestion pipeline is the core of the app:

```
upload ──► Library.storeIngestTrack ──► track_to_ingest row (PENDING) + file in <ingestFolder>/<group>/<trackId>
                                              │
IngestionEngine.processTrack ◄────────────────┘
   1. pick a Parser by file extension (format/, case-insensitive)
   2. Parser.parseMetadata(InputStream) ──► RawMetadata = tags + duration (metadata/)
   3. MetadataResolver.resolveTrack ──► MusicBrainz search (client/) ──► ResolvedTrack (MBIDs + titles)
        └─ no match ──► state = INPUT_REQUIRED, file stays in the ingest area
   4. store the rows the track references: each is loaded, or resolved from MusicBrainz and upserted if it
        isn't stored (release with its group, labels and media; the track's medium; the track's artist)
   5. Library.libraryPath ──► <release group id>[name]/<release id>[name]/<track id>[title].<ext>
        (Sona's own UUIDv7 database IDs, not MBIDs, which a track may not have)
   6. insert the `track` row, then Library.importTrack moves the file into <mediaFolder>, state = COMPLETED
        └─ move fails ──► state = MOVE_FAILED, and processing it again retries only the move
```

Each row commits on its own, and the upserts are idempotent, so processing an ingest again after a failure reuses
what was stored. A release's media are stored after the release and its labels, so a stored release without the
track's medium is one whose ingest stopped part-way: the engine looks the release up again and stores its labels and
media.

| Package | Responsibility |
|---|---|
| `engine` | `IngestionEngine` / `DefaultIngestionEngine`: orchestrates parse → resolve → persist → import. Holds the extension → `Parser` registry. |
| `format` | Audio container parsing/writing. `Parser`, `Writer`, `Format` (supported formats + extensions), `MD5Checksum`. |
| `format.flac` | Hand-written FLAC metadata reader per [RFC 9639](https://www.rfc-editor.org/rfc/rfc9639.html). Each metadata block type is a record implementing the sealed `Block` interface. `FlacTag` maps Vorbis comment field names to the format-agnostic `metadata.Tag`. Bit-level reads use `dev.javax:bitstream`. |
| `metadata` | Format-agnostic tag model: `Tag` (known tags), `TagValue` (sealed: `Str`, `Int`, `Binary`), `RawMetadata` (known + unknown tags, multi-valued). |
| `metadata.resolver` | `MetadataResolver` / `MusicBrainzResolver`: turns `RawMetadata` into a `ResolvedTrack` via MusicBrainz search. `MusicBrainzTag` lists the Picard `musicbrainz_*` tag names. |
| `client` | `MusicBrainzClient`: thin wrapper over a Spring `RestClient` for the MusicBrainz WS/2 API. `MusicBrainzConfig` builds the `RestClient`. Requests are rate limited with Resilience4j's `@RateLimiter(name = "musicbrainz")` on the client, configured under `resilience4j.ratelimiter.instances.musicbrainz` in `application.properties` (about one request a second). `client.model` holds response records mirroring MB JSON (kebab-case via `@JsonProperty`). |
| `library` | `Library` / `DefaultLibrary`: owns the filesystem layout for the ingest staging area and the media library. Library paths are stored in the DB relative to the media folder, '/'-separated. `PathSegment` formats sanitised `uuid[name]` names. |
| `auth` | Authentication and permissions. Every API request carries an access JWT that Sona issued, and `SonaJwtConverter` turns it into a `UserAuthentication` straight from its claims (username, role, session state), with no database lookup. `Permission` maps each role to its permissions (Spring authorities like `library:read`, checked with `@PreAuthorize`). A session whose password must be changed may only see that it must (`ActiveUserService`). `TokenIssuer` / `DefaultTokenIssuer` issue access JWTs and rotate refresh tokens, which travel in an HttpOnly cookie (`RefreshCookieService`); the database is checked again on each refresh. `PasswordChecker` checks local users' passwords at login and changes them. `InitialAdminRunner` (every profile except `dev`) and `DevUserRunner` (`dev`) create the default accounts. Each user is `LOCAL` or `OIDC` (`users.type`); `OidcDiscoveryClient` holds Sona's client registrations at the configured OIDC providers, discovered from each issuer on first use. OIDC users sign in with Spring's OAuth2 login (authorization code flow with PKCE) at `/api/auth/oidc/authorize/<id>`, and `SignInHandler` finishes the callback through `AccountRegistrar`: a known user gets a session, and a new one is sent to the frontend's `register` page with a `RegistrationTokenService` token to choose a username (`POST /api/auth/register`). With `?prompt=none` (`SilentSignInResolver`) the provider signs a user who still has a session there straight back in, or answers that they must sign in (code 108); the provider last used is remembered in a cookie the frontend reads. An OIDC user's Sona ID is a UUIDv5 of their issuer and subject, so it is known without a lookup. The provider's refresh token is kept per session (`oidc_session`), and `ProviderClient` checks it with the provider on every refresh, so a user the provider disables loses access then. |
| `db` | DAOs over jOOQ, one per table (`ArtistDao`, `ReleaseDao`, `MediumDao`, `TrackDao`, `UserDao`, …), plus one per composite read (`ReleaseDetailsDao` and `ReleaseGroupDetailsDao` read the release and release group pages, `ReleaseGroupSummaryDao` pages through the albums list). Return lists, not open `fetchStream()` cursors. |
| `mappers` | MapStruct mappers between jOOQ records and pojos, one per table and one per composite result (`ReleaseDetailsMapper`, `MediumWithTracksMapper`, `TrackWithArtistMapper`). |
| `config` | Spring `@Configuration` classes (the MusicBrainz `RestClient` and URI builder, `SecurityConfig`, the per-start `SigningKeyConfig`, and `WebFrontendConfig`, which serves the web frontend bundled into the boot jar under `static/`, answering every non-API path it has no file for with `index.html`) and `config.properties` (`@ConfigurationProperties` records, found by `@ConfigurationPropertiesScan`). |
| `org.sona.model` | **Generated by jOOQ** (`Tables`, `tables.*`, `tables.pojos.*`, `enums.*`). Don't put hand-written classes here. Note `client.model.Track` (a MusicBrainz track) is unrelated to `model.tables.pojos.Track` (a DB row). |
| `controller` | REST controllers: `AuthController`, `OidcController`, `UserController`, `ReleaseController` (the release page), `ReleaseGroupController` (the albums list, paged by name, and the release group page), `JwksController` (Sona's public signing key), and a stub `MetadataController`. Route paths are constants in `ApiRoutes`. Every client error has an `ErrorResponse` body: a three-digit `code` from `ErrorCode`, which clients branch on and which never changes once released, and a `reason` for people. Controllers throw `ClientException(ErrorCode)` and `ApiExceptionHandler` answers it, and a parameter that isn't the type an endpoint takes with `INVALID_PARAMETER`; the security filters write theirs through `ErrorResponseWriter`. |
| `utils` | `IDGenerator`: `uuidv7()` for rows that are ordered by ID, `uuidv5(name)` for IDs worked out from a name (an OIDC user's ID comes from their issuer and subject). `HashUtils`, `CollectionUtils`. |
| `exception` | Checked exceptions (`InvalidFormatException`), and `ClientException` for refusing a request with an `ErrorCode`. |

### Persistence

- **Postgres** schema `sona`, managed by **Flyway** migrations in `server/src/main/resources/db/migration`.
  Migrations use the placeholders `${schema}` and `${user}` (set in `build.gradle.kts`).
- **Flyway runs on application startup** (configured in `application.properties`) and from Gradle
  (`flywayMigrate`, used before jOOQ codegen). Keep the placeholders in both places in sync.
- **jOOQ** code is generated from the live database into `server/build/generated-src/jooq/main`
  (package `org.sona.model`) as immutable record pojos plus interfaces. `jooqCodegen` depends on
  `flywayMigrate`. `compileJava` does **not** run codegen, so after changing a migration you must run
  `:server:jooqCodegen` yourself or you will compile against stale generated classes.
- Until the initial release, edit existing migrations in place and recreate the local database
  (`docker compose down`, then `up -d`) rather than adding new versions. After release, never edit a migration
  that has been applied; add a new `V<n>__Description.sql` instead.

### Configuration

- `server/application.properties` imports `file:config.yaml` **relative to the working directory**, so run
  the server and tests from `server/` (Gradle does this for you).
- `library.ingestFolder` / `library.mediaFolder` → `LibraryProperties`; `musicbrainz.url` → `MusicBrainzProperties`;
  `auth.*` (token lifetimes, `frontendUrl`, the `oidcProviders` users can sign in through) → `AuthProperties`.
- `bootRun` activates the `dev` profile, which creates `admin`/`admin` and `user`/`user`, and stores a few releases
  (`DevLibraryRunner`, whose tracks have no audio files) and logs each one's page. Any other profile gets
  `admin`/`admin` on an empty database, who must change the password first.
- The MusicBrainz rate limit is in `application.properties` (`resilience4j.ratelimiter.instances.musicbrainz.*`).
- The datasource is Spring Boot's (`spring.datasource.*` in `application.properties`, local dev credentials).
  The `flyway` / `jooq` blocks in `build.gradle.kts` repeat them for build-time codegen; keep them in sync.

## Building and testing

Prerequisites: JDK 25, Docker, Node 22 (for `web/`, which the boot jar bundles, and the E2E tests). (`dev.javax:bitstream` is the author's own library, published to Maven Central.)

```bash
docker compose -f server/docker-compose.yml up -d     # start Postgres
./gradlew :server:flywayMigrate :server:jooqCodegen  # apply migrations + regenerate jOOQ classes
./gradlew :server:compileJava
./gradlew :server:test                               # integration tests start their own Postgres via Testcontainers
./gradlew :server:test --tests 'org.sona.format.flac.FlacParserTest'   # unit test, no Docker needed
./gradlew :server:e2eTest                            # builds web/, then drives it in Firefox against a test server
./gradlew :server:e2eTest -Pe2eBrowser=webkit        # or chromium; CI runs all three
./gradlew :server:bootRun                            # dev profile: admin/admin and user/user
npm --prefix web run dev                             # frontend on :5173, proxying /api to bootRun on :8080
```

The docker-compose database is only needed for `bootRun` and jOOQ codegen; tests don't use it.

Test notes:

- `@IntegrationTest` (in `src/test/java/org/sona`) = `@SpringBootTest(webEnvironment = RANDOM_PORT)`
  + `TestcontainersConfig` (throwaway `postgres:18`, migrated by Flyway on startup) + `TestLibraryConfig`
  (in-memory Jimfs library folders, MusicBrainz pointed at a WireMock server) + `TestAuthConfig` (users can sign in
  through a `FakeOidcProvider`, a WireMock OIDC provider that signs ID tokens and answers token requests). Tests
  commit, and `TruncateTablesExtension` empties every table afterwards. `TestQueryPlanConfig` EXPLAINs every jOOQ
  query with sequential scans off, and `IndexedQueryListener` fails the test if any table of Sona's is scanned in
  full, so a query without an index fails the first test that runs it. Needs Docker.
- HTTP tests call the real port through `ApiClient` (a Spring `RestTestClient`), so they pass through the security
  filters. `TestTokens` signs access tokens as Sona does, and `TestUsers` creates local users whose password is their
  username.
- **MusicBrainz is never called live.** Tests stub it with [WireMock](https://wiremock.org). Mapping files in
  `src/test/resources/wiremock/mappings` match exact queries and return responses recorded from the live API
  into `src/test/resources/wiremock/__files`; unmatched requests get a 404. Unit tests register a
  `WireMockExtension` with `usingFilesUnderClasspath("wiremock")` (or stub in code, as `MusicBrainzClientTest`
  does). Record a response with
  `curl -G -A "Sona/0.1.0 ( jonathan@vusich.cloud )" https://musicbrainz.org/ws/2/recording --data-urlencode 'query=…' --data-urlencode fmt=json`.
- **Audio fixtures:** prefer `FlacBuilder` (`flac().tag("TITLE", "…").build()`) to generate tiny synthetic FLACs
  in-test. `FlacSample` exposes real-world files trimmed to their metadata blocks, with the audio frames removed.
- Unit tests (parser, `PathSegment`, query building, client, resolver) don't need Docker.
- **E2E tests** (`src/test/java/org/sona/e2e`, `@E2ETest`) drive the built frontend in a real browser with
  [Playwright for Java](https://playwright.dev/java/), as a user would: Firefox locally, and Chromium, Firefox and
  WebKit on CI (`-Pe2eBrowser=<name>`). The browsers run in Microsoft's Playwright image, started by Testcontainers
  (`RunningBrowser`), so nothing is installed on the machine and every machine tests the same Linux builds; keep
  `playwrightVersion` in `build.gradle.kts` as the one place the client and image versions are set. `@E2ETest` is an `@IntegrationTest` whose server serves the bundled
  frontend as production does, over HTTPS with a self-signed localhost certificate (WebKit won't keep the `Secure`
  refresh cookie over plain HTTP), and gives each test method a fresh browser profile: take a `Page`, or a
  `BrowserContext` for several tabs that share cookies. Access tokens last ten seconds, so token refreshes happen
  during tests. Don't create a user named `admin`: the server creates one at startup. Seed data with `TestUsers` and
  the DAOs, then do everything else through the page. Drive pages through page objects (`LoginPage`, `SettingsMenu`, …), find elements by role and label, and use
  Playwright's `assertThat`, which waits. They're tagged `e2e` and only run in `:server:e2eTest`, which bundles `web/`. A failing test's trace is saved to `server/build/e2e-traces`;
  open it with `npx playwright show-trace <file>`.
- GitHub Actions (`.github/workflows/ci.yml`) generates the jOOQ classes against a Postgres service, builds and tests
  every module, type-checks and builds `web/`, and runs the E2E tests on every PR and push to `main`.

## Adding a feature

Work on one feature at a time, and finish it before starting the next. Within a feature, the backend comes first:
build it, get it reviewed and merged, and only once it's settled start the frontend (or anything else that builds on
it). Don't start work ahead of time that will have to be rebased or redone when the work under it changes, above all
frontend work on top of an API still in review.

1. Check [docs/VISION.md](docs/VISION.md); ask about anything it doesn't settle. Agree each page's data with the
   maintainer before writing its queries: add no DAO method without an approved use case, and don't infer one from
   the mocked pages in `web/`.
2. Build the backend with unit and `@IntegrationTest` tests. Once it's merged, build the frontend against the real API.
3. **Every feature a user can see or do ships with E2E tests in the same PR**: an `@E2ETest` class (or new methods in
   the class for that area) covering the main flow and the errors a user can hit. Add a page object for any new page.
   A backend-only change with no frontend yet is covered by integration tests, and gets E2E tests in the PR that adds
   its UI.
4. Run `./gradlew :server:test :server:e2eTest` before opening the PR.

## Code style

Match the surrounding code. The conventions below are consistent across the codebase:

**Language**
- Java 25. Use modern features: records for data, sealed interfaces + pattern-matching `switch` for
  closed hierarchies (`Block`, `TagValue`), unnamed variables (`_ ->`), `List.getFirst()`, `Stream.toList()`.
- Compiler warnings fail the build (`-Werror`, with every `-Xlint` check but `processing` and `serial`: Sona serializes
  with Jackson, so classes need no `serialVersionUID`). Fix warnings rather than suppressing them.
- `final var` for locals; `final` on parameters in implementation classes.
- Classes are `final` unless designed for extension or proxied by Spring AOP (`MusicBrainzClient` isn't, so
  `@RateLimiter` can apply). Records over Lombok `@Value` for new data types.
- Exhaustive `switch` expressions over enums instead of if/else chains; throw on the `default` branch when
  input is untrusted (see `FlacParser.getBlockType`).
- Static-import small helpers (`uuidv7`, `Map.entry`, `Collectors.toMap`, jOOQ table constants).
- Comments are sparse and explain *why* or reference a spec (RFC section, MusicBrainz/Picard behaviour).
  Use `// TODO:` for known gaps.

**Data flow**
- Methods build and return their results. Do **not** pass mutable collections (lists, sets, maps, deques)
  into a method for it to fill in or modify.
- Prefer immutable collections (`List.of`, `toList()`, `toUnmodifiableMap`) and immutable jOOQ pojos.
- Return `Optional` for "may not be found" results from resolvers/lookups.
- **Avoid combinatorial explosion.** Give each case callers need its own method, function or component, rather than
  one that takes nullable, optional or mode arguments and branches on which it was given: `getAsUser(accessToken)` and
  `postJson(body)` rather than `send(accessToken | null, body | null)`, `playTracks(tracks)` and
  `playListedTrack(tracks, index)` rather than `playTracks(tracks, start = 0)`, `ReleaseGroupGrid` and
  `DiscographyGrid` rather than one grid with a `subtitle` mode. Share the common work in helpers that take only what
  every case has. `null` is for data that can genuinely be absent, never for picking a case.

**Spring**
- Interface + `Default*` / provider-named implementation (`IngestionEngine` → `DefaultIngestionEngine`,
  `MetadataResolver` → `MusicBrainzResolver`).
- Constructor injection via Lombok `@RequiredArgsConstructor` on `private final` fields; no field `@Autowired`.
- Configuration is bound to `@ConfigurationProperties` records in `config.properties`.
- Lombok usage is limited to `@RequiredArgsConstructor`, `@Getter` (enums), `@Builder`, `@UtilityClass`, and `@Slf4j`,
  which every class that logs uses (never a hand-written `LoggerFactory.getLogger` field).

**Database**
- Use jOOQ's typed DSL with generated `Tables.*` constants; keep queries in `db/` DAOs.
- Map between jOOQ records and pojos with MapStruct (`@Mapper(componentModel = "spring")`), and build API responses
  from pojos with MapStruct too. DAOs return their table's pojos, not per-caller column picks (`Records.mapping`,
  `fetchInto`).
- Each DAO reads and writes one type: its table's pojo, or one composite record read with joins, which gets its own
  DAO (`ReleaseDetailsDao`).
- DAO methods share one set of verbs: `load…` for reads (`loadById`, `loadByMusicBrainzId`; never `find…`),
  `insert`, `upsert` / `upsertAll`, `update…` and `delete`. A read that may find nothing returns `Optional`, and one
  whose row must exist returns the pojo. Every DAO method has a caller in `main`: delete one when its last caller goes,
  along with any index that only it used.
- Each page has one endpoint, which reads everything the page shows in one query: joins for single rows, `multiset`
  for lists (`ReleaseDetailsDao.loadById`). Don't chain lookups by ID and stitch the results together in Java.
- Only a table's own mapper sees its jOOQ records. A composite read converts every table it selects to its pojo as
  it's read (`RELEASE.convertFrom(releaseMapper::fromRecord)`, and `multiset(...).convertFrom(rows -> rows.map(...))`
  for lists), and its composite mappers build their results from pojos only (`fromPojos(Medium medium,
  List<TrackWithArtist> tracks)`).
- Ingestion loads each row a track needs, or resolves it from MusicBrainz and upserts it when it isn't stored, the same
  way for every kind of row (release, medium, artist).
- Model relationships on the MusicBrainz database when in doubt (a track is on a medium of a release, and has its
  own artist), keeping only the subset Sona needs.
- Counts derived from other tables are columns kept up to date by a trigger (`release.track_count`), not counted
  per query.
- Every query is served by an index (the scale target is 500k+ tracks); the integration tests enforce it.
- Migrations are lowercase SQL, `create table if not exists`, `uuid` primary keys named `<table>_id`, one table per
  migration.
- Every column is `not null` unless a row can genuinely lack the value (MusicBrainz doesn't know it, the row
  isn't in MusicBrainz at all, or it doesn't exist yet). Say why in a comment on each nullable column.

**Tests**
- JUnit 5 + AssertJ (`assertThat`). Parameterise over fixtures with `@EnumSource`.
- Test classes and methods are package-private; method names describe behaviour (`readAndStoreIngestTrack`).
- Put tests that need Spring/DB/network behind `@IntegrationTest`; keep parser tests as plain unit tests.
- User-facing flows get an `@E2ETest` (see *Adding a feature*).

**Frontend (`web/`)**
- Name API types, constants and functions after their backend counterparts: `ApiRoutes`, `ErrorCode`,
  `*Request` / `*Response` types in `web/src/api/`, verb-noun functions (`refreshSession`, `hasPermission`).
- Every closed set of values is an enum, written as a const object with a type of the same name, and code uses its
  members, never the raw strings:
  `export const Theme = { SYSTEM: "system", ... } as const;` then
  `export type Theme = (typeof Theme)[keyof typeof Theme];`. No string-literal union types.
- Discriminated unions are flat: one variant per case, each a `kind` from an enum plus its own fields, never a variant
  that wraps another union. `ApiResult<T>` is `OK` with a `body`, `REFUSED` with Sona's `code` and `reason`, or `FAILED`
  with a `reason`, rather than a success or a failure holding a union of failures.
- Page paths come from `AppRoutes` in `web/src/routes.ts`, and paths with an ID from
  `routeTo(AppRoutes.ARTIST, artist.id)`, never from string literals.
- No casts (`as T`) or non-null assertions (`!`); narrow instead.
- async/await throughout, with no `.then` chains or `void` calls. Only the shared API client (`api/client.ts`) and
  storage helper (`storage.ts`) use `try`/`catch`; API calls return an `ApiResult` that callers branch on.

**Dependencies and parsers**
- **Ask before adding any new dependency.** Latest JDK and Spring release candidates are acceptable.
- Audio parsers and writers are **hand-written on purpose**. Don't swap in jaudiotagger or similar.
  Tag rewrites must leave audio data byte-for-byte identical.

**Git / PRs**
- Agents may create branches, commit, push and open PRs for review.
- Branch per feature, PR into `main` using `docs/pull_request_template.md` (Summary / Details / Reviewers).
- Short imperative commit subjects.
