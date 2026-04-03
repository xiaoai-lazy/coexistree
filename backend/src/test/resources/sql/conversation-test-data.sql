-- 对话相关测试数据

-- 会话数据
INSERT INTO conversations (conversation_id, system_id, title, created_at, updated_at)
VALUES ('test-conv-001', 1, 'Test Conversation 1', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO conversations (conversation_id, system_id, title, created_at, updated_at)
VALUES ('test-conv-002', 1, 'Test Conversation 2', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT INTO conversations (conversation_id, system_id, title, created_at, updated_at)
VALUES ('other-user-conv', 1, 'Other User Conversation', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);
