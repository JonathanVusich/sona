-- ALBUM: files the uploader says belong together, such as one folder. UNKNOWN: loose files, which matching groups
-- itself.
create type ingest_group_kind as enum ('ALBUM', 'UNKNOWN');

-- Files uploaded together. The uploader makes up the ID, and creates the group once its groups are final, before
-- uploading any of its files.
create table if not exists ingest_group (
    ingest_group_id uuid primary key,
    kind ingest_group_kind not null,
    -- The folder's name, or what the uploader called the group; shown while it's ingested.
    name text not null,
    -- The user who uploaded it, the only one shown how its files' ingests are getting on.
    user_id uuid not null references users
);
