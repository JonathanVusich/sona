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
anything.

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
POST ingest group with all its files (IngestController) ──► ingest_group (CREATED) + a track_to_ingest row (CREATED) per file
PUT each file to /api/ingest-tracks/<its ID> ──► Library.storeIngestTrack ──► UPLOADING, then UPLOADED + file in <ingestFolder>/<group>/<trackId>
   the last file to arrive moves its group to UPLOADED                  │
IngestWorker (every second, oldest UPLOADED group first) ◄───────────────┘ (marks the group MATCHED when done)
   │  runs the IngestionEngine's steps on each file; whatever a step throws, the worker catches for that file
   │  ──► state = FAILED (MOVE_FAILED for the move), and goes on with the rest
   ▼
IngestionEngine (parseTrack, resolveTrack or resolveAlbum, storeTrack or requireInput, moveTrack)
   1. pick a sonaudio Parser by file extension (case-insensitive)
   2. Parser.parse(SeekableByteChannel) ──► RawMetadata = tags + duration, and the audio's Fingerprint,
        decoded from the same channel (FlacDecoder, Mp3Decoder). The whole audio is decoded once to measure its duration (`AudioUtils.measureDuration`), which
        replaces the header's, as a bad encoder can leave that wrong.
   3. MetadataResolver.resolveTrack ──► AcoustID lookup of the fingerprint, then MusicBrainz (client/) ──► ResolvedTrack
        ├─ close AcoustID match ──► its recording, whatever the tags say, if its length is within 3 seconds of the audio's
        └─ otherwise ──► MusicBrainz search by the tags: each recording is scored (its search score, +10 if AcoustID
        │                  matched it loosely, -30 on a release whose title isn't the album tag's, -50 if its length
        │                  isn't within 3 seconds of the audio's, or is unknown) and the best is taken if it scores 90
        │                  or more
        └─ none that high ──► the best five go in `ingest_candidate`, state = INPUT_REQUIRED, file stays in the ingest area
   4. store the rows the track references: each is loaded, or resolved from MusicBrainz and upserted if it
        isn't stored (release with its group, labels and media; the track's medium; the track's artist)
   5. Library.libraryPath ──► <release group id>[name]/<release id>[name]/<track id>[title].<ext>
        (Sona's own UUIDv7 database IDs, not MBIDs, which a track may not have)
   6. insert the `track` row, then Library.importTrack moves the file into <mediaFolder>, state = COMPLETED
        └─ move fails ──► state = MOVE_FAILED: the track is stored, and only its file is missing from the library
```

A track is only in the library once its ingest is `COMPLETED`, as its row is stored before its file is moved: the
pages list only those tracks, and `release.track_count` counts only them.

Each row commits on its own, and the upserts are idempotent, so processing an ingest again after a failure reuses
what was stored. A release is stored with its labels and media in one transaction (`ReleaseDao.upsert`), so a stored
release always has them. A release group is stored before its release, and a release before its tracks, so a release with no
track (`release.track_count` is 0), and a group with no such release (`release_group.release_count` is 0), isn't in
the library yet: the pages and lists leave them out.

| Package | Responsibility |
|---|---|
| `engine` | `IngestionEngine` / `DefaultIngestionEngine`: the steps of an ingest (parse, resolve, store, move), which catch nothing. Holds the extension → `Parser` registry. `IngestWorker` / `DefaultIngestWorker`: a `@Scheduled` pass every second (`sona.ingest-worker.cron`) processes the pending ingests oldest first, once every file of their group is uploaded (the group is `UPLOADED`, and is `MATCHED` afterwards), an `ALBUM` group together (each file parsed, the album resolved through `resolveAlbum`, then each file stored and moved) and the files of an `UNKNOWN` group one by one, catching what each file's steps throw to record its state, and does nothing when there are none, so ingests left pending by a restart are picked up too. Tests turn the schedule off (`-`) and run passes themselves. Every state an ingest is given is pushed to its uploader through `IngestProgressPublisher` (see `controller`). |
| `dev.javax.sonaudio` | Not a package of Sona's: audio parsing, decoding and fingerprints come from [sonaudio](https://github.com/JonathanVusich/sonaudio) (`dev.javax:sonaudio`), the author's own library, which grew out of Sona. Its `FlacParser` and `Mp3Parser` read a file's tags (`RawMetadata`, `Tag`, `TagValue`) through a `SeekableByteChannel` and hand out its decoded audio (`PcmAudio`, sealed: `IntPcmAudio` or `FloatPcmAudio`); `AudioUtils.measureDuration` decodes all of it, `Fingerprint` its first two minutes as AcoustID's `fpcalc` does, and `FlacWriter` writes the FLAC files tests use. A malformed file throws its checked `InvalidFormatException`. Change the audio code in sonaudio, not here. |
| `metadata.resolver` | `MetadataResolver` / `MusicBrainzResolver`: turns `RawMetadata` and a `Fingerprint` into a `TrackResolution`: `Matched` with a `ResolvedTrack`, or `NeedsReview` with ranked `TrackCandidate`s (none if nothing was found). `resolveAlbum` resolves the files of an `ALBUM` group together: each file's recording on each release it would be imported on without review is counted, the release most files are on is chosen (ties go to the better scores, then MusicBrainz's order), and every file on it is matched there, while one that isn't on it needs review. A close AcoustID match (score 0.9 or more) settles the recording, looked up on MusicBrainz; otherwise MusicBrainz is searched by the tags and its hits are scored (see the pipeline above); a recording is a candidate once, on the release that best suits the album tag. A recording's length must be within 3 seconds of the audio's duration to be accepted without review; `MusicBrainzTag` lists the Picard `musicbrainz_*` tag names. |
| `client` | `MusicBrainzClient`: thin wrapper over a Spring `RestClient` for the MusicBrainz WS/2 API. `MusicBrainzConfig` builds the `RestClient`. Requests are rate limited with Resilience4j's `@RateLimiter(name = "musicbrainz")` on the client, configured under `resilience4j.ratelimiter.instances.musicbrainz` in `application.properties` (about one request a second). `client.model` holds response records mirroring MB JSON (kebab-case via `@JsonProperty`). `AcoustIdClient` looks fingerprints up in the AcoustID v2 API (`acoustid.url` → `AcoustIdProperties`, its `RestClient` from `AcoustIdConfig`; Sona's API key is a constant in the client), rate limited to three requests a second (`resilience4j.ratelimiter.instances.acoustid`); its responses are in `client.model.acoustid`. |
| `library` | `Library` / `DefaultLibrary`: owns the filesystem layout for the ingest staging area and the media library. Library paths are stored in the DB relative to the media folder, '/'-separated. `PathSegment` formats sanitised `uuid[name]` names. |
| `auth` | Authentication and permissions. Every API request carries an access JWT that Sona issued, and `SonaJwtConverter` turns it into a `UserAuthentication` straight from its claims (username, role, session state), with no database lookup. `Permission` maps each role to its permissions (Spring authorities like `library:read`, checked with `@PreAuthorize`). A session whose password must be changed may only see that it must (`ActiveUserManager`). `TokenIssuer` / `DefaultTokenIssuer` issue access JWTs and rotate refresh tokens, which travel in an HttpOnly cookie (`RefreshCookieService`); the database is checked again on each refresh. `PasswordChecker` checks local users' passwords at login and changes them. `InitialAdminRunner` (every profile except `dev`) and `DevUserRunner` (`dev`) create the default accounts. Each user is `LOCAL` or `OIDC` (`users.type`); `OidcDiscoveryClient` holds Sona's client registrations at the configured OIDC providers, discovered from each issuer on first use. OIDC users sign in with Spring's OAuth2 login (authorization code flow with PKCE) at `/api/auth/oidc/authorize/<id>`, and `SignInHandler` finishes the callback through `AccountRegistrar`: a known user gets a session, and a new one is sent to the frontend's `register` page with a `RegistrationTokenService` token to choose a username (`POST /api/auth/register`). With `?prompt=none` (`SilentSignInResolver`) the provider signs a user who still has a session there straight back in, or answers that they must sign in (code 108); the provider last used is remembered in a cookie the frontend reads. An OIDC user's Sona ID is a UUIDv5 of their issuer and subject, so it is known without a lookup. The provider's refresh token is kept per session (`oidc_session`), and `ProviderClient` checks it with the provider on every refresh, given the user's identity from the account the refresh loads, so a user the provider disables loses access then. Audio elements can't send a header, so a track is streamed with a `StreamTokenService` token for that one track, issued to users who may read the library and sent as the URL's `access_token` parameter; the stream path has its own filter chain in `SecurityConfig`, where `StreamAccessManager` checks that the token is for the track asked for, and stream tokens have their own issuer, so the API never takes one as an access token. STOMP connections send the access token in the CONNECT frame, where `SocketAuthenticationInterceptor` reads it, and Spring Security's messaging support (`SocketSecurityConfig`) checks each message: a subscription needs its destination's permission, and clients may send nothing. |
| `db` | DAOs over jOOQ, one per feature: `ReleaseDao` writes releases with their labels and media and reads the release page, `ReleaseGroupDao` writes release groups and reads the albums list, paged by name, and the release group page, `ArtistDao` writes artists and reads the artists list and the artist page, `PlaylistDao` writes and reads everything about playlists, `PlayDao` keeps users' play history (one row per play) and reads their plays a page at a time, `QueueDao` keeps each user's play queue (`queue`: current entry, seconds into it, shuffle and repeat modes, and a `version` every change raises; `queue_track`: the entries, each with its `position` as queued and its `shuffle_order`, a random order made when the queue is filled, so that turning shuffle on or off changes no entry: a shuffled queue plays from its `shuffle_anchor` to the last entry and wraps round to the ones before it) and locks the queue's row for each change, filling it from a playlist, a release or a list of tracks in one statement, `UserDao` keeps users and how they sign in, storing each with their password or OIDC identity and reading them as an `Account` (a `LocalAccount` or an `OidcAccount`), `SessionDao` keeps sessions (refresh token families, and the provider's refresh token behind an OIDC user's session, stored with the session's first token and returned with each token a refresh revokes), `IngestDao` keeps ingest groups, the ingest of each file, and the candidates of those that need input, and `TrackDao` keeps tracks. `ReleaseFields` reads a release's labels, and its media with their tracks, inside the queries of the release and release group pages, and `TrackFields` holds what every read of tracks shares: whether a track is in the library, and a track as lists show it. Return lists, not open `fetchStream()` cursors. |
| `mappers` | MapStruct mappers: one per table, between its jOOQ records and pojos (`TrackMapper`), and one per API area, from the DAOs' results to responses (`ReleaseResponseMapper`). |
| `config` | Spring `@Configuration` classes (the MusicBrainz `RestClient` and URI builder, `SecurityConfig`, the per-start `SigningKeyConfig`, `WebSocketConfig` (STOMP over WebSocket at `/api/socket`, with Spring's in-memory broker), and `WebFrontendConfig`, which serves the web frontend bundled into the boot jar under `static/`, answering every non-API path it has no file for with `index.html`) and `config.properties` (`@ConfigurationProperties` records, found by `@ConfigurationPropertiesScan`). |
| `org.sona.model` | **Generated by jOOQ** (`Tables`, `tables.*`, `tables.pojos.*`, `enums.*`). Don't put hand-written classes here. Note `client.model.Track` (a MusicBrainz track) is unrelated to `model.tables.pojos.Track` (a DB row). |
| `controller` | REST controllers: `AuthController`, `OidcController`, `UserController`, `ReleaseController` (the release page), `ReleaseGroupController` (the albums list, paged by name, and the release group page), `ArtistController` (the artists list, paged by name, and the artist page), `TrackController` (a track's stream token, and its file as stored, which Spring serves in the parts range requests ask for), `PlaylistController` (the signed-in user's own playlists: listing, creating, renaming and deleting them, and adding, removing and moving their tracks; another user's playlist is answered as not found; `PlaylistDao` locks the playlist's row for each change, and positions are unique only at commit, so a move shifts the tracks it passes in one statement), `QueueController` (the signed-in user's own play queue, of any length: it is read as its state (with the current entry) and then the entries a page at a time in play order (`after`, or `earlier` with `before`, 20 by default), as a playlist can be as long as it likes. Playing a playlist or release replaces it with the source's tracks, copied in SQL by `QueueDao` so no request carries them, and playing a list of at most 1000 track IDs does the same; the entries, which the player moves between and reports its position in, are the user's own; shuffle starts the random order at the current entry and turning it off goes back to the queued order, both by flipping the mode), `PlayController` (the signed-in user's own play history: the player reports a play once it has played half a track or four minutes, and the plays are listed latest first, repeats included, a page at a time (`after`, 20 by default)), `IngestController` (uploads: once the uploader has settled its groups, it creates each ingest group, `ALBUM` for files that belong together or `UNKNOWN` for loose ones, with the names of all the files it will send (their formats are checked then), and Sona gives the group and each file an ID and a `CREATED` state; it then `PUT`s each file to `/api/ingest-tracks/<its ID>` as its own request, its raw bytes as the body, which may be sent again until it is stored (a file is `UPLOADING` meanwhile, and `UPLOADED` once stored, and when none of a group's files is left to upload the group is `UPLOADED` and matching can begin), up to `library.maxUploadSize`, which `/api/users/me` tells clients; matching uses the group as a hint for the release), `JwksController` (Sona's public signing key), and a stub `MetadataController`. Over the socket, an uploader subscribed to `/user/queue/ingests` gets each new state of the ingests in the groups they created (`ingest_group.user_id`); user destinations go by user ID (`UserAuthentication.getDestinationUserName`). Route paths are constants in `ApiRoutes`. Every client error has an `ErrorResponse` body: a three-digit `code` from `ErrorCode`, which clients branch on and which never changes once released, and a `reason` for people. Controllers and the services they call throw domain exceptions (see `exception`), never error codes, and `ApiExceptionHandler` is the one place that answers each with its `ErrorCode`, as it does a parameter that isn't the type an endpoint takes, or breaks its Jakarta validation constraints, with `INVALID_PARAMETER`; the security filters write theirs through `ErrorResponseWriter`. |
| `utils` | `IDGenerator`: `uuidv7()` for rows that are ordered by ID, `uuidv5(name)` for IDs worked out from a name (an OIDC user's ID comes from their issuer and subject). `DateUtils`. |
| `exception` | Domain exceptions. `SonaException` is the sealed base of every way Sona refuses a request (`ReleaseNotFoundException`, `InvalidCredentialsException`, `UsernameTakenException`, …), and `ApiExceptionHandler` maps each with an exhaustive `switch`, so a new one doesn't compile until it has an `ErrorCode`. Parsing throws sonaudio's checked `InvalidFormatException`, which `ApiExceptionHandler` answers too. |

### Persistence

- **Postgres** schema `sona`, managed by **Flyway** migrations in `server/src/main/resources/db/migration`.
  Migrations use the placeholders `${schema}` and `${user}` (set in `build.gradle.kts`).
- **Flyway runs on application startup** (configured in `application.properties`) and from Gradle
  (`flywayMigrate`, used before jOOQ codegen). Keep the placeholders in both places in sync.
- **jOOQ** code is generated from the live database into `server/build/generated-src/jooq/main`
  (package `org.sona.model`) as immutable record pojos plus interfaces. `jooqCodegen` depends on
  `flywayMigrate`. `compileJava` does **not** run codegen, so after changing a migration you must run
  `:server:jooqCodegen` yourself or you will compile against stale generated classes.
- Migration versions are whole numbers (`V5__CreateIngestGroupTable.sql`), never `V4_1__`. A new table that others
  must reference goes in at the version it needs, and the migrations after it are renumbered.
- Until the initial release, edit existing migrations in place and recreate the local database
  (`docker compose down`, then `up -d`) rather than adding new versions. After release, never edit a migration
  that has been applied; add a new `V<n>__Description.sql` instead.

### Configuration

- `server/application.properties` imports `file:config.yaml` **relative to the working directory**, so run
  the server and tests from `server/` (Gradle does this for you).
- `library.ingestFolder` / `library.mediaFolder` / `library.maxUploadSize` → `LibraryProperties`; `musicbrainz.url` → `MusicBrainzProperties`;
  `acoustid.url` → `AcoustIdProperties`;
  `auth.*` (token lifetimes, `frontendUrl`, the `oidcProviders` users can sign in through) → `AuthProperties`.
- `bootRun` activates the `dev` profile, which creates `admin`/`admin` and `user`/`user`, and stores a few releases
  (`DevLibraryRunner`, whose tracks have no audio files) and logs each one's page. Any other profile gets
  `admin`/`admin` on an empty database, who must change the password first.
- The MusicBrainz rate limit is in `application.properties` (`resilience4j.ratelimiter.instances.musicbrainz.*`).
- The datasource is Spring Boot's (`spring.datasource.*` in `application.properties`, local dev credentials).
  The `flyway` / `jooq` blocks in `build.gradle.kts` repeat them for build-time codegen; keep them in sync.

## Building and testing

Prerequisites: JDK 25, Docker, Node 22 (for `web/`, which the boot jar bundles, and the E2E tests). (`dev.javax:sonaudio`, the audio library, is the author's own, published to Maven Central.)

```bash
docker compose -f server/docker-compose.yml up -d     # start Postgres
./gradlew :server:flywayMigrate :server:jooqCodegen  # apply migrations + regenerate jOOQ classes
./gradlew :server:compileJava
./gradlew :server:test                               # integration tests start their own Postgres via Testcontainers
./gradlew :server:test --tests 'org.sona.library.PathSegmentTest'     # unit test, no Docker needed
./gradlew :server:e2eTest                            # builds web/, then drives it in Firefox against a test server
./gradlew :server:e2eTest -Pe2eBrowser=webkit        # or chromium; CI runs all three
./gradlew :server:bootRun                            # dev profile: admin/admin and user/user
npm --prefix web run dev                             # frontend on :5173, proxying /api to bootRun on :8080
./gradlew spotlessApply                              # format the Java sources (palantir-java-format)
npm --prefix web run format                          # format the frontend (Prettier)
```

Formatting is the formatters' job, not a reviewer's: Spotless formats the Java sources with palantir-java-format, and
Prettier formats `web/`, both at 120 columns. `gradle build` fails on Java that `spotlessApply` would change, and CI
runs `npm run format:check`, so run both before committing.

The docker-compose database is only needed for `bootRun` and jOOQ codegen; tests don't use it.

Test notes:

- `@IntegrationTest` (in `src/test/java/org/sona`) = `@SpringBootTest(webEnvironment = RANDOM_PORT)`
  + `TestcontainersConfig` (throwaway `postgres:18`, migrated by Flyway on startup) + `TestLibraryConfig`
  (in-memory Jimfs library folders, MusicBrainz pointed at a WireMock server) + `TestAuthConfig` (users can sign in
  through a `FakeOidcProvider`, a WireMock OIDC provider that signs ID tokens and answers token requests). Tests
  commit, and `TruncateTablesExtension` empties every table afterwards. `TestQueryPlanConfig` EXPLAINs every jOOQ
  query with sequential scans off, and `IndexedQueryListener` fails the test if any table of Sona's is scanned in
  full, so a query without an index fails the first test that runs it. `TestSingleQueryConfig` fails any `GET` under
  `/api` (except `/api/auth`, where signing in writes) that runs a second query (`SingleQueryListener`), in the E2E
  tests too, so every page is read in one. Needs Docker.
- HTTP tests call the real port through `ApiClient` (a Spring `RestTestClient`), so they pass through the security
  filters. `TestTokens` signs access tokens as Sona does, and `TestUsers` creates local users whose password is their
  username.
- **MusicBrainz and AcoustID are never called live.** Tests stub them with [WireMock](https://wiremock.org). Mapping
  files in `src/test/resources/wiremock/mappings` match exact queries and return responses recorded from the live API
  into `src/test/resources/wiremock/__files`; unmatched requests get a 404, except AcoustID lookups, which a
  low-priority mapping answers with no match. A test that needs a fingerprint match stubs one above it at priority 1
  (`MusicBrainzResolverTest`). Unit tests register a
  `WireMockExtension` with `usingFilesUnderClasspath("wiremock")` (or stub in code, as `MusicBrainzClientTest`
  does). Record a response with
  `curl -G -A "Sona/0.1.0 ( jonathan@vusich.cloud )" https://musicbrainz.org/ws/2/recording --data-urlencode 'query=…' --data-urlencode fmt=json`.
- **Audio fixtures:** `FlacFiles.flacFile(metadata)` writes ten-second FLAC files with the tags given, whose audio is
  the silence of `FlacAudioSample.SILENCE_10_SECONDS`, so they decode and can be fingerprinted as ingestion does.
  `Mp3Sample` has the MP3s ingestion tests upload. How files parse and decode is sonaudio's to test, not Sona's.
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

Match the surrounding code. Layout (indentation, line length, wrapping) is the formatters' (see *Building and
testing*): run them rather than formatting by hand. The conventions below are consistent across the codebase:

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
- Everything a parser or decoder hands out is immutable (frames, tags, blocks, headers): its constructor
  copies nothing, so the caller hands it arrays and collections it made for it and won't touch again, and copies a
  working buffer it still holds first, as a decoder does into a new frame for each one it decodes. It never exposes an
  internal array or collection. Data in different representations (integer and float samples) is a type each,
  and code keeps the specific type rather than switching on a base type and throwing for the kinds that can't happen.
