create table if not exists artist (
    artist_id uuid primary key,

    name text not null,
    -- ISO 3166-1 code of the artist's area, when MusicBrainz knows it.
    country text,
    -- Null when there's no image of the artist.
    thumbnail_path text,
    -- How many of the artist's release groups are in the library, kept up to date by a trigger on release_group. A
    -- group without a release isn't counted.
    release_group_count integer not null default 0,

    -- Null when the artist isn't in MusicBrainz.
    musicbrainz_artist_id uuid unique
);

-- Lists artists by name. Only those with a release group in the library are listed.
create index if not exists artist_name_idx on artist (name, artist_id) where release_group_count > 0;
