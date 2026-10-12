create type ingest_state as enum ('PENDING', 'INPUT_REQUIRED', 'MOVE_FAILED', 'FAILED', 'COMPLETED');

create table if not exists track_to_ingest (
    track_ingest_id uuid primary key,
    group_ingest_id uuid not null,

    original_file_name text not null,
    state ingest_state not null default 'PENDING'
);

-- Loads an ingest group, and the tracks still waiting to be ingested.
create index if not exists track_to_ingest_group_ingest_id_idx on track_to_ingest (group_ingest_id);
create index if not exists track_to_ingest_state_idx on track_to_ingest (state);