- Return `Optional` for "may not be found" results from resolvers/lookups.
- **Delete dead code in the change that makes it dead.** When a change stops using a method, field, constant, class,
  exception, error code, route or index, remove it with its tests and docs; search for what the change left unused
  before finishing. Code already unused when you find it goes too. Error codes may be renumbered until the first
  release.
- **No try/catch below the boundary; exceptions propagate.** A request's exceptions go up to `ApiExceptionHandler`,
  and a background job's to its scheduled entry point (`DefaultIngestWorker`), which catches once per unit of work
  (each file), records what the failure means (its state), and goes on with the rest. Engines, services, DAOs and
  libraries never catch to mark, log, translate, clean up or retry: no mark-then-rethrow, no catch that turns one
  exception into another, no catch that deletes a partial file (write so the next attempt overwrites it). A step whose
  failure means something different (a failed move is `MOVE_FAILED`) is its own method, so the boundary catches it
  separately. The one catch allowed below the boundary is where an interface can't throw checked exceptions
  (`Iterator.next` in the decoders). Where an API reports an expected outcome by throwing, prefer one that doesn't
  (`insert ... on conflict do nothing` over catching `DuplicateKeyException`). At the boundary, catch the exceptions
  the calls throw, never `Exception` or `Throwable`.
- **Declare only what the body throws.** A `throws` clause lists the checked exceptions the method can throw, and goes
  when the last call that throws one does (`InterruptedException` on a resolver whose HTTP client throws none).
