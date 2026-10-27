create type shuffle_mode as enum ('OFF', 'ON');
create type repeat_mode as enum ('OFF', 'ALL', 'ONE');

-- How a user's play queue is getting on. A user has a row once they first change their queue.
create table if not exists queue (
    user_id uuid primary key references users (user_id) on delete cascade,

    -- Null when the queue is empty.
    current_entry_id uuid references queue_track (queue_track_id) on delete set null,
    -- How far into the current entry the user has played, as of the last time a client reported it.
    position_seconds integer not null default 0,
    shuffle shuffle_mode not null default 'OFF',
    repeat repeat_mode not null default 'OFF',
    -- Goes up with every change, so a client can tell it has missed one.
    version bigint not null default 0
);

-- Finds the queue whose current entry is deleted.
create index if not exists queue_current_entry_id_idx on queue (current_entry_id);
