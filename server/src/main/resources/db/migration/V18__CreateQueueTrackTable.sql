-- The tracks in a user's play queue. A track can be in it more than once, so each entry has an ID of its own.
create table if not exists queue_track (
    queue_track_id uuid primary key,

    user_id uuid not null references users (user_id) on delete cascade,
    track_id uuid not null references track (track_id),
    -- The order the user queued the tracks in, from 1.
    -- Checked at commit, so entries can pass through each other's positions in one statement.
    position integer not null,
    -- Where the entry falls when the queue is shuffled, from 1: a random order made when the queue is filled, so
    -- turning shuffle on or off changes no entry.
    shuffle_order integer not null,

    constraint queue_track_position_key unique (user_id, position) deferrable initially deferred,
    constraint queue_track_shuffle_order_key unique (user_id, shuffle_order) deferrable initially deferred
);