- **Avoid combinatorial explosion.** Give each case callers need its own method, function or component, rather than
  one that takes nullable, optional or mode arguments and branches on which it was given: `getAsUser(accessToken)` and
  `postJson(body)` rather than `send(accessToken | null, body | null)`, `playTracks(tracks)` and
  `playListedTrack(tracks, index)` rather than `playTracks(tracks, start = 0)`, `ReleaseGroupGrid` and
  `DiscographyGrid` rather than one grid with a `subtitle` mode. Share the common work in helpers that take only what
  every case has. In the frontend, `null` is for data that can genuinely be absent, never for picking a case. On the
  backend an optional parameter may be null and passed straight through (`@RequestParam(required = false) UUID after`
  for the first page), with its doc comment saying what null means.

**Spring**
- Use Spring's own components wherever one does the job, before writing your own: Spring Security's messaging support
  for STOMP authorization, not a hand-written interceptor that checks destinations; `@PreAuthorize`, Jakarta
  validation, `@ConfigurationProperties`. Write your own only for what Spring leaves to you (reading Sona's access
  token from a CONNECT frame), and check the Spring docs for a standard way before you do.
- Interface + `Default*` / provider-named implementation (`IngestionEngine` → `DefaultIngestionEngine`,
  `MetadataResolver` → `MusicBrainzResolver`).
