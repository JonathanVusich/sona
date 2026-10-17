-- MusicBrainz's primary release group types, mapped from its names by MusicBrainzResolver.
create type release_group_type as enum ('ALBUM', 'SINGLE', 'EP', 'BROADCAST', 'OTHER');

create table if not exists release_group (
    release_group_id uuid primary key,

    name text not null,
    -- Null when MusicBrainz gives the release group no primary type.
    type release_group_type,
    -- The group's earliest release anywhere. Like release.date, parts MusicBrainz doesn't know are the first.
    first_release_date date,
    artist_id uuid not null references artist (artist_id),

    -- Null when the release group isn't in MusicBrainz.
    musicbrainz_release_group_id uuid unique
);

-- Lists release groups by name.
create index if not exists release_group_name_idx on release_group (name, release_group_id);
-- An artist's release groups, newest first.
create index if not exists release_group_artist_id_idx
    on release_group (artist_id, first_release_date desc nulls last, release_group_id);

-- Keeps artist.release_group_count up to date as release groups are added, removed, or credited to another artist.
create or replace function count_artist_release_groups() returns trigger as $$
begin
    if tg_op in ('INSERT', 'UPDATE') then
        update ${schema}.artist set release_group_count = release_group_count + 1 where artist_id = new.artist_id;
    end if;
    if tg_op in ('UPDATE', 'DELETE') then
        update ${schema}.artist set release_group_count = release_group_count - 1 where artist_id = old.artist_id;
    end if;
    return null;
end;
$$ language plpgsql;

create or replace trigger release_group_count_artist_release_groups
    after insert or delete or update of artist_id on release_group
    for each row execute function count_artist_release_groups();
