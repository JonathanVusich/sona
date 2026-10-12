create table if not exists label (
    label_id uuid primary key,

    name text not null,

    -- Null when the label isn't in MusicBrainz.
    musicbrainz_label_id uuid unique
);