- Constructor injection via Lombok `@RequiredArgsConstructor` on `private final` fields; no field `@Autowired`.
- Configuration is bound to `@ConfigurationProperties` records in `config.properties`.
- **Controllers don't decide on errors.** They call the service and return its result: no checks that throw, no
  translating a result enum or an empty `Optional` into a response. The code that finds the problem throws a domain
  exception (`InvalidFormatException` from the engine, `IncorrectPasswordException` from `PasswordChecker`), and
  `ApiExceptionHandler` answers it. The one thing a controller may throw is not-found from a DAO's `Optional`
  (`.orElseThrow(() -> new ReleaseNotFoundException(releaseId))`), since DAOs return `Optional` by convention.
- Endpoints validate their input with Jakarta validation annotations (`@Min`, `@Max`, `@NotBlank`, `@Valid` on request
  bodies), not checks written out in the controller; `ApiExceptionHandler` answers a violation with `INVALID_PARAMETER`.
- Lombok usage is limited to `@RequiredArgsConstructor`, `@Getter` (enums), `@Builder`, `@UtilityClass`, and `@Slf4j`,
  which every class that logs uses (never a hand-written `LoggerFactory.getLogger` field).

**Database**
- Use jOOQ's typed DSL with generated `Tables.*` constants; keep queries in `db/` DAOs.
- Map between jOOQ records and pojos with MapStruct (`@Mapper(componentModel = "spring")`), and build API responses
  from them with MapStruct too. A DAO returns a table's pojo when the caller needs the row (`loadById`, `upsert`). A read
  that only shows part of a row, above all a list or a page, **selects just the columns it shows** and never a whole
  row it then drops most of: see the next rule. Don't use `fetchInto`.
