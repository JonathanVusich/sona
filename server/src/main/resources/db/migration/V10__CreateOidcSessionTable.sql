-- The provider's refresh token behind each session of an OIDC user, keyed by the session's refresh token family. Sona
-- uses it whenever the session is refreshed, so a user the provider has disabled loses access then. It is kept as
-- it is because Sona has to send it back to the provider.
create table if not exists oidc_session (
    family_id uuid primary key,
    user_id uuid not null references users on delete cascade,

    refresh_token text not null
);
