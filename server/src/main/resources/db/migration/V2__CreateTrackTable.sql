create table if not exists tracks (
    track_id uuid primary key,

    name text not null,
    duration integer not null, -- in seconds
    format text not null,
    path text not null,

    artist_id uuid,
    release_id uuid,

    -- These fields are nullable in case they are not found in the MB database.
    musicbrainz_artist_id uuid,
    musicbrainz_track_id uuid,
    musicbrainz_release_id uuid
);