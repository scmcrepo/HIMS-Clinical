-- Create join table for multi-branch assignment
CREATE TABLE IF NOT EXISTS user_branches (
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    branch_id UUID NOT NULL REFERENCES branches(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, branch_id)
);

-- Seed user_branches with existing single-branch mappings from users table
INSERT INTO user_branches (user_id, branch_id)
SELECT id, branch_id FROM users 
WHERE branch_id IS NOT NULL
ON CONFLICT DO NOTHING;
