create table if not exists artist (
    artist_id uuid primary key,

    name text not null,
    -- ISO 3166-1 code of the artist's area, when MusicBrainz knows it.
    country text,
    -- Null when there's no image of the artist.
    thumbnail_path text,

    musicbrainz_artist_id uuid not null unique
);

-- Lists artists by name.
create index if not exists artist_name_idx on artist (name, artist_id);
