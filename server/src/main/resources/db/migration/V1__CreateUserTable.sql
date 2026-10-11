create type user_role as enum ('ADMIN', 'USER');

-- LOCAL users sign in with a password kept by Sona, OIDC users through a provider.
create type user_type as enum ('LOCAL', 'OIDC');

-- DISABLED users can't sign in or refresh their session.
create type account_state as enum ('ACTIVE', 'DISABLED');

-- Every user, however they sign in. How they sign in is in its own table: password for LOCAL users, oidc_identity
-- for OIDC users.
create table if not exists users (
    user_id uuid primary key,

    type user_type not null,
    username text not null unique,
    role user_role not null,
    state account_state not null default 'ACTIVE'
);
