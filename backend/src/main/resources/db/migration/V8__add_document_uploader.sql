-- Add uploader tracking to documents
ALTER TABLE documents ADD COLUMN IF NOT EXISTS uploaded_by BIGINT;
CREATE INDEX IF NOT EXISTS idx_documents_uploaded_by ON documents(uploaded_by);
