create table if not exists release (
    release_id uuid primary key,

    name text not null,
    thumbnail_path text,
    -- MusicBrainz may only know the year or month, and the parts it doesn't know are the first: "2002" is 2002-01-01.
    date date,
    -- ISO 3166-1 code, or a MusicBrainz code like "XW" for worldwide.
    country text,
    release_group_id uuid not null references release_group (release_group_id),

    -- These fields are nullable in case they are not found in the MB database.
    artist_id uuid references artist (artist_id),
    musicbrainz_release_id uuid unique
);

-- A group's releases, earliest first: the earliest stands for the group.
create index if not exists release_release_group_id_idx on release (release_group_id, date, release_id);
