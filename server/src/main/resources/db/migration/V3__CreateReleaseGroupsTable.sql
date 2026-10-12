-- MusicBrainz's primary release group types, mapped from its names by MusicBrainzResolver.
create type release_group_type as enum ('ALBUM', 'SINGLE', 'EP', 'BROADCAST', 'OTHER');

create table if not exists release_group (
    release_group_id uuid primary key,

    name text not null,
    -- Null when MusicBrainz gives the release group no primary type.
    type release_group_type,
    -- The group's earliest release anywhere. Like release.date, parts MusicBrainz doesn't know are the first.
    first_release_date date,
    artist_id uuid references artist (artist_id),

    -- Nullable in case it is not found in the MB database.
    musicbrainz_release_group_id uuid unique
);

-- Lists release groups by name.
create index if not exists release_group_name_idx on release_group (name, release_group_id);
create index if not exists release_group_artist_id_idx on release_group (artist_id, release_group_id);
