create table if not exists release (
    release_id uuid primary key,

    name text not null,
    -- Null when there's no cover art.
    thumbnail_path text,
    -- MusicBrainz may only know the year or month, and the parts it doesn't know are the first: "2002" is 2002-01-01.
    date date,
    -- ISO 3166-1 code, or a MusicBrainz code like "XW" for worldwide.
    country text,
    -- How many of the release's tracks are in the library, kept up to date by a trigger on track.
    track_count integer not null default 0,
    release_group_id uuid not null references release_group (release_group_id),
    artist_id uuid not null references artist (artist_id),

    -- Null when the release isn't in MusicBrainz.
    musicbrainz_release_id uuid unique
);

-- A group's releases, earliest first: the earliest stands for the group.
create index if not exists release_release_group_id_idx on release (release_group_id, date, release_id);
