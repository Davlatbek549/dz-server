-- Federated identities. One row per provider account bound to a DZ user, so the
-- same person can sign in with a password and with Google without ending up
-- with two accounts.
create table linked_accounts (
    id          uuid        primary key default gen_random_uuid(),
    user_id     uuid        not null references users (id) on delete cascade,
    -- 'Google' today; the column is text so adding Apple needs no migration.
    provider    text        not null,
    -- The provider's own immutable id for the person ('sub' in an ID token).
    -- Deliberately not the email: people change addresses, and matching on one
    -- that was later reassigned would hand over the wrong account.
    subject     text        not null,
    -- What the provider said the address was when the link was made. Kept for
    -- support and for spotting drift; never used to find the account.
    email       text,
    created_at  timestamptz not null default now()
);

-- One DZ user per provider identity, and one link per provider per user.
create unique index linked_accounts_provider_subject_key on linked_accounts (provider, subject);
create unique index linked_accounts_user_provider_key on linked_accounts (user_id, provider);

-- Someone who only ever signs in with Google has no password to hash. The
-- column stays for everyone else; login treats a null as "this account has no
-- password", which is a refusal rather than a match.
alter table users
    alter column password_hash drop not null;
