-- One row each time a user plays a track through, as the player reports it. A user's history, which only they see.
create table if not exists play (
    play_id uuid primary key,

    user_id uuid not null references users (user_id),
    track_id uuid not null references track (track_id),
    played_at timestamptz not null default now()
);

-- Lists a user's latest plays: play IDs are UUIDv7, so they are in the order the plays were reported.
create index if not exists play_user_id_idx on play (user_id, play_id desc);
