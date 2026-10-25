-- A user's own list of tracks, which only they see.
create table if not exists playlist (
    playlist_id uuid primary key,

    user_id uuid not null references users (user_id),
    name text not null,
    -- Null when the user gives it none.
    description text,
    -- How many tracks are on the playlist, kept up to date by a trigger on playlist_track.
    track_count integer not null default 0
);

-- Lists a user's playlists by name.
create index if not exists playlist_user_id_idx on playlist (user_id, name, playlist_id);
