create table if not exists artist (
    artist_id uuid primary key,

    name text not null,
    thumbnail_path text,

    -- These fields are nullable in case they are not found in the MB database.
    musicbrainz_artist_id uuid unique
);