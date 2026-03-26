import type { ChangeEvent } from "react";
import { useMemo, useState } from "react";
import { api } from "../lib/api";
import { prettyJson, tryParseJson } from "../lib/format";
import { useAsyncData } from "../lib/hooks";
import { useI18n } from "../lib/i18n";
import type { NodeTemplate, WorkflowDsl, WorkflowDslEdge, WorkflowDslNode } from "../lib/types";

type DslStructuredEditorProps = {
  dsl: WorkflowDsl;
  onChange: (dsl: WorkflowDsl) => void;
};

type NodeTemplateType = "START" | "END" | "ATOMIC_ABILITY" | "DIGITAL_EMPLOYEE" | "WAIT_FOR_FEEDBACK" | "CONDITION";
type AtomicAbilityKind = "MOCK" | "REST" | "DATABASE" | "REDIS" | "ELASTICSEARCH" | "KAFKA";
type VariableToken = {
  key: "input" | "context" | "steps" | "system";
  example: string;
};

const nodeTemplateTypes: NodeTemplateType[] = ["START", "END", "ATOMIC_ABILITY", "DIGITAL_EMPLOYEE", "WAIT_FOR_FEEDBACK", "CONDITION"];
const atomicAbilityKinds: AtomicAbilityKind[] = ["MOCK", "REST", "DATABASE", "REDIS", "ELASTICSEARCH", "KAFKA"];
const variableTokens: VariableToken[] = [
  { key: "input", example: "{{ input.orderId }}" },
  { key: "context", example: "{{ context.authSession.token }}" },
  { key: "steps", example: "{{ steps.review_1.output.approved }}" },
  { key: "system", example: "{{ system.instanceId }}" },
];

function createNodeTemplate(type: NodeTemplateType, index: number): WorkflowDslNode {
  const suffix = index + 1;

  switch (type) {
    case "START":
      return { id: `start_${suffix}`, name: "Start", type, config: {} };
    case "END":
      return { id: `end_${suffix}`, name: "End", type, config: {} };
    case "WAIT_FOR_FEEDBACK":
      return {
        id: `review_${suffix}`,
        name: "Wait For Review",
        type,
        config: { feedbackKey: "manual_review" },
      };
    case "CONDITION":
      return { id: `condition_${suffix}`, name: "Condition", type, config: {} };
    case "DIGITAL_EMPLOYEE":
      return {
        id: `employee_${suffix}`,
        name: "Digital Employee",
        type,
        config: {
          url: "http://localhost:8080/api/mock/digital-employee",
          method: "POST",
          body: {
            employeeCode: "demo.employee",
            payload: "{{ input }}",
          },
        },
      };
    case "ATOMIC_ABILITY":
    default:
      return {
        id: `ability_${suffix}`,
        name: "Atomic Ability",
        type: "ATOMIC_ABILITY",
        config: { abilityCode: "mock.echo" },
      };
  }
}

function normalizeNode(node: WorkflowDslNode): WorkflowDslNode {
  return {
    ...node,
    config: (node.config as Record<string, unknown> | undefined) ?? {},
  };
}

function updateNode(nodes: WorkflowDslNode[], nodeId: string, updater: (node: WorkflowDslNode) => WorkflowDslNode): WorkflowDslNode[] {
  return nodes.map((node) => (node.id === nodeId ? updater(normalizeNode(node)) : node));
}

function stringifyObject(value: unknown): string {
  if (value === undefined) {
    return "";
  }

  return prettyJson(value);
}

function normalizeEdge(edge: WorkflowDslEdge): WorkflowDslEdge {
  return {
    from: edge.from,
    to: edge.to,
    condition: edge.condition ?? null,
  };
}

function withDefaultStringMap(value: unknown): Record<string, string> {
  if (!value || typeof value !== "object" || Array.isArray(value)) {
    return {};
  }

  return Object.entries(value as Record<string, unknown>).reduce<Record<string, string>>((acc, [key, item]) => {
    acc[key] = item === undefined || item === null ? "" : String(item);
    return acc;
  }, {});
}

