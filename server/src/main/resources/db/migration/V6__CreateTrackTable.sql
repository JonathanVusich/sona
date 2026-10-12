-- Values match org.sona.format.Format.
create type audio_format as enum ('FLAC');

create table if not exists track (
    track_id uuid primary key,

    name text not null,
    duration integer not null, -- in seconds
    format audio_format not null,
    path text not null,
    -- The medium the track is on, and its position there, both from 1.
    disc_number integer not null,
    track_number integer not null,

    artist_id uuid not null references artist (artist_id),
    release_id uuid not null references release (release_id),

    -- The ingest the track came from. Unique, so retrying an ingest can't create a second track.
    track_ingest_id uuid not null unique references track_to_ingest (track_ingest_id)
);

create index if not exists track_release_id_idx on track (release_id, disc_number, track_number);
