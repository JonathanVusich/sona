-- Refresh tokens are rotated on every use. All tokens descended from one login share a family, so a reused token
-- can revoke the whole chain.
create table if not exists refresh_token (
    refresh_token_id uuid primary key,
    user_id uuid not null references local_user on delete cascade,
    family_id uuid not null,

    -- SHA-256 of the token, hex encoded. The token itself is never stored.
    token_hash text not null unique,
    expires_at timestamptz not null,
    revoked_at timestamptz
);

create index if not exists refresh_token_family_id_idx on refresh_token (family_id);
