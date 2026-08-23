-- Codes emailed to prove someone reads a mailbox: confirming an address after
-- sign-up, and authorising a password reset.
--
-- One table for both because the mechanics are identical -- issue, expire,
-- count attempts, consume once. `purpose` keeps them apart, so a code minted to
-- confirm an address can never be spent to change a password.
create table verification_codes (
    id          uuid        primary key default gen_random_uuid(),
    user_id     uuid        not null references users (id) on delete cascade,
    -- BCrypt, not SHA-256 like refresh_tokens. Those are 32 random bytes and
    -- have nothing to brute-force; a six-digit code has a million possibilities,
    -- so a fast digest of one is reversible from a leaked dump in milliseconds.
    code_hash   text        not null,
    purpose     text        not null,
    expires_at  timestamptz not null,
    -- Counted so a code dies after a handful of wrong guesses rather than
    -- standing until it expires while someone works through the keyspace.
    attempts    int         not null default 0,
    -- Set on the one successful use; a code is never accepted twice.
    consumed_at timestamptz,
    created_at  timestamptz not null default now()
);

-- Issue and verify both look up the newest live code for one person and one
-- purpose, which is exactly this index.
create index verification_codes_user_purpose_idx
    on verification_codes (user_id, purpose, created_at desc);
