-- 工作流定义表：表示一个逻辑上的工作流，例如“用户入职流程”
CREATE TABLE IF NOT EXISTS workflow_definition (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(128) NOT NULL UNIQUE,
    name VARCHAR(256) NOT NULL,
    description TEXT,
    status VARCHAR(32) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 版本表：一个工作流定义可以发布多个版本
CREATE TABLE IF NOT EXISTS workflow_version (
    id BIGSERIAL PRIMARY KEY,
    workflow_definition_id BIGINT NOT NULL REFERENCES workflow_definition(id),
    version_no INTEGER NOT NULL,
    status VARCHAR(32) NOT NULL,
    dsl_json JSONB NOT NULL,
    published_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (workflow_definition_id, version_no)
);

CREATE INDEX IF NOT EXISTS idx_workflow_version_definition_id
    ON workflow_version(workflow_definition_id);

-- 节点模板表：用于存储可复用的节点预设，后续结构化编辑和图形化编排都可以复用
CREATE TABLE IF NOT EXISTS node_template (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(128) NOT NULL UNIQUE,
    name VARCHAR(256) NOT NULL,
    description TEXT,
    node_type VARCHAR(64) NOT NULL,
    node_config JSONB NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_node_template_node_type
    ON node_template(node_type);

-- 实例表：某个工作流版本的一次运行
CREATE TABLE IF NOT EXISTS workflow_instance (
    id BIGSERIAL PRIMARY KEY,
    workflow_definition_id BIGINT NOT NULL REFERENCES workflow_definition(id),
    workflow_version_id BIGINT NOT NULL REFERENCES workflow_version(id),
    status VARCHAR(32) NOT NULL,
    input_payload JSONB,
    context_json JSONB,
    current_node_id VARCHAR(128),
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ended_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_workflow_instance_version_id
    ON workflow_instance(workflow_version_id);

CREATE INDEX IF NOT EXISTS idx_workflow_instance_status
    ON workflow_instance(status);

-- 节点执行表：记录每个节点的一次执行
CREATE TABLE IF NOT EXISTS node_execution (
    id BIGSERIAL PRIMARY KEY,
    workflow_instance_id BIGINT NOT NULL REFERENCES workflow_instance(id),
    node_id VARCHAR(128) NOT NULL,
    node_name VARCHAR(256) NOT NULL,
    node_type VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    attempt_no INTEGER NOT NULL DEFAULT 1,
    input_json JSONB,
    output_json JSONB,
    error_message TEXT,
    started_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ended_at TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_node_execution_instance_id
    ON node_execution(workflow_instance_id);

-- 事件表：用于审计与后续问题排查
CREATE TABLE IF NOT EXISTS execution_event (
    id BIGSERIAL PRIMARY KEY,
    workflow_instance_id BIGINT NOT NULL REFERENCES workflow_instance(id),
    node_execution_id BIGINT REFERENCES node_execution(id),
    event_type VARCHAR(64) NOT NULL,
    event_message TEXT NOT NULL,
    event_detail JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_execution_event_instance_id
    ON execution_event(workflow_instance_id);

-- 任务表：第一阶段先建好结构，后续扩展异步 worker 直接使用
CREATE TABLE IF NOT EXISTS workflow_task (
    id BIGSERIAL PRIMARY KEY,
    workflow_instance_id BIGINT NOT NULL REFERENCES workflow_instance(id),
    node_id VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    attempt_no INTEGER NOT NULL DEFAULT 1,
    input_json JSONB,
    available_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    locked_at TIMESTAMP,
    lock_owner VARCHAR(128),
    error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_workflow_task_status_available_at
    ON workflow_task(status, available_at);

ALTER TABLE workflow_task
    ADD COLUMN IF NOT EXISTS input_json JSONB;

ALTER TABLE workflow_task
    ADD COLUMN IF NOT EXISTS error_message TEXT;

-- 反馈表：第一阶段最小闭环不会完整使用，但先把模型建出来
CREATE TABLE IF NOT EXISTS feedback_record (
    id BIGSERIAL PRIMARY KEY,
    workflow_instance_id BIGINT NOT NULL REFERENCES workflow_instance(id),
    node_execution_id BIGINT REFERENCES node_execution(id),
    feedback_type VARCHAR(64) NOT NULL,
    feedback_payload JSONB,
    created_by VARCHAR(128),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
