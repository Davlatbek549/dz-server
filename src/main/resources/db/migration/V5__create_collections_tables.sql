-- User-created collections ("shelves") and their membership.
--
-- external_id is the id the app generated, which is what appears in API paths.
-- A local-first client has to be able to create a collection offline, so it
-- cannot wait for the server to assign one. The surrogate uuid exists only so
-- collection_books has a single column to reference.
create table collections (
    id          uuid        primary key default gen_random_uuid(),
    user_id     uuid        not null references users (id) on delete cascade,
    external_id text        not null,
    title       text        not null,
    description text,
    created_at  timestamptz not null default now(),
    updated_at  timestamptz not null default now(),

    constraint collections_user_external_key unique (user_id, external_id)
);

create index collections_user_created_idx on collections (user_id, created_at desc);

-- Book display fields are denormalised so a collection renders without joining
-- the user's library — a collection may hold books they have not added.
--
-- No user_id here on purpose: ownership is reached through the parent
-- collection, which is the only way rows are ever looked up.
create table collection_books (
    id            uuid primary key default gen_random_uuid(),
    collection_id uuid not null references collections (id) on delete cascade,
    book_id       text not null,
    title         text not null,
    author        text,
    cover_url     text,
    position      int  not null default 0,

    constraint collection_books_collection_book_key unique (collection_id, book_id)
);

create index collection_books_collection_idx on collection_books (collection_id, position);