function validateDsl(dsl: WorkflowDsl): string[] {
  const issues: string[] = [];
  const nodes = dsl.nodes.map(normalizeNode);
  const nodeIds = nodes.map((node) => node.id.trim());
  const uniqueIds = new Set(nodeIds);

  if (nodes.length === 0) {
    issues.push("至少需要一个节点。");
  }
  if (nodeIds.some((id) => id.length === 0)) {
    issues.push("节点 ID 不能为空。");
  }
  if (uniqueIds.size !== nodeIds.length) {
    issues.push("节点 ID 不能重复。");
  }
  if (!nodes.some((node) => node.type === "START")) {
    issues.push("DSL 必须包含 START 节点。");
  }
  if (!nodes.some((node) => node.type === "END")) {
    issues.push("DSL 必须包含 END 节点。");
  }

  const nodeIdSet = new Set(nodeIds);
  dsl.edges.forEach((edge, index) => {
    if (!nodeIdSet.has(edge.from)) {
      issues.push(`连线 ${index + 1} 的起点节点不存在: ${edge.from}`);
    }
    if (!nodeIdSet.has(edge.to)) {
      issues.push(`连线 ${index + 1} 的目标节点不存在: ${edge.to}`);
    }
  });

  nodes.forEach((node) => {
    const config = (node.config ?? {}) as Record<string, unknown>;
    if (node.type === "ATOMIC_ABILITY") {
      const abilityType = String(config.abilityType ?? "MOCK");
      if (abilityType === "REST" && !String(config.url ?? "").trim()) {
        issues.push(`节点 ${node.id} 缺少 REST URL。`);
      }
      if (abilityType === "DATABASE" && !String(config.jdbcUrl ?? "").trim()) {
        issues.push(`节点 ${node.id} 缺少 JDBC URL。`);
      }
      if (abilityType === "DATABASE" && !String(config.sql ?? "").trim()) {
        issues.push(`节点 ${node.id} 缺少 SQL。`);
      }
      if (abilityType === "REDIS" && !String(config.host ?? "").trim()) {
        issues.push(`节点 ${node.id} 缺少 Redis Host。`);
      }
      if (abilityType === "ELASTICSEARCH" && !String(config.url ?? "").trim()) {
        issues.push(`节点 ${node.id} 缺少 Elasticsearch URL。`);
      }
      if (abilityType === "KAFKA" && !String(config.bootstrapServers ?? "").trim()) {
        issues.push(`节点 ${node.id} 缺少 Kafka Servers。`);
      }
      if (abilityType === "KAFKA" && !String(config.topic ?? "").trim()) {
        issues.push(`节点 ${node.id} 缺少 Kafka Topic。`);
      }
    }

    if (node.type === "DIGITAL_EMPLOYEE" && !String(config.url ?? "").trim()) {
      issues.push(`节点 ${node.id} 缺少数字员工接口地址。`);
    }

    if (node.type === "WAIT_FOR_FEEDBACK" && !String(config.feedbackKey ?? "").trim()) {
      issues.push(`节点 ${node.id} 缺少反馈键。`);
    }
  });

  return issues;
}

