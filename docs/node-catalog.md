# FlowForge Node Catalog

这份文档说明第一阶段当前支持的节点类型、配置字段和推荐写法。

重点不是列出全部未来能力，而是把当前真正可用的节点格式讲清楚。

## 1. 通用节点结构

所有节点都遵守：

```json
{
  "id": "node_1",
  "name": "Node Name",
  "type": "NODE_TYPE",
  "config": {}
}
```

通用规则：

- `id` 必填，且在单个工作流内唯一
- `name` 必填，作为管理台展示名称
- `type` 必填，决定节点执行行为
- `config` 必填，允许为空对象，但字段语义由节点类型决定
- 结构化编辑器与 JSON 编辑器最终输出完全一致的节点结构

## 2. `START`

用途：

- 标记流程起点

推荐配置：

```json
{
  "id": "start_1",
  "name": "Start",
  "type": "START",
  "config": {}
}
```

说明：

- 当前阶段 `START` 不需要额外配置
- 常见错误：同一个 DSL 中放置多个 `START`

## 3. `END`

用途：

- 标记流程结束点

推荐配置：

```json
{
  "id": "end_1",
  "name": "End",
  "type": "END",
  "config": {}
}
```

说明：

- 当前阶段 `END` 不需要额外配置
- 常见错误：没有任何边连接到 `END`

## 4. `ATOMIC_ABILITY`

用途：

- 表示一个原子能力调用节点
- 当前是平台最核心的执行节点类型

基础结构：

```json
{
  "id": "ability_1",
  "name": "Some Ability",
  "type": "ATOMIC_ABILITY",
  "config": {
    "abilityType": "REST"
  }
}
```

注意：

- `abilityType` 必须写在 `config` 中
- 如果 `config` 里完全没有接入配置，当前实现会走 mock 原子能力

常见错误：

- `abilityType` 与实际配置字段不匹配
- 缺少关键连接字段就直接发布

### 4.1 Mock 原子能力

适用场景：

- 最小闭环演示
- 没有接真实系统时做联调

示例：

```json
{
  "id": "ability_1",
  "name": "Mock Atomic Ability",
  "type": "ATOMIC_ABILITY",
  "config": {
    "abilityCode": "mock.echo"
  }
}
```

说明：

- 当前 `abilityCode` 主要作为占位说明
- 真正触发 mock 的关键是没有配置真实外部能力字段

### 4.2 `REST`

用途：

- 调用 HTTP / REST 接口
- 支持流式和非流式
- 支持基础认证和 login session

最小示例：

```json
{
  "id": "rest_1",
  "name": "Invoke REST",
  "type": "ATOMIC_ABILITY",
  "config": {
    "abilityType": "REST",
    "url": "http://localhost:8080/api/mock/echo",
    "method": "POST",
    "body": {
      "message": "hello"
    }
  }
}
```

支持字段：

- `abilityType`
  - 固定为 `REST`
- `url`
  - 必填
- `method`
  - 可选，默认 `POST`
- `headers`
  - 可选，对象结构
- `body`
  - 可选，请求体
- `streaming`
  - 可选，`true / false`
- `auth`
  - 可选，认证配置

常见错误：

- 缺少 `url`
- `login_session` 缺少登录地址或 `sessionContextKey`

#### `REST` 认证：`basic`

```json
{
  "auth": {
    "type": "basic",
    "username": "lee",
    "password": "secret"
  }
}
```

#### `REST` 认证：`bearer_static`

```json
{
  "auth": {
    "type": "bearer_static",
    "token": "your-token"
  }
}
```

#### `REST` 认证：`login_session`

适用场景：

- 先登录，再复用 token / session 调后续接口

示例：

```json
{
  "id": "rest_login_1",
  "name": "Login And Invoke Protected REST",
  "type": "ATOMIC_ABILITY",
  "config": {
    "abilityType": "REST",
    "url": "http://localhost:8080/api/mock/echo",
    "method": "POST",
    "auth": {
      "type": "login_session",
      "loginUrl": "http://localhost:8080/api/mock/login",
      "loginMethod": "POST",
      "loginBody": {
        "username": "lee",
        "password": "secret"
      },
      "tokenPath": "token",
      "sessionContextKey": "mockSession"
    }
  }
}
```