- One DAO per feature: a feature's tables, and the joined reads of the pages that show it, share one DAO named for
  the feature (`PlaylistDao` writes `playlist` and `playlist_track`, and reads the playlists list and a playlist's
  page). Don't split them into a DAO per table and a DAO per composite read.
- DAO methods are named for what they do to the DAO's main table, which their names leave out (`TrackDao.loadById`,
  not `loadTrackById`). The feature's other tables are read only inside the main table's joined reads (`multiset`),
  and written inside its writes (a group inserted with its files), never loaded through methods of their own; where
  the feature changes them on their own, the method names what it does to them (`PlaylistDao.insertTracks`,
  `setTrackPosition`). Every DAO uses the same verbs:
  - `loadById` / `loadBy<Key>` for a whole row, `load<Shape>` for a page or a projection (`loadDetails`,
    `loadSummaries`, `loadFirstPage` / `loadNextPage`), `load<State><Rows>` for a batch (`loadUploadedGroups`), and
    `has…` for a boolean (`hasNoUsers`). Never `find…`, `get…` or `fetch…`. A read that may find nothing returns
    `Optional`, and one whose row must exist returns the pojo.
  - `insert` for a new row, which throws on a conflict, and `insertIfAbsent` for one that may already exist
    (`on conflict do nothing`), returning whether it was stored.
  - `upsert` / `upsertAll`.
  - `set<Field>` to write a column, an enum state included (`setState`, `setRole`, `setPasswordHash`,
    `setTrackPosition`), and `set<State>` for a change to one state that applies only from certain states or does
    more than write it (`setUploading`, `setUploaded`); `mark<Flag>` to set a boolean.
  - `replace<Rows>` to delete a set of rows and insert their replacements (`replaceCandidates`, `replaceWithTracks`).
  - `delete` / `delete<Rows>`.
  - A verb of the domain for an operation that isn't writing a column (`revokeFamily`, `revokeAll`).

  Never `update…`, which names the statement rather than the change. A write that may find no row returns it as an
  `Optional`. Every DAO method has a caller in `main`:
  delete one when its last caller goes, along with any index that only it used.
