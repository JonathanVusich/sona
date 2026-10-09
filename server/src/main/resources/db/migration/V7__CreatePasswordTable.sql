-- A user whose password is CHANGE_REQUIRED may only change it until they do.
create type password_state as enum ('VALID', 'CHANGE_REQUIRED');

-- The passwords of users who sign in with one kept by Sona.
create table if not exists password (
    user_id uuid primary key references users on delete cascade,

    password_hash text not null,
    state password_state not null default 'VALID'
);
