-- Add security fields to documents
ALTER TABLE documents ADD COLUMN IF NOT EXISTS security_level INT NOT NULL DEFAULT 1;
ALTER TABLE documents ADD COLUMN IF NOT EXISTS uploaded_by BIGINT;

-- Indexes
CREATE INDEX IF NOT EXISTS idx_documents_security_level ON documents(security_level);
CREATE INDEX IF NOT EXISTS idx_documents_uploaded_by ON documents(uploaded_by);
