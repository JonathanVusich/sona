-- The releases MusicBrainz offered for an ALBUM group whose state is INPUT_REQUIRED, best first: those that all of,
-- or one of whose media, has as many tracks as the group has files. Written when matching the group stops, for the
-- review that chooses between them.
create table if not exists ingest_group_candidate (
    ingest_group_candidate_id uuid primary key,
    ingest_group_id uuid not null references ingest_group (ingest_group_id) on delete cascade,

    -- From 1, the best candidate first.
    rank integer not null,

    musicbrainz_release_id uuid not null,
    release_title text not null,
    musicbrainz_artist_id uuid not null,
    artist_name text not null,
    -- MusicBrainz may only know the year or month, and the parts it doesn't know are the first; null when it doesn't
    -- know any.
    release_date date,
    -- ISO 3166-1 code, or a MusicBrainz code like "XW" for worldwide; null when MusicBrainz doesn't know it.
    country text,
    -- The medium the group's files are, from 1, as when one disc of several was uploaded; null when they are the
    -- whole release.
    medium_position integer,
    -- How many of the group's files Sona could place on the release's tracks.
    placed_count integer not null
);

-- Serves reading a group's candidates in order, and replacing them.
create unique index if not exists ingest_group_candidate_ingest_group_id_idx
    on ingest_group_candidate (ingest_group_id, rank);
