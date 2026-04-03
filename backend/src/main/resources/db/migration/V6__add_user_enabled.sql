-- Add enabled column to users table
ALTER TABLE users ADD COLUMN enabled BOOLEAN NOT NULL DEFAULT TRUE;

-- Update existing admin user to be enabled
UPDATE users SET enabled = TRUE WHERE role = 'SUPER_ADMIN';
