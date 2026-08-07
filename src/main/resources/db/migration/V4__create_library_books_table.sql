-- A user's own library. There is deliberately no catalogue table: book metadata
-- comes from Gutendex/OpenLibrary at runtime, and book_id is the id from that
-- source, so the fields below are a denormalised snapshot rather than a second
-- copy of a catalogue we do not own. It mirrors the app's local `library_book`.
create table library_books (
    id               uuid        primary key default gen_random_uuid(),
    user_id          uuid        not null references users (id) on delete cascade,
    book_id          text        not null,
    title            text        not null,
    author           text,
    cover_url        text,
    description      text,
    text_url         text,
    is_free          boolean     not null default true,
    is_favorite      boolean     not null default false,
    progress_percent int         not null default 0
                     check (progress_percent between 0 and 100),
    added_at         timestamptz not null default now(),
    last_read_at     timestamptz,
    updated_at       timestamptz not null default now(),

    -- One row per book per user; the same book in two libraries is two rows.
    constraint library_books_user_book_key unique (user_id, book_id)
);

-- Serves the main "my library, newest first" query.
create index library_books_user_added_idx on library_books (user_id, added_at desc);

-- is_downloaded and download_path are intentionally absent: they describe one
-- device's filesystem, so they stay local to the app's SQLDelight database.
