create table if not exists artist (
    artist_id uuid primary key,

    name text not null,
    -- ISO 3166-1 code of the artist's area, when MusicBrainz knows it.
    country text,
    thumbnail_path text,

    -- These fields are nullable in case they are not found in the MB database.
    musicbrainz_artist_id uuid unique
);

-- Lists artists by name.
create index if not exists artist_name_idx on artist (name, artist_id);