- **Query for the rows you want; never load rows and filter or check their state in Java.** The state goes in the
  `where` (`IngestDao.loadUploadedGroups`, `UserDao.loadActiveById`), not in a `filter(...)` or a `switch` over
  `state()` on rows loaded anyway, and not in a static helper that checks a loaded row. A change that only applies in
  some state, because another request can change the row meanwhile, is one conditional `update ... where state = ?
  returning`, not a load, a check and an update, which two requests can both pass. Never load the row again to
  explain why a write didn't apply: a conflict on insert is the conflict it hit (`UsernameTakenException`), and where
  the reasons differ, the one statement tells them apart (`IngestDao.setUploading` writes the state back unchanged
  when the file is already uploaded, and returns the row, so empty means there is none). A conditional update
  is only for rows another request can change: never one that second-guesses what the code's own callee wrote, such
  as a `setState(id, from, to)` guarding against overwriting the engine's own state (give the steps their own
  methods instead; see *No try/catch below the boundary*). Each DAO call commits on its own, by design, so no
  connection is held across HTTP or file I/O. (Decisions on the identity in the request's token, such as its
  `SessionState`, are not loaded rows.)
- **A DAO method takes the row's own key, never IDs the schema already relates to it.** A file's ingest is found by
  its `track_ingest_id` alone, not by its group's ID as well; read the group ID from the row (`returning()` on the
  update gives it). Likewise a route nests an ID under its parent only when it isn't unique without it: a playlist
  track is `/playlists/{id}/tracks/{trackId}`, but an ingest file is `/ingest-tracks/{trackIngestId}`.
