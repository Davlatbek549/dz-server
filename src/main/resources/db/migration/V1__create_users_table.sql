create table users (
    id             uuid        primary key default gen_random_uuid(),
    email          text        not null,
    password_hash  text        not null,
    name           text        not null,
    avatar_url     text,
    email_verified boolean     not null default false,
    enabled        boolean     not null default true,
    created_at     timestamptz not null default now(),
    updated_at     timestamptz not null default now()
);

-- Addresses are matched case-insensitively, so uniqueness has to be too:
-- without this, Ann@x.com and ann@x.com would be two accounts.
create unique index users_email_lower_key on users (lower(email));