登录后复用 session 的后续节点：

```json
{
  "id": "rest_followup_1",
  "name": "Reuse Session",
  "type": "ATOMIC_ABILITY",
  "config": {
    "abilityType": "REST",
    "url": "http://localhost:8080/api/mock/echo",
    "method": "POST",
    "auth": {
      "type": "login_session",
      "sessionContextKey": "mockSession"
    },
    "body": {
      "step": "followup"
    }
  }
}
```

### 4.3 `DATABASE`

用途：

- 连接数据库执行 SQL
- 当前可用于 PostgreSQL / MySQL

查询示例：

```json
{
  "id": "db_query_1",
  "name": "Query Workflow Count",
  "type": "ATOMIC_ABILITY",
  "config": {
    "abilityType": "DATABASE",
    "jdbcUrl": "jdbc:postgresql://localhost:5432/flowforge",
    "username": "flowforge",
    "password": "flowforge",
    "operation": "QUERY",
    "sql": "SELECT COUNT(*) AS workflow_count FROM workflow_definition"
  }
}
```

常见错误：

- 缺少 `jdbcUrl`
- 缺少 `sql`
- 变量应动态替换时却把值写死

更新示例：

```json
{
  "id": "db_update_1",
  "name": "Update Record",
  "type": "ATOMIC_ABILITY",
  "config": {
    "abilityType": "DATABASE",
    "jdbcUrl": "jdbc:mysql://localhost:3306/demo",
    "username": "root",
    "password": "secret",
    "operation": "UPDATE",
    "sql": "UPDATE task SET status = 'DONE' WHERE id = 1"
  }
}
```

支持字段：

- `abilityType`
  - 固定为 `DATABASE`
- `jdbcUrl`
  - 必填
- `username`
  - 可选
- `password`
  - 可选
- `operation`
  - `QUERY` 或 `UPDATE`
- `sql`
  - 必填

### 4.4 `REDIS`

用途：

- 连接 Redis 执行简单操作

支持操作：

- `GET`
- `SET`
- `PUBLISH`

`GET` 示例：

```json
{
  "id": "redis_get_1",
  "name": "Read Cache",
  "type": "ATOMIC_ABILITY",
  "config": {
    "abilityType": "REDIS",
    "host": "localhost",
    "port": 6379,
    "operation": "GET",
    "key": "demo:key"
  }
}
```

`SET` 示例：

```json
{
  "id": "redis_set_1",
  "name": "Write Cache",
  "type": "ATOMIC_ABILITY",
  "config": {
    "abilityType": "REDIS",
    "host": "localhost",
    "port": 6379,
    "operation": "SET",
    "key": "demo:key",
    "value": "hello"
  }
}
```

`PUBLISH` 示例：

```json
{
  "id": "redis_publish_1",
  "name": "Publish Message",
  "type": "ATOMIC_ABILITY",
  "config": {
    "abilityType": "REDIS",
    "host": "localhost",
    "port": 6379,
    "operation": "PUBLISH",
    "channel": "demo-channel",
    "value": "hello"
  }
}
```

### 4.5 `ELASTICSEARCH`

用途：

- 通过 HTTP 调用 Elasticsearch 接口

示例：

```json
{
  "id": "es_query_1",
  "name": "Query Elasticsearch",
  "type": "ATOMIC_ABILITY",
  "config": {
    "abilityType": "ELASTICSEARCH",
    "url": "http://localhost:9200/demo-index/_search",
    "method": "POST",
    "body": {
      "query": {
        "match_all": {}
      }
    }
  }
}
```

支持字段：

- `abilityType`
  - 固定为 `ELASTICSEARCH`
- `url`
  - 必填
- `method`
  - 可选，默认 `POST`
- `body`
  - 可选

### 4.6 `KAFKA`

用途：

- 向 Kafka topic 发送消息

