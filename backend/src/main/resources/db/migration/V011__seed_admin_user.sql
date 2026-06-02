-- V009__seed_admin_user.sql  (PostgreSQL 16)
-- Seeds an initial admin role, features, and admin user for first login.
-- Default credentials: admin / password

-- ─────────────────────────────────────────────────────────
-- 1. ADMIN ROLE
-- ─────────────────────────────────────────────────────────
INSERT INTO roles (id, name, status, created_at, modified_at)
VALUES ('a0000000-0000-0000-0000-000000000001', 'ADMIN', 1, NOW(), NOW())
ON CONFLICT (name) DO NOTHING;

-- ─────────────────────────────────────────────────────────
-- 2. ADMIN USER (commented out as per user request to only have superadmin)
-- ─────────────────────────────────────────────────────────
-- INSERT INTO users (id, username, password_hash, first_name, last_name,
--                    status, account_locked, department_visibility, created_at, modified_at)
-- VALUES ('b0000000-0000-0000-0000-000000000001', 'admin',
--         '$2a$12$aMo/J6gcac5nxiJJv6Ldhej4wpD37kAd.TTj2CJJO4L6yMPn3eNyK',
--         'System', 'Admin', 1, false, 1, NOW(), NOW())
-- ON CONFLICT (username) DO NOTHING;
--
-- ─────────────────────────────────────────────────────────
-- 3. ASSIGN ADMIN ROLE TO ADMIN USER
-- ─────────────────────────────────────────────────────────
-- INSERT INTO user_roles (user_id, role_id)
-- VALUES ('b0000000-0000-0000-0000-000000000001', 'a0000000-0000-0000-0000-000000000001')
-- ON CONFLICT DO NOTHING;
