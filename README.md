# FlowForge

FlowForge 是一个面向“工作流/策略编排”的模块化单体项目。

当前阶段已经落下了第一版后端骨架，并打通一条最小闭环：

1. 创建工作流定义
2. 发布工作流版本
3. 启动工作流实例
4. 执行 `开始 -> 原子能力节点 -> 结束`
5. 记录 `node_execution` 和 `execution_event`
6. 提供 REST API 查询实例状态与执行日志

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

## 下一步建议

接下来优先做这几件事：

1. 把 `NodeExecutor` 抽象正式拆出来
2. 引入 `workflow_task` 的数据库调度模型
3. 加上暂停 / 恢复 / 重试 API
4. 增加数字员工节点和条件分支节点
5. 给前端管理台补最小页面
