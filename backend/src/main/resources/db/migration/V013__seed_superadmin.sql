-- V013__seed_superadmin.sql
-- Seeds the SUPERADMIN role and superadmin user.

-- 1. SUPERADMIN ROLE
INSERT INTO roles (id, name, description, status, created_at, modified_at)
VALUES ('a0000000-0000-0000-0000-000000000002', 'SUPERADMIN', 'System Super Administrator - full access bypass', 1, NOW(), NOW())
ON CONFLICT (name) DO NOTHING;

-- 2. SUPERADMIN USER (password = 'password')
INSERT INTO users (id, username, password_hash, first_name, last_name,
                   status, account_locked, department_visibility, created_at, modified_at)
VALUES ('b0000000-0000-0000-0000-000000000002', 'superadmin',
        '$2a$12$aMo/J6gcac5nxiJJv6Ldhej4wpD37kAd.TTj2CJJO4L6yMPn3eNyK',
        'System', 'SuperAdmin', 1, false, 1, NOW(), NOW())
ON CONFLICT (username) DO NOTHING;

-- 3. ASSIGN SUPERADMIN ROLE TO SUPERADMIN USER
INSERT INTO user_roles (user_id, role_id)
VALUES ('b0000000-0000-0000-0000-000000000002', 'a0000000-0000-0000-0000-000000000002')
ON CONFLICT DO NOTHING;
