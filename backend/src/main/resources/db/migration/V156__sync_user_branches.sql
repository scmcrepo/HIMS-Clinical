-- Sync user_branches with primary branch_id for any users where it might be missing
INSERT INTO user_branches (user_id, branch_id)
SELECT id, branch_id FROM users
WHERE branch_id IS NOT NULL
ON CONFLICT DO NOTHING;
