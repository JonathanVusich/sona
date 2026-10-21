-- The labels a release came out on.
create table if not exists release_label (
    release_id uuid not null references release (release_id),
    label_id uuid not null references label (label_id),

    primary key (release_id, label_id)
);
