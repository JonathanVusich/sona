create type ingest_state as enum ('PENDING', 'INPUT_REQUIRED', 'MOVE_FAILED', 'FAILED', 'COMPLETED');

create table if not exists track_to_ingest (
    track_ingest_id uuid primary key,
    group_ingest_id uuid not null references ingest_group (ingest_group_id),

    original_file_name text not null,
    state ingest_state not null default 'PENDING'
);

-- The ingest worker takes pending ingests oldest first.
create index if not exists track_to_ingest_state_idx on track_to_ingest (state, track_ingest_id);

-- Counts the files of a group uploaded so far.
create index if not exists track_to_ingest_group_idx on track_to_ingest (group_ingest_id);
