-- The tracks on a playlist, in the user's order. A track is on a playlist once at most.
create table if not exists playlist_track (
    playlist_id uuid not null references playlist (playlist_id) on delete cascade,
    track_id uuid not null references track (track_id),
    -- From 1. Checked at commit, so moving a track can shift the tracks it passes in one statement.
    position integer not null,

    primary key (playlist_id, track_id),
    constraint playlist_track_position_key unique (playlist_id, position) deferrable initially deferred
);

-- Keeps playlist.track_count up to date as tracks are added and removed.
create or replace function count_playlist_tracks() returns trigger as $$
begin
    if tg_op = 'INSERT' then
        update ${schema}.playlist set track_count = track_count + 1 where playlist_id = new.playlist_id;
    end if;
    if tg_op = 'DELETE' then
        update ${schema}.playlist set track_count = track_count - 1 where playlist_id = old.playlist_id;
    end if;
    return null;
end;
$$ language plpgsql;

create or replace trigger playlist_track_count_playlist_tracks
    after insert or delete on playlist_track
    for each row execute function count_playlist_tracks();
