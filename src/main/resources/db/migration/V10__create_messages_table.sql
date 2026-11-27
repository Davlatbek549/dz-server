-- A direct message between two users. There is no separate conversation/
-- thread entity -- the pair of user ids *is* the thread identity, the same
-- way a friendship is just a row in friend_requests rather than its own
-- table.
create table messages (
    id           uuid        primary key default gen_random_uuid(),
    sender_id    uuid        not null references users (id) on delete cascade,
    recipient_id uuid        not null references users (id) on delete cascade,
    text         text        not null,
    created_at   timestamptz not null default now(),
    read_at      timestamptz,
    check (sender_id <> recipient_id)
);

-- Serves "the thread between me and this friend, in order" regardless of who
-- sent which message.
create index messages_thread_idx on messages (least(sender_id, recipient_id), greatest(sender_id, recipient_id), created_at);

-- Serves "how many unread messages do I have from this friend" and the bulk
-- mark-read update, both directional (recipient = me, sender = them) rather
-- than symmetric like the thread read above.
create index messages_unread_idx on messages (recipient_id, sender_id) where read_at is null;
