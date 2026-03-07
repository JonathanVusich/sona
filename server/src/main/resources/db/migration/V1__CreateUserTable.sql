create table if not exists users (
    user_id uuid primary key,
    name text not null
);