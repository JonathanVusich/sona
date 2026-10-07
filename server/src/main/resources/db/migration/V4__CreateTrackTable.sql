-- Values match org.sona.format.Format.
create type audio_format as enum ('FLAC');

create table if not exists track (
    track_id uuid primary key,

    name text not null,
    duration integer not null, -- in seconds
    format audio_format not null,
    path text not null,

    artist_id uuid references artist (artist_id),
    release_id uuid references release (release_id)
);