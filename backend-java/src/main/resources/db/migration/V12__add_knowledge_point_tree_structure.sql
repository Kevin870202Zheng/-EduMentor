-- V12__add_knowledge_point_tree_structure.sql
-- 添加知识点树状结构支持字段
-- 用于支持编(VOLUME)、卷(PART)、章(CHAPTER)、节(SECTION)、知识点(LEAF) 五层结构

-- 新增节点类型字段（默认 'LEAF' 保证向后兼容）
ALTER TABLE knowledge_points
    ADD COLUMN IF NOT EXISTS type VARCHAR(16) NOT NULL DEFAULT 'LEAF';

-- 新增路径编号字段（如 "1.2.3"，用于排序和展示）
ALTER TABLE knowledge_points
    ADD COLUMN IF NOT EXISTS sequence_path VARCHAR(32);

-- 添加索引以支持按类型查询
CREATE INDEX IF NOT EXISTS idx_kp_type ON knowledge_points (type);

-- 为已有数据设置默认值（LEAF）
UPDATE knowledge_points SET type = 'LEAF' WHERE type IS NULL OR type = '';
