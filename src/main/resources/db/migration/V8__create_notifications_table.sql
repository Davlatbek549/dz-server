-- A user's notification feed: friend activity, direct messages, reading-goal
-- nudges and store offers. `category` is what a tap routes on -- see the
-- app's own NotificationCategory -- so it travels with the row rather than
-- being guessed at on the client.
create table notifications (
    id         uuid        primary key default gen_random_uuid(),
    user_id    uuid        not null references users (id) on delete cascade,
    category   text        not null,
    title      text        not null,
    message    text        not null,
    is_read    boolean     not null default false,
    created_at timestamptz not null default now()
);

-- Serves "my notifications, newest first", the only read this module does.
create index notifications_user_created_idx on notifications (user_id, created_at desc);
