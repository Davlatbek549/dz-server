-- Profile fields the account itself owns. The counts the profile screen shows
-- (books read, friends, collections) are deliberately not stored: they are
-- derived from other tables once those modules exist, and a denormalised copy
-- would only drift.
alter table users
    add column bio                  text,
    add column phone_number         text,
    add column language             text,
    add column current_goal_minutes int;
