-- The discs, or other media, a release is made of.
create table if not exists medium (
    medium_id uuid primary key,
    release_id uuid not null references release (release_id),

    -- From 1.
    position integer not null,
    -- Like "CD" or "Digital Media"; null when MusicBrainz doesn't know.
    format text,

    unique (release_id, position)
);
