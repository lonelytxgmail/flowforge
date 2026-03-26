# FlowForge

FlowForge 是一个面向“工作流/策略编排”的模块化单体项目。

当前阶段已经落下了第一版后端骨架，并打通一条最小闭环：

1. 创建工作流定义
2. 发布工作流版本
3. 启动工作流实例
4. 执行 `开始 -> 原子能力节点 -> 结束`
5. 记录 `node_execution` 和 `execution_event`
6. 提供 REST API 查询实例状态与执行日志

同时，执行模型已经从“同步 while 循环”升级为“数据库任务表 + 应用内 worker”：

- `workflow_task` 表保存待执行节点
- `WorkflowTaskWorker` 轮询并消费任务
- `NodeExecutor` 负责具体节点执行

这会让后续的暂停、恢复、重试、定时触发更容易实现。

当前已经支持的节点能力：

- `START`
- `END`
- `ATOMIC_ABILITY`
- `DIGITAL_EMPLOYEE`
- `CONDITION`
- `WAIT_FOR_FEEDBACK`

其中 `ATOMIC_ABILITY` 当前支持这些能力类型：

- `REST`
- `REDIS`
- `DATABASE`
- `ELASTICSEARCH`
- `KAFKA`

其中 `DATABASE` 可直接连接 `PostgreSQL / MySQL`。

目前已经补上的运行态控制 API：

- `POST /api/instances/{id}/pause`
- `POST /api/instances/{id}/resume`
- `POST /api/instances/{id}/retry`
- `POST /api/instances/{id}/feedback`

目前已经补上的基础查询 API：

- `GET /api/workflows`
- `GET /api/workflows/{id}`
- `GET /api/workflows/{id}/versions`
- `GET /api/instances`
- `GET /api/instances/{id}`
- `GET /api/instances/{id}/node-executions`
- `GET /api/instances/{id}/events`
- `GET /api/instances/{id}/feedback-records`

## 为什么这样搭

- 后端使用 `Kotlin + Spring Boot 3 + PostgreSQL`
- 架构采用“模块化单体”，而不是一开始拆微服务
- 运行态全部持久化在 PostgreSQL，不依赖 MQ / Redis 才能跑
- DSL、执行引擎、运行态、API 解耦，方便后续扩展 LLM / Agent / Tool

## 当前目录

```text
flowforge/
├── flowforge-app/            # Spring Boot 启动模块
├── modules/
│   ├── flowforge-common/     # 公共模型、枚举、JSONB 工具
│   ├── flowforge-workflow/   # 工作流定义、版本、DSL
│   ├── flowforge-runtime/    # 实例、节点执行、事件查询
│   ├── flowforge-engine/     # 最小执行引擎
│   └── flowforge-api/        # REST API
├── flowforge-console/        # 前端管理台占位
├── docker-compose.yml
└── README.md
```

## 核心文档

- [第一阶段计划](/Users/lee/LeeProject/flowforge/docs/phase-one-plan.md)
- [开发路线图](/Users/lee/LeeProject/flowforge/docs/development-roadmap.md)
- [DSL 规范](/Users/lee/LeeProject/flowforge/docs/dsl-spec.md)
- [节点目录](/Users/lee/LeeProject/flowforge/docs/node-catalog.md)

## 运行前提

### 1. Java

Spring Boot 3 需要 Java 17+，这里目标版本是 Java 21。

你本机已经有 Java 21，可以这样切换：

```bash
export JAVA_HOME=/Users/lee/Library/Java/JavaVirtualMachines/graalvm-ce-21.0.2/Contents/Home
export PATH=$JAVA_HOME/bin:$PATH
java -version
```

### 2. direnv

你已经安装了 `direnv`，这个项目也已经带上了 [.envrc](/Users/lee/LeeProject/flowforge/.envrc)。

只要你的 `zsh` 已经启用 `direnv hook`，第一次进入项目目录执行一次：

```bash
direnv allow
```

之后每次进入这个目录，都会自动切到 Java 21。

### 3. Gradle Wrapper

项目已经自带 `Gradle Wrapper`，所以你不需要全局安装 `gradle`。

统一使用：

```bash
./gradlew
```

## 本地启动 PostgreSQL

```bash
docker compose up -d postgres
```

数据库默认信息：

- database: `flowforge`
- username: `flowforge`
- password: `flowforge`
- port: `5432`

## 启动应用

在项目目录下，先让 `direnv` 生效，然后直接运行：

```bash
direnv allow
./gradlew :flowforge-app:bootRun
```

或者打包：

```bash
./gradlew build
java -jar flowforge-app/build/libs/flowforge-app.jar
```

## Swagger

应用启动后访问：