export function DslStructuredEditor({ dsl, onChange }: DslStructuredEditorProps) {
  const { t } = useI18n();
  const templates = useAsyncData(() => api.listNodeTemplates(), []);
  const [templateMessage, setTemplateMessage] = useState<string | null>(null);
  const [copiedToken, setCopiedToken] = useState<string | null>(null);
  const [templateFilter, setTemplateFilter] = useState("");

  const normalizedDsl: WorkflowDsl = {
    version: dsl.version || "1.0",
    nodes: dsl.nodes.map(normalizeNode),
    edges: dsl.edges.map(normalizeEdge),
  };
  const validationIssues = useMemo(() => validateDsl(normalizedDsl), [normalizedDsl]);
  const filteredTemplates = useMemo(() => {
    const keyword = templateFilter.trim().toLowerCase();
    const items = templates.data ?? [];
    if (!keyword) {
      return items;
    }
    return items.filter((template) =>
      [template.name, template.code, template.nodeType, template.description ?? ""]
        .join(" ")
        .toLowerCase()
        .includes(keyword)
    );
  }, [templateFilter, templates.data]);

  function patch(next: Partial<WorkflowDsl>) {
    onChange({
      ...normalizedDsl,
      ...next,
    });
  }

  function addNode(type: NodeTemplateType) {
    const nextNode = createNodeTemplate(type, normalizedDsl.nodes.length);
    const nextNodes = [...normalizedDsl.nodes, nextNode];
    const previousNode = normalizedDsl.nodes[normalizedDsl.nodes.length - 1];
    const nextEdges =
      previousNode && previousNode.type !== "END"
        ? [...normalizedDsl.edges, { from: previousNode.id, to: nextNode.id }]
        : normalizedDsl.edges;

    patch({ nodes: nextNodes, edges: nextEdges });
  }

  function addNodeFromTemplate(template: NodeTemplate) {
    const nextNode: WorkflowDslNode = {
      id: `${template.nodeType.toLowerCase()}_${normalizedDsl.nodes.length + 1}`,
      name: template.name,
      type: template.nodeType,
      config: template.nodeConfig ?? {},
    };

    const previousNode = normalizedDsl.nodes[normalizedDsl.nodes.length - 1];
    patch({
      nodes: [...normalizedDsl.nodes, nextNode],
      edges:
        previousNode && previousNode.type !== "END"
          ? [...normalizedDsl.edges, { from: previousNode.id, to: nextNode.id }]
          : normalizedDsl.edges,
    });
  }

  function removeNode(nodeId: string) {
    patch({
      nodes: normalizedDsl.nodes.filter((node) => node.id !== nodeId),
      edges: normalizedDsl.edges.filter((edge) => edge.from !== nodeId && edge.to !== nodeId),
    });
  }

  function updateNodeField(nodeId: string, field: "id" | "name", value: string) {
    patch({
      nodes: updateNode(normalizedDsl.nodes, nodeId, (node) => ({ ...node, [field]: value })),
      edges: normalizedDsl.edges.map((edge) => ({
        ...edge,
        from: edge.from === nodeId ? value : edge.from,
        to: edge.to === nodeId ? value : edge.to,
      })),
    });
  }

  function updateNodeType(nodeId: string, nextType: NodeTemplateType) {
    patch({
      nodes: updateNode(normalizedDsl.nodes, nodeId, (node) => {
        const seed = createNodeTemplate(nextType, normalizedDsl.nodes.findIndex((item) => item.id === nodeId));
        return {
          ...seed,
          id: node.id,
          name: node.name,
        };
      }),
    });
  }

  function buildAtomicAbilityDefaults(kind: AtomicAbilityKind): Record<string, unknown> {
    switch (kind) {
      case "REST":
        return {
          abilityType: "REST",
          url: "http://localhost:8080/api/mock/echo",
          method: "POST",
          streaming: false,
          headers: {},
          body: { orderId: "{{ input.orderId }}" },
        };
      case "DATABASE":
        return {
          abilityType: "DATABASE",
          jdbcUrl: "jdbc:postgresql://localhost:5432/flowforge",
          username: "flowforge",
          password: "flowforge",
          operation: "QUERY",
          sql: "SELECT * FROM workflow_definition WHERE code = '{{ input.workflowCode }}'",
        };
      case "REDIS":
        return {
          abilityType: "REDIS",
          host: "127.0.0.1",
          port: 6379,
          password: "",
          operation: "GET",
          key: "flowforge:demo:{{ input.orderId }}",
          value: "{{ input }}",
          channel: "flowforge.events",
        };
      case "ELASTICSEARCH":
        return {
          abilityType: "ELASTICSEARCH",
          url: "http://localhost:9200/_cluster/health",
          method: "GET",
          username: "",
          password: "",
          body: {},
        };
      case "KAFKA":
        return {
          abilityType: "KAFKA",
          bootstrapServers: "localhost:9092",
          topic: "flowforge.events",
          key: "{{ system.instanceId }}",
          value: {
            workflowInstanceId: "{{ system.instanceId }}",
            payload: "{{ input }}",
          },
        };
      case "MOCK":
      default:
        return { abilityCode: "mock.echo" };
    }
  }

  function setAtomicAbilityKind(nodeId: string, kind: AtomicAbilityKind) {
    patch({
      nodes: updateNode(normalizedDsl.nodes, nodeId, (node) => ({
        ...node,
        config: buildAtomicAbilityDefaults(kind),
      })),
    });
  }

  function updateNodeConfig(nodeId: string, key: string, value: unknown) {
    patch({
      nodes: updateNode(normalizedDsl.nodes, nodeId, (node) => {
        const nextConfig = {
          ...(node.config ?? {}),
          [key]: value,
        };
        if (value === undefined) {
          delete nextConfig[key];
        }
        return {
          ...node,
          config: nextConfig,
        };
      }),
    });
  }

  function updateJsonConfig(nodeId: string, key: string, value: string) {
    if (value.trim() === "") {
      updateNodeConfig(nodeId, key, undefined);
      return;
    }

    const parsed = tryParseJson<unknown>(value);
    if (parsed.data !== undefined) {
      updateNodeConfig(nodeId, key, parsed.data);
    }
  }

  function addEdge() {
    const defaultFrom = normalizedDsl.nodes[0]?.id ?? "";
    const defaultTo = normalizedDsl.nodes[1]?.id ?? defaultFrom;
    patch({
      edges: [...normalizedDsl.edges, { from: defaultFrom, to: defaultTo, condition: null }],
    });
  }

  function updateEdge(index: number, field: keyof WorkflowDslEdge, value: string) {
    patch({
      edges: normalizedDsl.edges.map((edge, edgeIndex) =>
        edgeIndex === index
          ? {
              ...edge,
              [field]: field === "condition" && value.trim() === "" ? null : value,
            }
          : edge
      ),
    });
  }

  function removeEdge(index: number) {
    patch({
      edges: normalizedDsl.edges.filter((_, edgeIndex) => edgeIndex !== index),
    });
  }

  async function saveNodeAsTemplate(node: WorkflowDslNode) {
    try {
      const timestamp = Date.now();
      await api.saveNodeTemplate({
        code: `${node.type.toLowerCase()}_${timestamp}`,
        name: node.name,
        description: `Saved from structured editor at ${new Date(timestamp).toISOString()}`,
        nodeType: node.type,
        nodeConfig: (node.config as Record<string, unknown> | undefined) ?? {},
      });
      setTemplateMessage(t("dslEditor.templateSaved"));
      templates.reload();
    } catch (error) {
      setTemplateMessage(error instanceof Error ? error.message : t("dslEditor.templateSaveFailed"));
    }
  }

  async function copyVariableToken(token: string) {
    try {
      await navigator.clipboard.writeText(token);
      setCopiedToken(token);
    } catch {
      setCopiedToken(null);
    }
  }

  function renderVariableButtons() {
    return (
      <div className="variable-token-grid">
        {variableTokens.map((token) => (
          <button
            className="ghost-button compact"
            key={token.key}
            onClick={() => copyVariableToken(token.example)}
            type="button"
          >
            {t(`dslEditor.variable${token.key[0].toUpperCase()}${token.key.slice(1)}`)}
          </button>
        ))}
      </div>
    );
  }

  function renderHeaderEditor(nodeId: string, config: Record<string, unknown>) {
    const headers = withDefaultStringMap(config.headers);
    const headerRows = Object.entries(headers);

    function updateHeader(index: number, field: "key" | "value", value: string) {
      const nextEntries = headerRows.map(([key, currentValue], rowIndex) =>
        rowIndex === index ? (field === "key" ? [value, currentValue] : [key, value]) : [key, currentValue]
      );
      updateNodeConfig(nodeId, "headers", Object.fromEntries(nextEntries.filter(([key]) => key.trim() !== "")));
    }

    function addHeader() {
      updateNodeConfig(nodeId, "headers", { ...headers, "X-FlowForge-Key": "{{ system.instanceId }}" });
    }

    function removeHeader(index: number) {
      updateNodeConfig(
        nodeId,
        "headers",
        Object.fromEntries(headerRows.filter((_, rowIndex) => rowIndex !== index))
      );
    }

    return (
      <div className="field-block field-span-two">
        <div className="field-inline-title">
          <span className="field-label">{t("dslEditor.restHeaders")}</span>
          <button className="ghost-button compact" onClick={addHeader} type="button">
            {t("dslEditor.addHeader")}
          </button>
        </div>
        {!headerRows.length ? <p className="structured-editor-note">{t("dslEditor.noHeaders")}</p> : null}
        <div className="structured-subrows">
          {headerRows.map(([key, value], index) => (
            <div className="subrow-grid" key={`${key}-${index}`}>
              <input
                className="text-input"
                onChange={(event) => updateHeader(index, "key", event.target.value)}
                placeholder="Authorization"
                value={key}
              />
              <input
                className="text-input"
                onChange={(event) => updateHeader(index, "value", event.target.value)}
                placeholder="{{ context.authSession.token }}"
                value={value}
              />
              <button className="ghost-button compact" onClick={() => removeHeader(index)} type="button">
                {t("dslEditor.removeHeader")}
              </button>
            </div>
          ))}
        </div>
      </div>
    );
  }

  function renderAuthEditor(nodeId: string, config: Record<string, unknown>, digitalEmployee = false) {
    const auth = (config.auth as Record<string, unknown> | undefined) ?? {};
    const authType = String(auth.type ?? "none");

    function updateAuth(key: string, value: unknown) {
      updateNodeConfig(nodeId, "auth", {
        ...auth,
        [key]: value,
      });
    }

    function updateAuthJson(key: string, rawValue: string) {
      if (rawValue.trim() === "") {
        updateAuth(key, undefined);
        return;
      }
      const parsed = tryParseJson<unknown>(rawValue);
      if (parsed.data !== undefined) {
        updateAuth(key, parsed.data);
      }
    }

    return (
      <div className="field-block field-span-two">
        <div className="field-inline-title">
          <span className="field-label">
            {digitalEmployee ? t("dslEditor.digitalEmployeeAuth") : t("dslEditor.restAuth")}
          </span>
        </div>
        <div className="form-grid-two">
          <label className="field-block">
            <span className="field-label">{t("dslEditor.authType")}</span>
            <select
              className="text-input select-input"
              onChange={(event) => updateAuth("type", event.target.value)}
              value={authType}
            >
              <option value="none">{t("dslEditor.authNone")}</option>
              <option value="basic">{t("dslEditor.authBasic")}</option>
              <option value="bearer_static">{t("dslEditor.authBearer")}</option>
              <option value="login_session">{t("dslEditor.authLoginSession")}</option>
            </select>
          </label>

          {authType === "basic" ? (
            <>
              <label className="field-block">
                <span className="field-label">{t("dslEditor.authUsername")}</span>
                <input
                  className="text-input"
                  onChange={(event) => updateAuth("username", event.target.value)}
                  value={String(auth.username ?? "")}
                />
              </label>
              <label className="field-block">
                <span className="field-label">{t("dslEditor.authPassword")}</span>
                <input
                  className="text-input"
                  onChange={(event) => updateAuth("password", event.target.value)}
                  value={String(auth.password ?? "")}
                />
              </label>
            </>
          ) : null}

          {authType === "bearer_static" ? (
            <label className="field-block field-span-two">
              <span className="field-label">{t("dslEditor.authToken")}</span>
              <input
                className="text-input"
                onChange={(event) => updateAuth("token", event.target.value)}
                placeholder="{{ context.authSession.token }}"
                value={String(auth.token ?? "")}
              />
            </label>
          ) : null}

          {authType === "login_session" ? (
            <>
              <label className="field-block field-span-two">
                <span className="field-label">{t("dslEditor.authLoginUrl")}</span>
                <input
                  className="text-input"
                  onChange={(event) => updateAuth("loginUrl", event.target.value)}
                  value={String(auth.loginUrl ?? "")}
                />
              </label>
              <label className="field-block">
                <span className="field-label">{t("dslEditor.authLoginMethod")}</span>
                <select
                  className="text-input select-input"
                  onChange={(event) => updateAuth("loginMethod", event.target.value)}
                  value={String(auth.loginMethod ?? "POST")}
                >
                  <option value="POST">POST</option>
                  <option value="GET">GET</option>
                </select>
              </label>
              <label className="field-block">
                <span className="field-label">{t("dslEditor.authTokenPath")}</span>
                <input
                  className="text-input"
                  onChange={(event) => updateAuth("tokenPath", event.target.value)}
                  placeholder="data.token"
                  value={String(auth.tokenPath ?? "")}
                />
              </label>
              <label className="field-block">
                <span className="field-label">{t("dslEditor.authHeaderName")}</span>
                <input
                  className="text-input"
                  onChange={(event) => updateAuth("headerName", event.target.value)}
                  placeholder="Authorization"
                  value={String(auth.headerName ?? "")}
                />
              </label>
              <label className="field-block">
                <span className="field-label">{t("dslEditor.authPrefix")}</span>
                <input
                  className="text-input"
                  onChange={(event) => updateAuth("prefix", event.target.value)}
                  placeholder="Bearer"
                  value={String(auth.prefix ?? "")}
                />
              </label>
              <label className="field-block">
                <span className="field-label">{t("dslEditor.authSessionKey")}</span>
                <input
                  className="text-input"
                  onChange={(event) => updateAuth("sessionContextKey", event.target.value)}
                  placeholder="authSession"
                  value={String(auth.sessionContextKey ?? "")}
                />
              </label>
              <label className="field-block field-span-two">
                <span className="field-label">{t("dslEditor.authLoginBody")}</span>
                <textarea
                  className="text-area text-area-code text-area-medium"
                  onChange={(event: ChangeEvent<HTMLTextAreaElement>) => updateAuthJson("loginBody", event.target.value)}
                  value={stringifyObject(auth.loginBody)}
                />
              </label>
            </>
          ) : null}
        </div>
      </div>
    );
  }

  function renderAtomicAbilityEditor(node: WorkflowDslNode, config: Record<string, unknown>) {
    const atomicKind = String(config.abilityType ?? "MOCK") as AtomicAbilityKind;

    return (
      <div className="field-block">
        <span className="field-label">{t("dslEditor.atomicAbilityKind")}</span>
        <select
          className="text-input select-input"
          onChange={(event) => setAtomicAbilityKind(node.id, event.target.value as AtomicAbilityKind)}
          value={atomicKind}
        >
          {atomicAbilityKinds.map((kind) => (
            <option key={kind} value={kind}>
              {t(`dslEditor.atomicKind${kind[0]}${kind.slice(1).toLowerCase()}`)}
            </option>
          ))}
        </select>

        {atomicKind === "MOCK" ? (
          <label className="field-block">
            <span className="field-label">{t("dslEditor.abilityCode")}</span>
            <input
              className="text-input"
              onChange={(event) => updateNodeConfig(node.id, "abilityCode", event.target.value)}
              value={String(config.abilityCode ?? "")}
            />
          </label>
        ) : null}

        {atomicKind === "REST" ? (
          <div className="form-grid-two">
            <label className="field-block">
              <span className="field-label">{t("dslEditor.restUrl")}</span>
              <input
                className="text-input"
                onChange={(event) => updateNodeConfig(node.id, "url", event.target.value)}
                value={String(config.url ?? "")}
              />
            </label>
            <label className="field-block">
              <span className="field-label">{t("dslEditor.restMethod")}</span>
              <select
                className="text-input select-input"
                onChange={(event) => updateNodeConfig(node.id, "method", event.target.value)}
                value={String(config.method ?? "POST")}
              >
                <option value="GET">GET</option>
                <option value="POST">POST</option>
                <option value="PUT">PUT</option>
                <option value="DELETE">DELETE</option>
              </select>
            </label>
            <label className="toggle-row field-span-two">
              <input
                checked={Boolean(config.streaming)}
                onChange={(event) => updateNodeConfig(node.id, "streaming", event.target.checked)}
                type="checkbox"
              />
              <span>{t("dslEditor.restStreaming")}</span>
            </label>
            {renderHeaderEditor(node.id, config)}
            {renderAuthEditor(node.id, config)}
            <label className="field-block field-span-two">
              <span className="field-label">{t("dslEditor.restBody")}</span>
              <textarea
                className="text-area text-area-code text-area-medium"
                onChange={(event: ChangeEvent<HTMLTextAreaElement>) => updateJsonConfig(node.id, "body", event.target.value)}
                value={stringifyObject(config.body)}
              />
            </label>
          </div>
        ) : null}

        {atomicKind === "DATABASE" ? (
          <div className="form-grid-two">
            <label className="field-block field-span-two">
              <span className="field-label">{t("dslEditor.databaseJdbcUrl")}</span>
              <input
                className="text-input"
                onChange={(event) => updateNodeConfig(node.id, "jdbcUrl", event.target.value)}
                value={String(config.jdbcUrl ?? "")}
              />
            </label>
            <label className="field-block">
              <span className="field-label">{t("dslEditor.databaseUsername")}</span>
              <input
                className="text-input"
                onChange={(event) => updateNodeConfig(node.id, "username", event.target.value)}
                value={String(config.username ?? "")}
              />
            </label>
            <label className="field-block">
              <span className="field-label">{t("dslEditor.databasePassword")}</span>
              <input
                className="text-input"
                onChange={(event) => updateNodeConfig(node.id, "password", event.target.value)}
                value={String(config.password ?? "")}
              />
            </label>
            <label className="field-block">
              <span className="field-label">{t("dslEditor.databaseOperation")}</span>
              <select
                className="text-input select-input"
                onChange={(event) => updateNodeConfig(node.id, "operation", event.target.value)}
                value={String(config.operation ?? "QUERY")}
              >
                <option value="QUERY">QUERY</option>
                <option value="UPDATE">UPDATE</option>
              </select>
            </label>
            <label className="field-block field-span-two">
              <span className="field-label">{t("dslEditor.databaseSql")}</span>
              <textarea
                className="text-area text-area-code text-area-medium"
                onChange={(event) => updateNodeConfig(node.id, "sql", event.target.value)}
                value={String(config.sql ?? "")}
              />
            </label>
          </div>
        ) : null}

        {atomicKind === "REDIS" ? (
          <div className="form-grid-two">
            <label className="field-block">
              <span className="field-label">{t("dslEditor.redisHost")}</span>
              <input className="text-input" onChange={(event) => updateNodeConfig(node.id, "host", event.target.value)} value={String(config.host ?? "")} />
            </label>
            <label className="field-block">
              <span className="field-label">{t("dslEditor.redisPort")}</span>
              <input className="text-input" onChange={(event) => updateNodeConfig(node.id, "port", Number(event.target.value))} value={String(config.port ?? 6379)} />
            </label>
            <label className="field-block">
              <span className="field-label">{t("dslEditor.redisPassword")}</span>
              <input className="text-input" onChange={(event) => updateNodeConfig(node.id, "password", event.target.value)} value={String(config.password ?? "")} />
            </label>
            <label className="field-block">
              <span className="field-label">{t("dslEditor.redisOperation")}</span>
              <select className="text-input select-input" onChange={(event) => updateNodeConfig(node.id, "operation", event.target.value)} value={String(config.operation ?? "GET")}>
                <option value="GET">GET</option>
                <option value="SET">SET</option>
                <option value="PUBLISH">PUBLISH</option>
              </select>
            </label>
            <label className="field-block">
              <span className="field-label">{t("dslEditor.redisKey")}</span>
              <input className="text-input" onChange={(event) => updateNodeConfig(node.id, "key", event.target.value)} value={String(config.key ?? "")} />
            </label>
            <label className="field-block">
              <span className="field-label">{t("dslEditor.redisChannel")}</span>
              <input className="text-input" onChange={(event) => updateNodeConfig(node.id, "channel", event.target.value)} value={String(config.channel ?? "")} />
            </label>
            <label className="field-block field-span-two">
              <span className="field-label">{t("dslEditor.redisValue")}</span>
              <textarea className="text-area text-area-code text-area-small" onChange={(event) => updateNodeConfig(node.id, "value", event.target.value)} value={String(config.value ?? "")} />
            </label>
          </div>
        ) : null}

        {atomicKind === "ELASTICSEARCH" ? (
          <div className="form-grid-two">
            <label className="field-block field-span-two">
              <span className="field-label">{t("dslEditor.esUrl")}</span>
              <input className="text-input" onChange={(event) => updateNodeConfig(node.id, "url", event.target.value)} value={String(config.url ?? "")} />
            </label>
            <label className="field-block">
              <span className="field-label">{t("dslEditor.esMethod")}</span>
              <select className="text-input select-input" onChange={(event) => updateNodeConfig(node.id, "method", event.target.value)} value={String(config.method ?? "GET")}>
                <option value="GET">GET</option>
                <option value="POST">POST</option>
                <option value="PUT">PUT</option>
              </select>
            </label>
            <label className="field-block">
              <span className="field-label">{t("dslEditor.esUsername")}</span>
              <input className="text-input" onChange={(event) => updateNodeConfig(node.id, "username", event.target.value)} value={String(config.username ?? "")} />
            </label>
            <label className="field-block">
              <span className="field-label">{t("dslEditor.esPassword")}</span>
              <input className="text-input" onChange={(event) => updateNodeConfig(node.id, "password", event.target.value)} value={String(config.password ?? "")} />
            </label>
            <label className="field-block field-span-two">
              <span className="field-label">{t("dslEditor.esBody")}</span>
              <textarea
                className="text-area text-area-code text-area-medium"
                onChange={(event: ChangeEvent<HTMLTextAreaElement>) => updateJsonConfig(node.id, "body", event.target.value)}
                value={stringifyObject(config.body)}
              />
            </label>
          </div>
        ) : null}

        {atomicKind === "KAFKA" ? (
          <div className="form-grid-two">
            <label className="field-block field-span-two">
              <span className="field-label">{t("dslEditor.kafkaBootstrapServers")}</span>
              <input className="text-input" onChange={(event) => updateNodeConfig(node.id, "bootstrapServers", event.target.value)} value={String(config.bootstrapServers ?? "")} />
            </label>
            <label className="field-block">
              <span className="field-label">{t("dslEditor.kafkaTopic")}</span>
              <input className="text-input" onChange={(event) => updateNodeConfig(node.id, "topic", event.target.value)} value={String(config.topic ?? "")} />
            </label>
            <label className="field-block">
              <span className="field-label">{t("dslEditor.kafkaKey")}</span>
              <input className="text-input" onChange={(event) => updateNodeConfig(node.id, "key", event.target.value)} value={String(config.key ?? "")} />
            </label>
            <label className="field-block field-span-two">
              <span className="field-label">{t("dslEditor.kafkaValue")}</span>
              <textarea
                className="text-area text-area-code text-area-medium"
                onChange={(event: ChangeEvent<HTMLTextAreaElement>) => updateJsonConfig(node.id, "value", event.target.value)}
                value={stringifyObject(config.value)}
              />
            </label>
          </div>
        ) : null}
      </div>
    );
  }

  function renderDigitalEmployeeEditor(node: WorkflowDslNode, config: Record<string, unknown>) {
    return (
      <div className="form-grid-two">
        <label className="field-block field-span-two">
          <span className="field-label">{t("dslEditor.digitalEmployeeUrl")}</span>
          <input
            className="text-input"
            onChange={(event) => updateNodeConfig(node.id, "url", event.target.value)}
            value={String(config.url ?? "")}
          />
        </label>
        <label className="field-block">
          <span className="field-label">{t("dslEditor.digitalEmployeeMethod")}</span>
          <select
            className="text-input select-input"
            onChange={(event) => updateNodeConfig(node.id, "method", event.target.value)}
            value={String(config.method ?? "POST")}
          >
            <option value="POST">POST</option>
            <option value="GET">GET</option>
          </select>
        </label>
        <label className="field-block">
          <span className="field-label">{t("dslEditor.digitalEmployeeCode")}</span>
          <input
            className="text-input"
            onChange={(event) => updateNodeConfig(node.id, "employeeCode", event.target.value)}
            value={String(config.employeeCode ?? "")}
          />
        </label>
        {renderHeaderEditor(node.id, config)}
        {renderAuthEditor(node.id, config, true)}
        <label className="field-block field-span-two">
          <span className="field-label">{t("dslEditor.digitalEmployeeBody")}</span>
          <textarea
            className="text-area text-area-code text-area-medium"
            onChange={(event: ChangeEvent<HTMLTextAreaElement>) => updateJsonConfig(node.id, "body", event.target.value)}
            value={stringifyObject(config.body)}
          />
        </label>
      </div>
    );
  }

  return (
    <div className="structured-editor">
      <div className="structured-editor-panel">
        <div className="structured-editor-header">
          <div>
            <span className="field-label">{t("dslEditor.nodeTemplates")}</span>
            <p className="structured-editor-note">{t("dslEditor.nodeTemplatesHint")}</p>
          </div>
          <div className="preset-row">
            {nodeTemplateTypes.map((type) => (
              <button className="ghost-button compact" key={type} onClick={() => addNode(type)} type="button">
                {t(`dslEditor.nodeType.${type}`)}
              </button>
            ))}
          </div>
        </div>

        <div className="structured-editor-header">
          <div>
            <span className="field-label">{t("dslEditor.savedTemplates")}</span>
            <p className="structured-editor-note">{t("dslEditor.savedTemplatesHint")}</p>
          </div>
          {templates.loading ? <p className="structured-editor-note">{t("dslEditor.templateLoading")}</p> : null}
          <label className="field-block">
            <span className="field-label">{t("dslEditor.templateSearch")}</span>
            <input
              className="text-input"
              onChange={(event) => setTemplateFilter(event.target.value)}
              placeholder={t("dslEditor.templateSearchPlaceholder")}
              value={templateFilter}
            />
          </label>
          {!templates.loading && !filteredTemplates.length ? (
            <p className="structured-editor-note">{t("dslEditor.templateEmpty")}</p>
          ) : (
            <div className="preset-row">
              {filteredTemplates.slice(0, 16).map((template) => (
                <button className="ghost-button compact" key={template.id} onClick={() => addNodeFromTemplate(template)} type="button">
                  {template.name}
                </button>
              ))}
            </div>
          )}
          {templateMessage ? <p className="structured-editor-note">{templateMessage}</p> : null}
        </div>

        <div className="structured-card-stack">
          {normalizedDsl.nodes.map((node, index) => {
            const config = (node.config ?? {}) as Record<string, unknown>;

            return (
              <div className="structured-card" key={`${node.id}-${index}`}>
                <div className="structured-card-head">
                  <div>
                    <strong>{t("dslEditor.nodeLabel", { index: index + 1 })}</strong>
                    <span>{t(`dslEditor.nodeType.${node.type}`)}</span>
                  </div>
                  <div className="preset-row">
                    <button className="ghost-button compact" onClick={() => saveNodeAsTemplate(node)} type="button">
                      {t("dslEditor.saveAsTemplate")}
                    </button>
                    <button className="ghost-button compact" onClick={() => removeNode(node.id)} type="button">
                      {t("dslEditor.removeNode")}
                    </button>
                  </div>
                </div>

                <div className="form-grid-two">
                  <label className="field-block">
                    <span className="field-label">{t("dslEditor.nodeId")}</span>
                    <input className="text-input" onChange={(event) => updateNodeField(node.id, "id", event.target.value)} value={node.id} />
                  </label>
                  <label className="field-block">
                    <span className="field-label">{t("dslEditor.nodeName")}</span>
                    <input className="text-input" onChange={(event) => updateNodeField(node.id, "name", event.target.value)} value={node.name} />
                  </label>
                  <label className="field-block">
                    <span className="field-label">{t("dslEditor.nodeKind")}</span>
                    <select className="text-input select-input" onChange={(event) => updateNodeType(node.id, event.target.value as NodeTemplateType)} value={node.type}>
                      {nodeTemplateTypes.map((type) => (
                        <option key={type} value={type}>
                          {t(`dslEditor.nodeType.${type}`)}
                        </option>
                      ))}
                    </select>
                  </label>
                </div>

                {node.type === "ATOMIC_ABILITY" ? renderAtomicAbilityEditor(node, config) : null}
                {node.type === "DIGITAL_EMPLOYEE" ? renderDigitalEmployeeEditor(node, config) : null}

                {node.type === "WAIT_FOR_FEEDBACK" ? (
                  <label className="field-block">
                    <span className="field-label">{t("dslEditor.feedbackKey")}</span>
                    <input className="text-input" onChange={(event) => updateNodeConfig(node.id, "feedbackKey", event.target.value)} value={String(config.feedbackKey ?? "")} />
                  </label>
                ) : null}

                {node.type === "CONDITION" ? <p className="structured-editor-note">{t("dslEditor.conditionHint")}</p> : null}
              </div>
            );
          })}
        </div>
      </div>

      <div className="structured-editor-panel">
        <div className="structured-card">
          <div className="structured-card-head">
            <div>
              <strong>{t("dslEditor.validationTitle")}</strong>
              <span>{t("dslEditor.validationDescription")}</span>
            </div>
          </div>
          {!validationIssues.length ? (
            <p className="structured-editor-note structured-ok">{t("dslEditor.validationOk")}</p>
          ) : (
            <ul className="validation-list">
              {validationIssues.map((issue) => (
                <li key={issue}>{issue}</li>
              ))}
            </ul>
          )}
        </div>

        <div className="structured-editor-header">
          <div>
            <span className="field-label">{t("dslEditor.variableHints")}</span>
            <p className="structured-editor-note">{t("dslEditor.variableHintText")}</p>
          </div>
          {copiedToken ? <span className="structured-editor-note">{t("dslEditor.variableCopied")}: {copiedToken}</span> : null}
        </div>

        <div className="structured-card">
          <div className="node-chip-row">
            <span className="node-chip">{t("dslEditor.variableInput")}</span>
            <span className="node-chip">{t("dslEditor.variableContext")}</span>
            <span className="node-chip">{t("dslEditor.variableSteps")}</span>
            <span className="node-chip">{t("dslEditor.variableSystem")}</span>
          </div>
          {renderVariableButtons()}
        </div>

        <div className="structured-editor-header">
          <div>
            <span className="field-label">{t("dslEditor.edgeEditor")}</span>
            <p className="structured-editor-note">{t("dslEditor.edgeEditorHint")}</p>
          </div>
          <button className="ghost-button compact" onClick={addEdge} type="button">
            {t("dslEditor.addEdge")}
          </button>
        </div>

        <div className="structured-card-stack">
          {normalizedDsl.edges.map((edge, index) => (
            <div className="structured-card" key={`${edge.from}-${edge.to}-${index}`}>
              <div className="structured-card-head">
                <div>
                  <strong>{t("dslEditor.edgeLabel", { index: index + 1 })}</strong>
                </div>
                <button className="ghost-button compact" onClick={() => removeEdge(index)} type="button">
                  {t("dslEditor.removeEdge")}
                </button>
              </div>

              <div className="form-grid-three">
                <label className="field-block">
                  <span className="field-label">{t("dslEditor.edgeFrom")}</span>
                  <select className="text-input select-input" onChange={(event) => updateEdge(index, "from", event.target.value)} value={edge.from}>
                    {normalizedDsl.nodes.map((node) => (
                      <option key={node.id} value={node.id}>
                        {node.id}
                      </option>
                    ))}
                  </select>
                </label>
                <label className="field-block">
                  <span className="field-label">{t("dslEditor.edgeTo")}</span>
                  <select className="text-input select-input" onChange={(event) => updateEdge(index, "to", event.target.value)} value={edge.to}>
                    {normalizedDsl.nodes.map((node) => (
                      <option key={node.id} value={node.id}>
                        {node.id}
                      </option>
                    ))}
                  </select>
                </label>
                <label className="field-block field-span-three">
                  <span className="field-label">{t("dslEditor.edgeCondition")}</span>
                  <input
                    className="text-input"
                    onChange={(event) => updateEdge(index, "condition", event.target.value)}
                    placeholder={t("dslEditor.edgeConditionPlaceholder")}
                    value={edge.condition ?? ""}
                  />
                </label>
              </div>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
