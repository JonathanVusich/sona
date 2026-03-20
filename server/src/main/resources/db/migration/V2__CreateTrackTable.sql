create table if not exists track (
    track_id uuid primary key,

    name text not null,
    duration integer not null, -- in seconds
    format text not null,
    path text not null,

    artist_id uuid,
    release_id uuid,
);