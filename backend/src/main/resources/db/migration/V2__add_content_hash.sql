-- 添加文档内容哈希字段，用于防重复上传
ALTER TABLE documents ADD COLUMN content_hash VARCHAR(64) NULL;

-- 创建索引加速重复检测查询
CREATE INDEX idx_documents_system_hash ON documents(system_id, content_hash);
