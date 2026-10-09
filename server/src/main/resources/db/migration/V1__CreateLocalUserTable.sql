create type user_role as enum ('ADMIN', 'USER');

-- PASSWORD_CHANGE_REQUIRED users may only change their password until they do.
create type account_state as enum ('ACTIVE', 'PASSWORD_CHANGE_REQUIRED', 'DISABLED');

-- Users who sign in with a password kept by Sona.
create table if not exists local_user (
    user_id uuid primary key,

    username text not null unique,
    password_hash text not null,
    role user_role not null,
    state account_state not null default 'ACTIVE'
);
