-- Everything the app's Edit Profile screen lets a person set, beyond what V3
-- already added (bio, phone number, language, goal minutes).
--
-- Enumerations are stored as their names and pinned with check constraints, so
-- a value the code no longer knows can never be written by hand and then fail
-- to load. A null passes a check constraint, which is what "not set" needs.
alter table users
    add column username            text,
    add column banner_url          text,
    add column phone_country       text,
    add column date_of_birth       date,
    add column gender              text,
    add column speciality_kind     text,
    add column speciality          text,
    add column address_country     text,
    add column address_city        text,
    add column address_street      text,
    add column address_postal_code text,
    -- Open unless the person chooses otherwise (decided 2026-11-13).
    add column profile_visibility  text not null default 'OPEN';

alter table users
    add constraint users_gender_check
        check (gender in ('FEMALE', 'MALE', 'PREFER_NOT_TO_SAY')),
    add constraint users_speciality_kind_check
        check (speciality_kind in ('WORK', 'STUDY')),
    add constraint users_profile_visibility_check
        check (profile_visibility in ('OPEN', 'PRIVATE'));

-- Usernames are compared case-insensitively, like addresses (see V1), so
-- @Amelia and @amelia cannot be two people.
create unique index users_username_lower_key on users (lower(username));
