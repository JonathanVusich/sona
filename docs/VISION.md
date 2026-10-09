# Sona: vision and product decisions

This document records what Sona is for and the product decisions that guide development.
When a design question comes up, check here first; if the answer isn't here, ask before deciding.

## What Sona is

Sona is a **self-hosted music server for the public self-hosting community**, aiming for a real
open-source release. It competes with **Navidrome / Jellyfin** (streaming) and **Lidarr / Roon**
(library management and acquisition).

Its long-term job is **serving music**, but **v1 is "ingest done right"**: reliable, automatic
cataloguing comes first and streaming follows. What sets Sona apart is that the library behind the server
is **accurately catalogued and organised automatically**:

- **Accurate metadata:** MusicBrainz-quality matching out of the box.
- **Automatic organisation:** users hand Sona files; Sona identifies, tags and stores them itself.
- **Acquisition:** track wanted releases and fill gaps in discographies (Lidarr-style). Sources are
  still undecided.
- **Classical support:** a differentiator, but deferred until pop/rock matching is solid.

**Design philosophy: opinionated.** Strong defaults and few knobs. Sona should "just work" rather than expose
every option.

There is no deadline. Done when it's good.

### Out of scope

- Video (music videos, concerts)
- Podcasts and audiobooks

## Clients and playback

- **Subsonic / OpenSubsonic API** so existing apps (Symfonium, Feishin, DSub, …) work.
- **First-party mobile apps** (later), backed by an OpenAPI-documented **REST/JSON API**.
- **Search** technology is undecided (Postgres full-text search vs. a dedicated engine).
- **Transcoding** (e.g. FLAC → Opus for mobile) is wanted but **not for v1**.

## Library model

- **Sona owns the files.** Everything ingested is moved into Sona's managed library; Sona does not index
  folders in place.
- **Editions:** releases of the same album (remaster, deluxe, regional) are **grouped under one album
  (release group), with every edition listed inside it**.
- **Duplicates:** when the same recording is ingested twice, **keep the best-quality file** (lossless /
  higher bitrate) and discard the other.
- **Multi-user, admin-managed storage:** music is **deduplicated behind the scenes and managed by admins**.
  Users get **per-user libraries** (which music they see) and **per-user history** (plays, ratings,
  favourites, playlists). Only admins ingest, edit and delete shared storage.
- **Cover art:** pull from several sources (Cover Art Archive, embedded art, and others) and **let the user
  choose the highest-quality image**.
- **Classical modelling** (works, movements, performers, conductors) comes **later**.

## Ingestion

- **Entry points:** uploads through the app/API, and acquisition (Sona fetching wanted releases).
  No watched folder or in-place library scan.
- **After a successful import the original upload is deleted.** The ingest area is temporary.
- **Matching unit:** match **per album** (use every track in an ingest group to choose one release),
  **falling back to per-track** matching for loose files.
- **Existing MusicBrainz IDs** in tags (e.g. from Picard) are a **strong hint that must be verified**
  against the other tags, not trusted blindly.
- **Audio fingerprinting (AcoustID / Chromaprint) runs on every file.** Decoding the audio for it uses
  **hand-written decoders** (FLAC first), consistent with the hand-written parsers. No `fpcalc`/ffmpeg.
- **Confidence handling:** auto-accept above a confidence threshold; below it, put the file in a
  **review queue** where the user picks from ranked candidates.
- **Unmatchable files:** the user enters metadata manually and the file is imported anyway.

## Metadata sources

- **MusicBrainz is the primary source of truth**, supplemented by **Discogs** and **Spotify / Apple**.
- Providers sit behind an **internal interface** (plain classes in the codebase). Sona has no runtime
  plugin system.
- **The public MusicBrainz API is the default**, with a client-side rate limit. A **local
  MusicBrainz mirror is an optional, opt-in add-on** (e.g. a separate Docker Compose profile) for large
  libraries.
- The **`metadata` Gradle module is intended to become a separate, independently deployable
  metadata/search service**.

## Files on disk

- **Tags are fully rewritten** with Sona's resolved metadata on import.
- **Audio data must be preserved byte for byte.** Only metadata changes.
- **The library layout is based on Sona's own database IDs**, not MusicBrainz IDs, so files that have no
  MusicBrainz match (or are entered manually) fit the same layout. The library is Sona's internal storage, so it
  doesn't need to be friendly to other players. It must still be **consistent, and easy for a user to retrieve their music from** if they leave Sona.
- **Formats:** FLAC first, then **eventually all common formats**.
- **Parsers and writers are hand-written on purpose** (control and correctness). Don't replace them
  with jaudiotagger or similar.

## Users and auth

- Roles: **admin** vs **user**.
- Auth: **local passwords**, **OIDC / SSO** (Authentik, Authelia, Keycloak), and **API tokens** for clients.
- **Admins can do everything; users can only read the library.**
- **Built on Spring Security:** the API is an OAuth2 resource server that takes JWT bearer tokens.

## Deployment and operations

- **Docker Compose** (Sona image + Postgres) is the supported deployment.
- **Postgres only.**
- **Scale target: 500k+ tracks.** Design queries, indexes and ingestion throughput for that.
- **Flyway migrations run automatically on application startup.**
- **Sona must be safe to expose to the internet** behind a reverse proxy.
- **Metrics and health endpoints** (e.g. Prometheus, Actuator health).
- **The database is a source of truth** (users, history, review decisions), so document and support backups.

## Engineering decisions

- **Bleeding edge is fine:** latest JDK and Spring Boot release candidates are acceptable.
- **Ask before adding any new dependency.**
- **Tests:**
  - Integration tests use **Testcontainers** Postgres.
  - MusicBrainz interactions use **recorded responses served by WireMock**, not the live API.
- **CI:** GitHub Actions builds and tests every PR. No enforced formatter; follow the existing style.
- **Agent autonomy:** agents may branch, commit, push and **open PRs** for review.

## Roadmap

1. **Merge `add-ingestion-engine`:** fix the critical ingestion bugs **and add proper tests** first.
2. **Finish FLAC ingest:** working end-to-end import of FLAC albums (album-level matching, ID-based layout,
   tag rewrite, dedupe, review queue).
3. Then (order to be decided): upload API and auth, Subsonic streaming, more formats, acquisition.

## Open questions

These are not settled yet:

- **Acquisition sources:** usenet/torrents via download clients, stores (Bandcamp, Qobuz), or both?
  This has legal and UX implications for a public release.
- **Search technology:** Postgres full-text search vs. a dedicated engine (Meilisearch / OpenSearch).
