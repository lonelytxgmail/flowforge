import type { WorkflowDsl } from "./types";

export type DslValidationIssue = {
  level: "error" | "warning";
  message: string;
};

function hasText(value: unknown): boolean {
  return String(value ?? "").trim().length > 0;
}

export function validateWorkflowDsl(dsl: WorkflowDsl): DslValidationIssue[] {
  const issues: DslValidationIssue[] = [];
  const nodeIds = dsl.nodes.map((node) => node.id.trim());
  const nodeIdSet = new Set(nodeIds.filter(Boolean));

  if (!hasText(dsl.version)) {
    issues.push({ level: "error", message: "DSL version 不能为空。" });
  }
  if (dsl.nodes.length === 0) {
    issues.push({ level: "error", message: "至少需要一个节点。" });
  }
  if (dsl.edges.length === 0) {
    issues.push({ level: "warning", message: "当前没有任何连线，流程可能无法继续执行。" });
  }
  if (nodeIds.some((id) => id.length === 0)) {
    issues.push({ level: "error", message: "节点 ID 不能为空。" });
  }
  if (new Set(nodeIds).size !== nodeIds.length) {
    issues.push({ level: "error", message: "节点 ID 不能重复。" });
  }

  const startNodes = dsl.nodes.filter((node) => node.type === "START");
  const endNodes = dsl.nodes.filter((node) => node.type === "END");
  if (startNodes.length === 0) {
    issues.push({ level: "error", message: "DSL 必须包含 START 节点。" });
  }
  if (startNodes.length > 1) {
    issues.push({ level: "error", message: "DSL 只能包含一个 START 节点。" });
  }
  if (endNodes.length === 0) {
    issues.push({ level: "error", message: "DSL 必须包含 END 节点。" });
  }
  if (endNodes.length > 1) {
    issues.push({ level: "error", message: "DSL 只能包含一个 END 节点。" });
  }

  dsl.edges.forEach((edge, index) => {
    if (!nodeIdSet.has(edge.from)) {
      issues.push({ level: "error", message: `连线 ${index + 1} 的起点节点不存在: ${edge.from}` });
    }
    if (!nodeIdSet.has(edge.to)) {
      issues.push({ level: "error", message: `连线 ${index + 1} 的目标节点不存在: ${edge.to}` });
    }
    if (edge.from === edge.to) {
      issues.push({ level: "warning", message: `连线 ${index + 1} 指向自身节点，请确认是否符合预期。` });
    }
  });

  dsl.nodes.forEach((node) => {
    const config = (node.config ?? {}) as Record<string, unknown>;
    const incoming = dsl.edges.filter((edge) => edge.to === node.id);
    const outgoing = dsl.edges.filter((edge) => edge.from === node.id);

    if (node.type !== "START" && incoming.length === 0) {
      issues.push({ level: "warning", message: `节点 ${node.id} 没有上游连线。` });
    }
    if (node.type !== "END" && outgoing.length === 0) {
      issues.push({ level: "warning", message: `节点 ${node.id} 没有下游连线。` });
    }
    if (node.type === "CONDITION" && outgoing.some((edge) => !hasText(edge.condition))) {
      issues.push({ level: "error", message: `条件节点 ${node.id} 的所有出口边都必须填写条件表达式。` });
    }
    if (node.type === "WAIT_FOR_FEEDBACK" && !hasText(config.feedbackKey)) {
      issues.push({ level: "error", message: `节点 ${node.id} 缺少反馈键 feedbackKey。` });
    }
    if (node.type === "DIGITAL_EMPLOYEE") {
      if (!hasText(config.url)) {
        issues.push({ level: "error", message: `节点 ${node.id} 缺少数字员工接口地址。` });
      }
      if (!hasText(config.employeeCode)) {
        issues.push({ level: "warning", message: `节点 ${node.id} 建议填写数字员工编码 employeeCode。` });
      }
    }
    if (node.type !== "ATOMIC_ABILITY") {
      return;
    }

    const abilityType = String(config.abilityType ?? "MOCK");
    if (abilityType === "MOCK" && !hasText(config.abilityCode)) {
      issues.push({ level: "error", message: `节点 ${node.id} 缺少能力编码 abilityCode。` });
    }
    if (abilityType === "REST") {
      if (!hasText(config.url)) {
        issues.push({ level: "error", message: `节点 ${node.id} 缺少 REST URL。` });
      }
      if (!hasText(config.method)) {
        issues.push({ level: "warning", message: `节点 ${node.id} 建议明确填写 HTTP Method。` });
      }
    }
    if (abilityType === "DATABASE") {
      if (!hasText(config.jdbcUrl)) {
        issues.push({ level: "error", message: `节点 ${node.id} 缺少 JDBC URL。` });
      }
      if (!hasText(config.sql)) {
        issues.push({ level: "error", message: `节点 ${node.id} 缺少 SQL。` });
      }
    }
    if (abilityType === "REDIS") {
      if (!hasText(config.host)) {
        issues.push({ level: "error", message: `节点 ${node.id} 缺少 Redis Host。` });
      }
      if (!hasText(config.operation)) {
        issues.push({ level: "warning", message: `节点 ${node.id} 建议明确填写 Redis Operation。` });
      }
    }
    if (abilityType === "ELASTICSEARCH" && !hasText(config.url)) {
      issues.push({ level: "error", message: `节点 ${node.id} 缺少 Elasticsearch URL。` });
    }
    if (abilityType === "KAFKA") {
      if (!hasText(config.bootstrapServers)) {
        issues.push({ level: "error", message: `节点 ${node.id} 缺少 Kafka Servers。` });
      }
      if (!hasText(config.topic)) {
        issues.push({ level: "error", message: `节点 ${node.id} 缺少 Kafka Topic。` });
      }
    }
  });

  return issues;
}
