create table if not exists artists (
    artist_id uuid primary key,

    name text,
    thumbnail_path text,

    -- These fields are nullable in case they are not found in the MB database.
    musicbrainz_artist_id uuid
);