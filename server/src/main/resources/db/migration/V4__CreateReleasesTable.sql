create table if not exists release (
    release_id uuid primary key,

    name text not null,
    thumbnail_path text,
    release_group_id uuid not null references release_group (release_group_id),

    -- These fields are nullable in case they are not found in the MB database.
    artist_id uuid references artist (artist_id),
    musicbrainz_release_id uuid unique
);