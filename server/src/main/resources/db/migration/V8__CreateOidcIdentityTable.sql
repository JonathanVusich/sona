-- Where users who sign in through an OIDC provider come from. Their user_id is a UUIDv5 of the issuer and subject, so a
-- token's user is known without looking it up here.
create table if not exists oidc_identity (
    user_id uuid primary key references users on delete cascade,

    issuer text not null,
    subject text not null,

    unique (issuer, subject)
);