- [Swagger UI](http://localhost:8080/swagger-ui.html)

## 最小闭环示例

### 1. 创建工作流定义

```bash
curl -X POST http://localhost:8080/api/workflows \
  -H 'Content-Type: application/json' \
  -d '{
    "code": "demo_workflow",
    "name": "Demo Workflow",
    "description": "A minimal demo workflow"
  }'
```

### 2. 发布版本

假设上一步返回的工作流定义 `id = 1`

```bash
curl -X POST http://localhost:8080/api/workflows/1/versions \
  -H 'Content-Type: application/json' \
  -d @docs/minimal-workflow.json
```

### 3. 启动实例

```bash
curl -X POST http://localhost:8080/api/workflows/1/instances \
  -H 'Content-Type: application/json' \
  -d '{
    "inputPayload": {
      "orderId": "A-10001"
    }
  }'
```

### 4. 查询实例

```bash
curl http://localhost:8080/api/instances/1
```

### 5. 查询节点执行记录

```bash
curl http://localhost:8080/api/instances/1/node-executions
```

### 6. 查询事件日志

```bash
curl http://localhost:8080/api/instances/1/events
```

## 代码阅读建议

如果你第一次接触 Kotlin，建议按下面顺序读：

1. [FlowforgeApplication.kt](/Users/lee/LeeProject/flowforge/flowforge-app/src/main/kotlin/com/flowforge/app/FlowforgeApplication.kt)
2. [WorkflowController.kt](/Users/lee/LeeProject/flowforge/modules/flowforge-api/src/main/kotlin/com/flowforge/api/workflow/WorkflowController.kt)
3. [WorkflowEngineService.kt](/Users/lee/LeeProject/flowforge/modules/flowforge-engine/src/main/kotlin/com/flowforge/engine/WorkflowEngineService.kt)
4. [WorkflowDefinitionService.kt](/Users/lee/LeeProject/flowforge/modules/flowforge-workflow/src/main/kotlin/com/flowforge/workflow/application/WorkflowDefinitionService.kt)
5. [RuntimeRepositories.kt](/Users/lee/LeeProject/flowforge/modules/flowforge-runtime/src/main/kotlin/com/flowforge/runtime/infra/RuntimeRepositories.kt)

## 当前阶段的取舍

- 先同步执行，不先上 MQ
- 先手写 JDBC，不先上 JPA
- 先 JSON DSL，不先做画布
- 先 mock 原子能力执行器，不接真实数字员工
- `workflow_task` / `feedback_record` 先建表，后续补完整逻辑

## 关于“定时触发执行”

当前项目已经具备两个和“定时”相关的基础能力，但还没有完整的“业务定时触发”功能：

1. 已有应用内定时 worker
   作用是每隔几秒扫描一次 `workflow_task`，把待执行节点跑起来。
   这解决的是“任务消费”问题，不是“到点自动发起一个新实例”。

2. 还没有正式实现业务定时触发
   也就是还没有：
   - `schedule_plan` 之类的计划表
   - 创建/启停定时计划的 API
   - 到点自动创建 `workflow_instance` 的调度器

如果你要做第一阶段可用版，我建议下一步按这个方案实现：

- 新增 `workflow_schedule` 表
- 存 `workflow_definition_id`、cron 表达式、启停状态、最近执行时间、下次执行时间
- 用 Spring `@Scheduled` 定时扫描“到期计划”
- 到期后调用现有 `WorkflowEngineService.startWorkflow(...)`

这个方案和当前“数据库任务表 + 应用内 worker”是兼容的，而且仍然不需要 MQ / Redis。

## 等待反馈节点示例

现在已经支持 `WAIT_FOR_FEEDBACK` 节点。

可以使用：

- [wait-for-feedback-workflow.json](/Users/lee/LeeProject/flowforge/docs/wait-for-feedback-workflow.json)

运行方式：

1. 发布该 DSL
2. 启动实例
3. 实例会在 `WAIT_FOR_FEEDBACK` 节点进入 `PAUSED`
4. 调用 `POST /api/instances/{id}/feedback`
5. 实例恢复执行并继续往后跑

## 其他 DSL 示例

- [condition-workflow.json](/Users/lee/LeeProject/flowforge/docs/condition-workflow.json)
- [rest-login-session-workflow.json](/Users/lee/LeeProject/flowforge/docs/rest-login-session-workflow.json)
- [database-workflow.json](/Users/lee/LeeProject/flowforge/docs/database-workflow.json)
- [digital-employee-workflow.json](/Users/lee/LeeProject/flowforge/docs/digital-employee-workflow.json)

## REST 节点认证模式

当前 `REST` 原子能力节点支持这些认证方式：

- `none`
- `basic`
- `bearer_static`
- `login_session`

其中 `login_session` 适合：

- 先登录拿 token / session
- 再调用后续接口
- 多个节点复用同一份会话

做法是：

1. 登录节点配置 `auth.type = login_session`
2. 指定 `loginUrl`、`tokenPath`、`sessionContextKey`
3. 登录成功后，会话会写入 `workflow_instance.context_json`
4. 后续节点只要引用同一个 `sessionContextKey`，就会自动带上认证头

## 下一步建议

接下来优先做这几件事：

1. 把 `NodeExecutor` 抽象正式拆出来
2. 引入 `workflow_task` 的数据库调度模型
3. 加上暂停 / 恢复 / 重试 API
4. 增加数字员工节点和条件分支节点
5. 给前端管理台补最小页面
