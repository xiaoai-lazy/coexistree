-- Versioned system_knowledge_trees: 多版本行 + 每个 system 至多一条 ACTIVE。
-- 前提：不假设需保留生产历史数据；允许 TRUNCATE system_knowledge_trees CASCADE 以简化结构变更。
-- 若跳过 TRUNCATE：需先将 baseline 遗留的 tree_status = 'EMPTY' 更新为需求 17.2 枚举之一（ACTIVE / ARCHIVED / INACTIVE），
-- 且保证每个 system_id 至多一行 ACTIVE，方可创建部分唯一索引。

TRUNCATE TABLE system_knowledge_trees CASCADE;

ALTER TABLE system_knowledge_trees
    DROP CONSTRAINT IF EXISTS system_knowledge_trees_system_id_key;

ALTER TABLE system_knowledge_trees
    ALTER COLUMN tree_json TYPE JSONB USING tree_json::jsonb;

-- V1 baseline 列默认值为 EMPTY；本迁移将列默认值改为 ACTIVE，与需求 17.2 生命周期一致。
ALTER TABLE system_knowledge_trees
    ALTER COLUMN tree_status SET DEFAULT 'ACTIVE';

ALTER TABLE system_knowledge_trees
    ADD CONSTRAINT uk_system_tree_version UNIQUE (system_id, tree_version);

CREATE UNIQUE INDEX uk_active_tree_per_system
    ON system_knowledge_trees (system_id)
    WHERE tree_status = 'ACTIVE';

-- 需求 17.2：按 (system_id, tree_status) 查询 ACTIVE 等场景
CREATE INDEX IF NOT EXISTS idx_system_tree_active
    ON system_knowledge_trees (system_id, tree_status);
