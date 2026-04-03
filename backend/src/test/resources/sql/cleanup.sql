-- 清理所有测试数据
-- 注意删除顺序，先删子表再删父表

DELETE FROM documents WHERE 1=1;
DELETE FROM system_user_mappings WHERE 1=1;
DELETE FROM system_tree_snapshots WHERE 1=1;
DELETE FROM system_knowledge_trees WHERE 1=1;
DELETE FROM systems WHERE 1=1;
-- 删除测试创建的用户，但保留 AdminInitializer 创建的 admin 用户 (id=1)
DELETE FROM users WHERE id > 1;
