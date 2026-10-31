-- CREATED, UPLOADING and UPLOADED: the file is waiting for its bytes, receiving them, and waiting to be matched.
create type ingest_state as enum (
    'CREATED',
    'UPLOADING',
    'UPLOADED',
    'INPUT_REQUIRED',
    'MOVE_FAILED',
    'FAILED',
    'COMPLETED'
);

create table if not exists track_to_ingest (
    track_ingest_id uuid primary key,
    group_ingest_id uuid not null references ingest_group (ingest_group_id),

    original_file_name text not null,
    state ingest_state not null default 'CREATED'
);

-- Finds the files of a group in a state, such as those still being uploaded, or those waiting to be matched.
create index if not exists track_to_ingest_group_idx on track_to_ingest (group_ingest_id, state);
