# FlowForge DSL Spec

这份文档说明第一阶段 `Workflow DSL` 的结构、约束和变量规则。

当前阶段的原则是：

- `DSL` 是流程定义的唯一真相源
- 表单编辑、JSON 编辑、后续图形化画布，最终都落到这份 DSL

## 1. 顶层结构

第一阶段发布版本时，请求体中的 `dsl` 字段结构如下：

```json
{
  "dsl": {
    "version": "1.0",
    "nodes": [],
    "edges": []
  }
}
```

其中：

- `version`
  - DSL 版本号
  - 当前固定使用 `"1.0"`
- `nodes`
  - 节点数组
- `edges`
  - 边数组，描述节点之间的连线关系

最小约束：

- `version` 必填，当前固定使用 `"1.0"`
- `nodes` 必填，且不能为空数组
- `edges` 必填
- 节点 `id` 在同一个工作流内必须唯一
- 至少包含一个 `START` 节点和一个 `END` 节点
- 边的 `from` / `to` 必须引用实际存在的节点
- 当前阶段建议只使用一个 `START` 和一个 `END`

## 2. 节点结构

每个节点统一使用下面的结构：

```json
{
  "id": "ability_1",
  "name": "Mock Atomic Ability",
  "type": "ATOMIC_ABILITY",
  "config": {}
}
```

字段说明：

- `id`
  - 节点唯一标识
  - 建议在同一个工作流内唯一且稳定
- `name`
  - 节点显示名称
- `type`
  - 节点类型
- `config`
  - 节点配置
  - 具体字段由节点类型决定

## 3. 边结构

边结构如下：

```json
{
  "from": "start_1",
  "to": "ability_1",
  "condition": "steps.review_1.output.approved == true"
}
```

字段说明：

- `from`
  - 起始节点 ID
- `to`
  - 目标节点 ID
- `condition`
  - 可选
  - 当前主要用于条件分支

注意：

- 普通线性流程可以不写 `condition`
- 条件分支时，条件通常写在边上，而不是写在目标节点上
- `CONDITION` 节点的出口边建议显式填写 `condition`

## 4. 当前支持的节点类型

第一阶段当前支持：

- `START`
- `END`
- `ATOMIC_ABILITY`
- `DIGITAL_EMPLOYEE`
- `CONDITION`
- `WAIT_FOR_FEEDBACK`

未来保留位：

- `LLM`
- `AGENT`
- `TOOL`

说明：

- 以上保留位不属于当前阶段管理台可稳定配置的节点能力
- 当前阶段文档、结构化编辑器和运行时以已列出的 6 种节点为准

## 5. 最小流程示例

```json
{
  "dsl": {
    "version": "1.0",
    "nodes": [
      {
        "id": "start_1",
        "name": "Start",
        "type": "START",
        "config": {}
      },
      {
        "id": "ability_1",
        "name": "Mock Atomic Ability",
        "type": "ATOMIC_ABILITY",
        "config": {
          "abilityCode": "mock.echo"
        }
      },
      {
        "id": "end_1",
        "name": "End",
        "type": "END",
        "config": {}
      }
    ],
    "edges": [
      {
        "from": "start_1",
        "to": "ability_1"
      },
      {
        "from": "ability_1",
        "to": "end_1"
      }
    ]
  }
}
```

## 6. 条件分支示例

```json
{
  "dsl": {
    "version": "1.0",
    "nodes": [
      {
        "id": "start_1",
        "name": "Start",
        "type": "START",
        "config": {}
      },
      {
        "id": "condition_1",
        "name": "Check Approval",
        "type": "CONDITION",
        "config": {}
      },
      {
        "id": "approved_1",
        "name": "Approved Branch",
        "type": "ATOMIC_ABILITY",
        "config": {
          "abilityCode": "mock.echo"
        }
      },
      {
        "id": "rejected_1",
        "name": "Rejected Branch",
        "type": "ATOMIC_ABILITY",
        "config": {
          "abilityCode": "mock.echo"
        }
      },
      {
        "id": "end_1",
        "name": "End",
        "type": "END",
        "config": {}
      }
    ],
    "edges": [
      { "from": "start_1", "to": "condition_1" },
      { "from": "condition_1", "to": "approved_1", "condition": "input.approved == true" },
      { "from": "condition_1", "to": "rejected_1", "condition": "input.approved == false" },
      { "from": "approved_1", "to": "end_1" },
      { "from": "rejected_1", "to": "end_1" }
    ]
  }
}
```

## 7. 等待反馈示例

```json
{
  "dsl": {
    "version": "1.0",
    "nodes": [
      {
        "id": "start_1",
        "name": "Start",
        "type": "START",
        "config": {}
      },
      {
        "id": "review_1",
        "name": "Wait For Manual Review",
        "type": "WAIT_FOR_FEEDBACK",
        "config": {
          "feedbackKey": "manual_review"
        }
      },
      {
        "id": "ability_1",
        "name": "Mock Atomic Ability",
        "type": "ATOMIC_ABILITY",
        "config": {
          "abilityCode": "mock.echo"
        }
      },
      {
        "id": "end_1",
        "name": "End",
        "type": "END",
        "config": {}
      }
    ],
    "edges": [
      { "from": "start_1", "to": "review_1" },
      { "from": "review_1", "to": "ability_1" },
      { "from": "ability_1", "to": "end_1" }
    ]
  }
}
```

## 8. 变量来源约定

第一阶段建议统一把变量来源分为 4 类：

- `input`
  - 启动实例时传入的输入参数
- `context`
  - 工作流共享上下文
- `steps`
  - 前面节点的输出结果
- `system`
  - 系统内置变量

建议变量引用格式：

```text
{{ input.orderId }}
{{ context.auth.token }}
{{ steps.review_1.output.approved }}
{{ system.instanceId }}
```

当前说明：

- 这套变量语法目前是推荐规范
- 第一阶段会逐步补到结构化编辑和节点模板中
- 后续各节点配置应尽量统一使用这套变量来源

## 9. 条件表达式约定

`CONDITION` 节点的条件建议使用简单表达式：

```text
input.amount > 1000
input.approved == true
steps.review_1.output.score >= 80
```

第一阶段建议只支持：

- 比较运算
  - `==`
  - `!=`
  - `>`
  - `>=`
  - `<`
  - `<=`
- 简单布尔值判断
- 基于路径取值

先不要在第一阶段引入过于复杂的脚本化表达式。

## 10. 节点配置总规则

所有节点配置都遵守：

1. 通用字段只放在节点顶层
   - `id`
   - `name`
   - `type`
   - `config`

2. 节点专有配置统一放进 `config`

这意味着像下面这样的结构是不推荐的：

```json
{
  "id": "ability_1",
  "name": "Bad Example",
  "type": "ATOMIC_ABILITY",
  "abilityType": "REST"
}
```

应该写成：

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

## 11. 当前基础校验建议

发布版本前至少校验这些内容：

1. `version` 非空
2. `nodes` 非空
3. `edges` 非空
4. 节点 ID 唯一
5. `from` 和 `to` 引用的节点必须存在
6. 至少有一个 `START`
7. 至少有一个 `END`
8. 每个节点必须有合法的 `type`
9. 节点特定配置的必填项要齐全

## 12. 与图形化编辑的关系

后续如果引入图形化拖拽，仍然以这份 DSL 为真相源：

- 画布节点对应 `nodes`
- 连线对应 `edges`
- 右侧属性面板编辑 `config`

图形化画布不应该引入另一套独立流程模型。
