-- ALBUM: files the uploader says belong together, such as one folder. UNKNOWN: loose files, which matching groups
-- itself.
create type ingest_group_kind as enum ('ALBUM', 'UNKNOWN');

-- CREATED: its files are still being uploaded. UPLOADED: all of them are, and it waits for the worker to match them.
-- MATCHED: the worker has matched them, whatever came of each file.
create type ingest_group_state as enum ('CREATED', 'UPLOADED', 'MATCHED');

-- Files uploaded together. The uploader creates the group, with all of its files, before uploading any of them.
create table if not exists ingest_group (
    ingest_group_id uuid primary key,
    kind ingest_group_kind not null,
    -- The folder's name, or what the uploader called the group; shown while it's ingested.
    name text not null,
    -- The user who uploaded it, the only one shown how its files' ingests are getting on.
    user_id uuid not null references users,
    state ingest_group_state not null default 'CREATED'
);

-- The ingest worker takes uploaded groups oldest first.
create index if not exists ingest_group_state_idx on ingest_group (state, ingest_group_id);