示例：

```json
{
  "id": "kafka_send_1",
  "name": "Send Kafka Message",
  "type": "ATOMIC_ABILITY",
  "config": {
    "abilityType": "KAFKA",
    "bootstrapServers": "localhost:9092",
    "topic": "demo-topic",
    "key": "order-1",
    "value": {
      "event": "created"
    }
  }
}
```

支持字段：

- `abilityType`
  - 固定为 `KAFKA`
- `bootstrapServers`
  - 必填
- `topic`
  - 必填
- `key`
  - 可选
- `value`
  - 可选，不填时默认使用上游输入

## 5. `DIGITAL_EMPLOYEE`

用途：

- 当前阶段把数字员工节点视作 REST 接口风格调用
- 本质上是对 `REST` 能力的语义包装

示例：

```json
{
  "id": "digital_employee_1",
  "name": "Invoke Digital Employee",
  "type": "DIGITAL_EMPLOYEE",
  "config": {
    "url": "http://localhost:8080/api/mock/echo",
    "method": "POST",
    "body": {
      "employeeCode": "demo_employee",
      "task": "summarize"
    }
  }
}
```

说明：

- 当前执行时会被转换成 `REST` 类型原子能力
- 因此支持大部分 `REST` 节点配置
- 后续可以再补更明确的数字员工协议字段

## 6. `CONDITION`

用途：

- 根据条件决定走哪条边

推荐写法：

```json
{
  "id": "condition_1",
  "name": "Check Approval",
  "type": "CONDITION",
  "config": {}
}
```

说明：

- 当前条件主要写在 `edges[].condition`
- `CONDITION` 节点本身更多是一个语义分叉点

边上的条件示例：

```json
{
  "from": "condition_1",
  "to": "approved_1",
  "condition": "input.approved == true"
}
```

## 7. `WAIT_FOR_FEEDBACK`

用途：

- 让流程在该节点暂停，等待人工反馈后继续执行

示例：

```json
{
  "id": "review_1",
  "name": "Wait For Manual Review",
  "type": "WAIT_FOR_FEEDBACK",
  "config": {
    "feedbackKey": "manual_review"
  }
}
```

说明：

- 当前实例运行到该节点时，会进入等待/暂停状态
- 前端可通过实例详情页提交反馈
- 提交后会写入 `feedback_record`
- 流程继续执行后续节点

当前推荐字段：

- `feedbackKey`
  - 反馈语义标识
  - 当前更多用于说明和后续扩展

## 8. 未来保留节点

当前暂不 fully implement，但保留这些类型：

- `LLM`
- `AGENT`
- `TOOL`

后续新增时，建议统一遵守：

1. 明确节点语义
2. 定义 `config` schema
3. 实现独立 executor
4. 增加示例 DSL
5. 增加前端模板与表单
6. 增加文档说明

## 9. 当前最常见的配置错误

### 错误 1：把节点专有字段写在顶层

错误示例：

```json
{
  "id": "ability_1",
  "name": "Bad Example",
  "type": "ATOMIC_ABILITY",
  "abilityType": "REST"
}
```

正确示例：

```json
{
  "id": "ability_1",
  "name": "Good Example",
  "type": "ATOMIC_ABILITY",
  "config": {
    "abilityType": "REST"
  }
}
```

### 错误 2：缺少 `START` 或 `END`

流程定义必须有清晰起点和终点。

### 错误 3：边引用了不存在的节点

`edges[].from` 和 `edges[].to` 都必须能在 `nodes` 中找到对应 `id`。

### 错误 4：原子能力缺少关键配置

例如：

- `REST` 没有 `url`
- `DATABASE` 没有 `jdbcUrl` 或 `sql`
- `KAFKA` 没有 `bootstrapServers` 或 `topic`

## 10. 后续建议

如果要继续降低 DSL 编写门槛，后续最值得做的是：

1. 节点模板选择器
2. 节点配置表单
3. JSON 实时预览
4. 发布前 schema 校验

这会比单纯依赖大文本框更适合长期使用。
