-- V104__expand_username_limit.sql
ALTER TABLE users ALTER COLUMN username TYPE VARCHAR(25);
