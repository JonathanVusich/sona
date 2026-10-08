-- Values match org.sona.format.Format.
create type audio_format as enum ('FLAC');

create table if not exists track (
    track_id uuid primary key,

    name text not null,
    duration integer not null, -- in seconds
    format audio_format not null,
    path text not null,

    artist_id uuid references artist (artist_id),
    release_id uuid references release (release_id),

    -- The ingest the track came from. Unique, so retrying an ingest can't create a second track.
    track_ingest_id uuid not null unique references track_to_ingest (track_ingest_id)
);