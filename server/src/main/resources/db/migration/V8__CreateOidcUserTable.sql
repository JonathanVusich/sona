-- Users who sign in through an OIDC provider, created the first time they visit. The provider manages their account.
create table if not exists oidc_user (
    -- Worked out from the issuer and subject, so a token's user is known without a lookup.
    user_id uuid primary key,

    -- The provider's issuer and the user's subject there.
    issuer text not null,
    subject text not null,
    username text not null unique,
    -- The role the provider gave the user on their last visit.
    role user_role not null,

    unique (issuer, subject)
);
