create table if not exists release_group (
    release_group_id uuid primary key,

    name text not null,

    -- Nullable in case it is not found in the MB database.
    musicbrainz_release_group_id uuid unique
);