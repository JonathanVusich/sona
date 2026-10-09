create type user_role as enum ('ADMIN', 'USER');

-- PASSWORD_CHANGE_REQUIRED users may only change their password until they do.
create type account_state as enum ('ACTIVE', 'PASSWORD_CHANGE_REQUIRED', 'DISABLED');

create table if not exists users (
    user_id uuid primary key,

    username text not null unique,
    -- Null for users who only sign in through an OIDC provider.
    password_hash text,
    role user_role not null,
    state account_state not null default 'ACTIVE',

    -- Where a user from an OIDC provider signs in: the provider's issuer and their subject there. Null for local users.
    issuer text,
    subject text
);
