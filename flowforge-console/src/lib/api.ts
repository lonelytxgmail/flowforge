import type {
  CreateWorkflowRequest,
  ExecutionEvent,
  FeedbackRequest,
  FeedbackRecord,
  NodeTemplate,
  NodeExecution,
  PublishWorkflowVersionRequest,
  SaveNodeTemplateRequest,
  StartWorkflowRequest,
  UpdateNodeTemplateRequest,
  WorkflowDefinition,
  WorkflowDsl,
  WorkflowInstance,
  WorkflowVersion,
} from "./types";

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8080";

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${API_BASE_URL}${path}`, {
    headers: {
      "Content-Type": "application/json",
      ...(init?.headers ?? {}),
    },
    ...init,
  });

  if (!response.ok) {
    const errorText = await response.text();
    throw new Error(errorText || `Request failed: ${response.status}`);
  }

  return (await response.json()) as T;
}

type InstanceIdResponse = {
  instanceId: number;
};

type WorkflowCommandResponse = {
  id: number;
};

type RuntimeCommandResponse = {
  instanceId: number;
  status: string;
};

export const api = {
  listWorkflows: () => request<WorkflowDefinition[]>("/api/workflows"),
  getWorkflow: (id: string) => request<WorkflowDefinition>(`/api/workflows/${id}`),
  listVersions: (id: string) => request<WorkflowVersion[]>(`/api/workflows/${id}/versions`),
  createWorkflow: (payload: CreateWorkflowRequest) =>
    request<WorkflowCommandResponse>("/api/workflows", {
      method: "POST",
      body: JSON.stringify(payload),
    }),
  publishVersion: (workflowId: string, payload: PublishWorkflowVersionRequest) =>
    request<WorkflowVersion>(`/api/workflows/${workflowId}/versions`, {
      method: "POST",
      body: JSON.stringify(payload),
    }),
  listNodeTemplates: () => request<NodeTemplate[]>("/api/workflows/node-templates"),
  getNodeTemplate: (id: string) => request<NodeTemplate>(`/api/workflows/node-templates/${id}`),
  saveNodeTemplate: (payload: SaveNodeTemplateRequest) =>
    request<NodeTemplate>("/api/workflows/node-templates", {
      method: "POST",
      body: JSON.stringify(payload),
    }),
  updateNodeTemplate: (id: string, payload: UpdateNodeTemplateRequest) =>
    request<NodeTemplate>(`/api/workflows/node-templates/${id}`, {
      method: "PUT",
      body: JSON.stringify(payload),
    }),
  deleteNodeTemplate: (id: string) =>
    request<{ deleted: boolean; id: number }>(`/api/workflows/node-templates/${id}`, {
      method: "DELETE",
    }),
  startWorkflow: (workflowId: string, payload?: StartWorkflowRequest) =>
    request<InstanceIdResponse>(`/api/workflows/${workflowId}/instances`, {
      method: "POST",
      body: JSON.stringify(payload ?? {}),
    }),
  listInstances: () => request<WorkflowInstance[]>("/api/instances"),
  getInstance: (id: string) => request<WorkflowInstance>(`/api/instances/${id}`),
  getNodeExecutions: (id: string) => request<NodeExecution[]>(`/api/instances/${id}/node-executions`),
  getEvents: (id: string) => request<ExecutionEvent[]>(`/api/instances/${id}/events`),
  getFeedbackRecords: (id: string) => request<FeedbackRecord[]>(`/api/instances/${id}/feedback-records`),
  pauseInstance: (id: string) =>
    request<RuntimeCommandResponse>(`/api/instances/${id}/pause`, {
      method: "POST",
    }),
  resumeInstance: (id: string) =>
    request<RuntimeCommandResponse>(`/api/instances/${id}/resume`, {
      method: "POST",
    }),
  retryInstance: (id: string) =>
    request<RuntimeCommandResponse>(`/api/instances/${id}/retry`, {
      method: "POST",
    }),
  submitFeedback: (id: string, payload: FeedbackRequest) =>
    request<RuntimeCommandResponse>(`/api/instances/${id}/feedback`, {
      method: "POST",
      body: JSON.stringify(payload),
    }),
};

export const starterDslExamples: Record<string, WorkflowDsl> = {
  minimal: {
    version: "1.0",
    nodes: [
      { id: "start_1", name: "Start", type: "START", config: {} },
      {
        id: "ability_1",
        name: "Mock Atomic Ability",
        type: "ATOMIC_ABILITY",
        config: {
          abilityCode: "mock.echo",
        },
      },
      { id: "end_1", name: "End", type: "END", config: {} },
    ],
    edges: [
      { from: "start_1", to: "ability_1" },
      { from: "ability_1", to: "end_1" },
    ],
  },
  waitForFeedback: {
    version: "1.0",
    nodes: [
      { id: "start_1", name: "Start", type: "START", config: {} },
      {
        id: "review_1",
        name: "Manual Review",
        type: "WAIT_FOR_FEEDBACK",
        config: {},
      },
      { id: "end_1", name: "End", type: "END", config: {} },
    ],
    edges: [
      { from: "start_1", to: "review_1" },
      { from: "review_1", to: "end_1" },
    ],
  },
};
