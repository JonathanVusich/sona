create table if not exists releases (
    release_id uuid primary key,

    name text,
    thumbnail_path text,

    -- These fields are nullable in case they are not found in the MB database.
    artist_id uuid,
    musicbrainz_release_id uuid
);