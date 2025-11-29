create type ingest_state as enum ('PENDING', 'INPUT_REQUIRED', 'COMPLETED');

create table if not exists track_ingest (
    track_ingest_id uuid primary key,
    group_ingest_id uuid not null,

    original_file_name text not null,
    state ingest_state not null default 'PENDING'
);
