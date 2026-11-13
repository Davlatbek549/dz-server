-- One row per friend relationship, requester to addressee, carrying its own
-- lifecycle in `status` rather than splitting "request" and "friendship" into
-- two tables -- a friendship is just a row that reached Accepted. Mirrors how
-- verification_codes keeps issue-through-consumption in one table instead of
-- a separate ledger.
create table friend_requests (
    id            uuid        primary key default gen_random_uuid(),
    requester_id  uuid        not null references users (id) on delete cascade,
    addressee_id  uuid        not null references users (id) on delete cascade,
    status        text        not null default 'Pending',
    created_at    timestamptz not null default now(),
    responded_at  timestamptz,
    check (requester_id <> addressee_id)
);

-- Blocks a second pending request between the same pair in either direction
-- without blocking a new request after a prior one was declined. Resolved
-- (Accepted/Declined) rows fall outside this index entirely.
create unique index friend_requests_pending_pair_idx
    on friend_requests (least(requester_id, addressee_id), greatest(requester_id, addressee_id))
    where status = 'Pending';

-- Serves "requests sent to me" (the accept/decline inbox) and the symmetric
-- "am I already friends with / have I already requested this person" checks.
create index friend_requests_addressee_status_idx on friend_requests (addressee_id, status, created_at desc);
create index friend_requests_requester_status_idx on friend_requests (requester_id, status);
