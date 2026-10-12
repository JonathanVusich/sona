create table if not exists label (
    label_id uuid primary key,

    name text not null,

    musicbrainz_label_id uuid unique
);
