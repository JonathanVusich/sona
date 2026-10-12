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
   4. Library.libraryPath ──► <release group id>[name]/<release id>[name]/<track id>[title].<ext>
        (Sona's own UUIDv7 database IDs, not MBIDs, which a track may not have)
   5. one transaction: insert `track` row, state = COMPLETED, Library.importTrack moves the file
      into <mediaFolder> (moved last, so a failed move rolls back the DB changes)
```

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
| `db` | DAOs over jOOQ (`TrackToIngestDao`, `TrackDao`, `UserDao`, `PasswordDao`, `OidcIdentityDao`, `OidcSessionDao`, `RefreshTokenDao`). Return lists, not open `fetchStream()` cursors. |
| `mappers` | MapStruct mappers between jOOQ records and pojos. |
| `config` | Spring `@Configuration` classes (the MusicBrainz `RestClient` and URI builder, `SecurityConfig`, the per-start `SigningKeyConfig`) and `config.properties` (`@ConfigurationProperties` records, found by `@ConfigurationPropertiesScan`). |
| `org.sona.model` | **Generated by jOOQ** (`Tables`, `tables.*`, `tables.pojos.*`, `enums.*`). Don't put hand-written classes here. Note `client.model.Track` (a MusicBrainz track) is unrelated to `model.tables.pojos.Track` (a DB row). |
| `controller` | REST controllers: `AuthController`, `OidcController`, `UserController`, `JwksController` (Sona's public signing key), and a stub `MetadataController`. Route paths are constants in `ApiRoutes`. Every client error has an `ErrorResponse` body: a three-digit `code` from `ErrorCode`, which clients branch on and which never changes once released, and a `reason` for people. Controllers throw `ClientException(ErrorCode)` and `ApiExceptionHandler` answers it; the security filters write theirs through `ErrorResponseWriter`. |
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
- `bootRun` activates the `dev` profile, which creates `admin`/`admin` and `user`/`user`. Any other profile gets
  `admin`/`admin` on an empty database, who must change the password first.
- The MusicBrainz rate limit is in `application.properties` (`resilience4j.ratelimiter.instances.musicbrainz.*`).
- The datasource is Spring Boot's (`spring.datasource.*` in `application.properties`, local dev credentials).
  The `flyway` / `jooq` blocks in `build.gradle.kts` repeat them for build-time codegen; keep them in sync.

## Building and testing

Prerequisites: JDK 25, Docker. (`dev.javax:bitstream` is the author's own library, published to Maven Central.)

```bash
docker compose -f server/docker-compose.yml up -d     # start Postgres
./gradlew :server:flywayMigrate :server:jooqCodegen  # apply migrations + regenerate jOOQ classes
./gradlew :server:compileJava
./gradlew :server:test                               # integration tests start their own Postgres via Testcontainers
./gradlew :server:test --tests 'org.sona.format.flac.FlacParserTest'   # unit test, no Docker needed
./gradlew :server:bootRun                            # dev profile: admin/admin and user/user
npm --prefix web run dev                             # frontend on :5173, proxying /api to bootRun on :8080
```

The docker-compose database is only needed for `bootRun` and jOOQ codegen; tests don't use it.

Test notes:

- `@IntegrationTest` (in `src/test/java/org/sona`) = `@SpringBootTest(webEnvironment = RANDOM_PORT)`
  + `TestcontainersConfig` (throwaway `postgres:18`, migrated by Flyway on startup) + `TestLibraryConfig`
  (in-memory Jimfs library folders, MusicBrainz pointed at a WireMock server) + `TestAuthConfig` (users can sign in
  through a `FakeOidcProvider`, a WireMock OIDC provider that signs ID tokens and answers token requests). Tests
  commit, and `TruncateTablesExtension` empties every table afterwards. Needs Docker.
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
- GitHub Actions (`.github/workflows/ci.yml`) generates the jOOQ classes against a Postgres service, builds and tests
  every module, and type-checks and builds `web/` on every PR and push to `main`.

## Code style

Match the surrounding code. The conventions below are consistent across the codebase:

**Language**
- Java 25. Use modern features: records for data, sealed interfaces + pattern-matching `switch` for
  closed hierarchies (`Block`, `TagValue`), unnamed variables (`_ ->`), `List.getFirst()`, `Stream.toList()`.
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

**Spring**
- Interface + `Default*` / provider-named implementation (`IngestionEngine` → `DefaultIngestionEngine`,
  `MetadataResolver` → `MusicBrainzResolver`).
- Constructor injection via Lombok `@RequiredArgsConstructor` on `private final` fields; no field `@Autowired`.
- Configuration is bound to `@ConfigurationProperties` records in `config.properties`.
- Lombok usage is limited to `@RequiredArgsConstructor`, `@Getter` (enums), `@Builder`, `@UtilityClass`, and `@Slf4j`,
  which every class that logs uses (never a hand-written `LoggerFactory.getLogger` field).

**Database**
- Use jOOQ's typed DSL with generated `Tables.*` constants; keep queries in `db/` DAOs.
- Map between jOOQ records and pojos with MapStruct (`@Mapper(componentModel = "spring")`).
- Migrations are lowercase SQL, `create table if not exists`, `uuid` primary keys named `<table>_id`, one table per
  migration.

**Tests**
- JUnit 5 + AssertJ (`assertThat`). Parameterise over fixtures with `@EnumSource`.
- Test classes and methods are package-private; method names describe behaviour (`readAndStoreIngestTrack`).
- Put tests that need Spring/DB/network behind `@IntegrationTest`; keep parser tests as plain unit tests.

**Frontend (`web/`)**
- Name API types, constants and functions after their backend counterparts: `ApiRoutes`, `ErrorCode`,
  `*Request` / `*Response` types in `web/src/api/`, verb-noun functions (`refreshSession`, `hasPermission`).
- Every closed set of values is an enum, written as a const object with a type of the same name, and code uses its
  members, never the raw strings:
  `export const Theme = { SYSTEM: "system", ... } as const;` then
  `export type Theme = (typeof Theme)[keyof typeof Theme];`. No string-literal union types.
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
