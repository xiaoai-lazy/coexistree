-- 基础测试数据
-- 注意：使用 id > 100 避免与 AdminInitializer 自动创建的 admin 用户(id=1)冲突

-- 用户数据
INSERT INTO users (id, username, password_hash, display_name, role, enabled, created_at, updated_at)
VALUES (101, 'testuser', '$2a$10$xxxxxxxx', 'Test User', 'SUPER_ADMIN', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (id, username, password_hash, display_name, role, enabled, created_at, updated_at)
VALUES (102, 'otheruser', '$2a$10$xxxxxxxx', 'Other User', 'USER', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO users (id, username, password_hash, display_name, role, enabled, created_at, updated_at)
VALUES (199, 'testadmin', '$2a$10$xxxxxxxx', 'Test Admin User', 'USER', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- 系统数据
INSERT INTO systems (id, system_code, system_name, description, status, created_by, created_at, updated_at)
VALUES (1, 'order-service', 'Order Service', 'Order management system', 'ACTIVE', 101, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO systems (id, system_code, system_name, description, status, created_by, created_at, updated_at)
VALUES (2, 'user-service', 'User Service', 'User management system', 'ACTIVE', 102, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

-- 系统成员关系
INSERT INTO system_user_mappings (id, system_id, user_id, relation_type, view_level, assigned_at)
VALUES (1, 1, 101, 'OWNER', 1, CURRENT_TIMESTAMP);

INSERT INTO system_user_mappings (id, system_id, user_id, relation_type, view_level, assigned_at)
VALUES (2, 1, 102, 'SUBSCRIBER', 1, CURRENT_TIMESTAMP);

-- 文档数据
INSERT INTO documents (id, system_id, doc_name, original_file_name, file_path, content_type, parse_status, doc_type, security_level, uploaded_by, created_at, updated_at)
VALUES (1, 1, 'order-api.md', 'order-api.md', '/data/docs/order-api.md', 'text/markdown', 'SUCCESS', 'MARKDOWN', 1, 101, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO documents (id, system_id, doc_name, original_file_name, file_path, content_type, parse_status, doc_type, security_level, uploaded_by, created_at, updated_at)
VALUES (2, 1, 'user-guide.md', 'user-guide.md', '/data/docs/user-guide.md', 'text/markdown', 'PROCESSING', 'MARKDOWN', 1, 101, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO documents (id, system_id, doc_name, original_file_name, file_path, content_type, parse_status, doc_type, security_level, uploaded_by, created_at, updated_at)
VALUES (3, 2, 'other-system.md', 'other-system.md', '/data/docs/other-system.md', 'text/markdown', 'SUCCESS', 'MARKDOWN', 1, 102, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
