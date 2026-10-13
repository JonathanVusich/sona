-- Values match org.sona.format.Format.
create type audio_format as enum ('FLAC');

create table if not exists track (
    track_id uuid primary key,

    name text not null,
    duration integer not null, -- in seconds
    format audio_format not null,
    path text not null,
    -- As in MusicBrainz, a track is on a medium, which is on a release. Its position there is from 1.
    medium_id uuid not null references medium (medium_id),
    position integer not null,

    artist_id uuid not null references artist (artist_id),

    -- The ingest the track came from. Unique, so retrying an ingest can't create a second track.
    track_ingest_id uuid not null unique references track_to_ingest (track_ingest_id)
);

create index if not exists track_medium_id_idx on track (medium_id, position);

-- Keeps release.track_count up to date as tracks are added, removed, or moved to another medium.
create or replace function count_release_tracks() returns trigger as $$
begin
    if tg_op in ('INSERT', 'UPDATE') then
        update ${schema}.release set track_count = track_count + 1
        where release_id = (select release_id from ${schema}.medium where medium_id = new.medium_id);
    end if;
    if tg_op in ('UPDATE', 'DELETE') then
        update ${schema}.release set track_count = track_count - 1
        where release_id = (select release_id from ${schema}.medium where medium_id = old.medium_id);
    end if;
    return null;
end;
$$ language plpgsql;

create or replace trigger track_count_release_tracks
    after insert or delete or update of medium_id on track
    for each row execute function count_release_tracks();
