-- The recordings MusicBrainz offered for a file whose ingest is INPUT_REQUIRED, best first, each on the release that
-- suits the file's tags best. Written when the ingest stops, for the review that chooses between them.
create table if not exists ingest_candidate (
    ingest_candidate_id uuid primary key,
    track_ingest_id uuid not null references track_to_ingest (track_ingest_id) on delete cascade,

    -- From 1, the best candidate first.
    rank integer not null,
    -- How well it matches the file: MusicBrainz's search score, adjusted for how the file's tags and audio agree.
    score integer not null,

    musicbrainz_recording_id uuid not null,
    recording_title text not null,
    musicbrainz_artist_id uuid not null,
    artist_name text not null,
    musicbrainz_release_id uuid not null,
    release_title text not null,
    -- The track's medium of the release, and its position on it, both from 1.
    disc_number integer not null,
    track_number integer not null
);

-- Serves reading a file's candidates in order, and replacing them.
create unique index if not exists ingest_candidate_track_ingest_id_idx on ingest_candidate (track_ingest_id, rank);
