create table if not exists release (
    release_id uuid primary key,

    name text not null,
    -- Null when there's no cover art.
    thumbnail_path text,
    -- MusicBrainz may only know the year or month, and the parts it doesn't know are the first: "2002" is 2002-01-01.
    date date,
    -- ISO 3166-1 code, or a MusicBrainz code like "XW" for worldwide.
    country text,
    -- How many of the release's tracks are in the library, kept up to date by a trigger on track.
    track_count integer not null default 0,
    release_group_id uuid not null references release_group (release_group_id),
    artist_id uuid not null references artist (artist_id),

    -- Null when the release isn't in MusicBrainz.
    musicbrainz_release_id uuid unique
);

-- A group's releases in the library, earliest first: the earliest stands for the group.
create index if not exists release_release_group_id_idx on release (release_group_id, date, release_id)
    where track_count > 0;

-- Keeps release_group.release_count up to date as releases gain their first track or lose their last, are removed, or
-- are moved to another group. A release without a track isn't in the library, so it isn't counted.
create or replace function count_release_group_releases() returns trigger as $$
begin
    -- Most updates are a track added to a release that already had one, which changes no group's count.
    if tg_op = 'UPDATE' and old.release_group_id = new.release_group_id
            and (old.track_count > 0) = (new.track_count > 0) then
        return null;
    end if;
    if tg_op in ('INSERT', 'UPDATE') then
        if new.track_count > 0 then
            update ${schema}.release_group set release_count = release_count + 1
            where release_group_id = new.release_group_id;
        end if;
    end if;
    if tg_op in ('UPDATE', 'DELETE') then
        if old.track_count > 0 then
            update ${schema}.release_group set release_count = release_count - 1
            where release_group_id = old.release_group_id;
        end if;
    end if;
    return null;
end;
$$ language plpgsql;

create or replace trigger release_count_release_group_releases
    after insert or delete or update of release_group_id, track_count on release
    for each row execute function count_release_group_releases();
