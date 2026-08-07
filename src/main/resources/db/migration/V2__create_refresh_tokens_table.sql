-- Access tokens are stateless and cannot be withdrawn once signed, so logout
-- and "sign out everywhere" are enforced here instead: a refresh token only
-- works while its row is present, unexpired and unrevoked.
create table refresh_tokens (
    id         uuid        primary key default gen_random_uuid(),
    user_id    uuid        not null references users (id) on delete cascade,
    -- SHA-256 of the token, never the token itself: a leaked database dump
    -- must not hand out working sessions.
    token_hash text        not null unique,
    expires_at timestamptz not null,
    revoked_at timestamptz,
    created_at timestamptz not null default now()
);

create index refresh_tokens_user_id_idx on refresh_tokens (user_id);