- **Load a whole batch in one query.** A pass over work (the worker's uploaded groups) loads everything it will go
  through once and iterates, not one row per query until none is left.
- **One statement where one will do.** A write of several rows is one statement, the others in data-modifying common
  table expressions (`UserDao.insert` stores the user and their password together, `PlaylistDao.deleteTrack` deletes
  the track and shifts those after it), and a check that refuses a write is returned by the statement that takes the
  lock (`QueueDao.replaceWithPlaylist`). Every part of a statement reads the same snapshot, so split only where the
  next statement must see what the last did: work after a row lock that must see the commit it waited for
  (`IngestDao.setUploaded`), reading rows just written, or a delete and insert that a non-deferrable unique key would
  make clash (`IngestDao.replaceCandidates`). Comment why where you split.
- Each page has one endpoint, which reads everything the page shows first in one query, for a fast first paint: joins
  for single rows, `multiset` for lists (`ReleaseDao.loadDetails`). Don't chain lookups by ID and stitch the
  results together in Java. Secondary sections and menus (a release's **More by** and **Other editions**) load after
  it, from the endpoints that already serve that data (the artist's, the release group's), rather than slowing the
  page's own query. The tests enforce one query per read (`SingleQueryListener`).
- Only a table's own mapper sees its jOOQ records. A read that needs whole rows converts each table it selects to its
  pojo as it's read (`RELEASE.convertFrom(releaseMapper::fromRecord)`, and `multiset(...).convertFrom(rows ->
  rows.map(...))` for lists). A read that shows only some columns selects exactly those into a record of its own in
  `db/`, nested as its response is (`PlayedTrack(UUID playId, TrackSummary track)`, a nested record selected with
  `row(...).mapping(TrackArtist::new)`), and builds it with `Records.mapping(PlayedTrack::new)`; its response comes
  from that record with MapStruct, which needs no `@Mapping` when the shapes match (`PlayResponseMapper`). Columns or
  conditions that several reads share are written once (`TrackFields.trackSummary`, `TrackFields.inLibrary`).
- Join a row's parents along their foreign keys with jOOQ's path joins (`trackSummary(PLAY.track())` reaches
  `PLAY.track().medium().release()`), never with the `join(...).on(...)` written out. Write a join out only where no
  foreign key leads (a derived table, `unnest`).
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
- Lists Sona serves a page at a time load the next page as the user scrolls, through `InfiniteScroll` at the end of
  the list, everywhere in the app; never a "Show more" button.
- When a page moves to Sona's API, sections whose data Sona doesn't serve yet stay on the page, filled from the
  mocked library (the `placeholder…` functions in `library.ts`), and each is listed in [docs/GAPS.md](docs/GAPS.md).
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
- async/await throughout, with no `.then` chains or `void` calls. Only the shared API client (`api/client.ts`),
  storage helper (`storage.ts`) and audio output (`audio.ts`) use `try`/`catch`; API calls return an `ApiResult` that
  callers branch on.
- Every HTTP call goes through axios in `api/client.ts` (its `onUploadProgress` drives the upload bars), never `fetch`
  or `XMLHttpRequest`. Socket updates come over STOMP with `@stomp/stompjs`: `ingestProgress.ts` keeps one
  connection open while an uploader is signed in. The player (`player.ts`) streams through the one audio element in
  `audio.ts`, fetching each track's stream token as it starts the track.

**Dependencies and parsers**
- **Ask before adding any new dependency.** Latest JDK and Spring release candidates are acceptable.
- Audio parsers and writers are **hand-written on purpose**, in sonaudio. Don't swap in jaudiotagger or similar.
  Tag rewrites must leave audio data byte-for-byte identical.

**Git / PRs**
- Agents may create branches, commit, push and open PRs for review.
- Branch per feature, PR into `main` using `docs/pull_request_template.md` (Summary / Details / Reviewers).
- A PR that changes the UI shows each change in screenshots in its description, taken from the running app.
- Short imperative commit subjects.
