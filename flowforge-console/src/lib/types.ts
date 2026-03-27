export type WorkflowDslNode = {
  id: string;
  name: string;
  type: string;
  config?: Record<string, unknown>;
  [key: string]: unknown;
};

export type WorkflowDslEdge = {
  from: string;
  to: string;
  condition?: string | null;
  [key: string]: unknown;
};

export type WorkflowDsl = {
  version: string;
  nodes: WorkflowDslNode[];
  edges: WorkflowDslEdge[];
  [key: string]: unknown;
};

export type WorkflowDefinition = {
  id: number;
  code: string;
  name: string;
  description?: string | null;
  status: string;
  createdAt: string;
  updatedAt: string;
};

export type WorkflowVersion = {
  id: number;
  workflowDefinitionId: number;
  versionNo: number;
  status: string;
  dsl: WorkflowDsl;
  publishedAt?: string | null;
  createdAt: string;
};

export type WorkflowInstance = {
  id: number;
  workflowDefinitionId: number;
  workflowVersionId: number;
  status: string;
  inputPayload?: string | null;
  contextJson?: string | null;
  currentNodeId?: string | null;
  startedAt: string;
  endedAt?: string | null;
  createdAt: string;
};

export type NodeExecution = {
  id: number;
  workflowInstanceId: number;
  nodeId: string;
  nodeName: string;
  nodeType: string;
  status: string;
  attemptNo: number;
  inputJson?: string | null;
  outputJson?: string | null;
  errorMessage?: string | null;
  startedAt: string;
  endedAt?: string | null;
};

export type ExecutionEvent = {
  id: number;
  workflowInstanceId: number;
  nodeExecutionId?: number | null;
  eventType: string;
  eventMessage: string;
  eventDetail?: string | null;
  createdAt: string;
};

export type WorkflowTask = {
  id: number;
  workflowInstanceId: number;
  nodeId: string;
  status: string;
  attemptNo: number;
  sourceTaskId?: number | null;
  inputJson?: string | null;
  retryReason?: string | null;
  availableAt: string;
  lockedAt?: string | null;
  lockOwner?: string | null;
  errorMessage?: string | null;
  maxAttempts: number;
  timeoutSeconds?: number | null;
  retryBackoffSeconds?: number | null;
  createdAt: string;
  updatedAt: string;
};

export type FeedbackRecord = {
  id: number;
  workflowInstanceId: number;
  nodeExecutionId?: number | null;
  feedbackType: string;
  feedbackPayload?: string | null;
  createdBy?: string | null;
  createdAt: string;
};

export type CreateWorkflowRequest = {
  code: string;
  name: string;
  description?: string;
};

export type PublishWorkflowVersionRequest = {
  dsl: WorkflowDsl;
};

export type StartWorkflowRequest = {
  inputPayload?: Record<string, unknown>;
};

export type FeedbackRequest = {
  feedbackType?: string;
  feedbackPayload?: Record<string, unknown>;
  createdBy?: string;
};

export type RetryTaskRequest = {
  reason?: string;
};

export type NodeTemplate = {
  id: number;
  code: string;
  name: string;
  description?: string | null;
  groupName?: string | null;
  nodeType: string;
  nodeConfig: Record<string, unknown>;
  createdAt: string;
  updatedAt: string;
};

export type SaveNodeTemplateRequest = {
  code: string;
  name: string;
  description?: string;
  groupName?: string;
  nodeType: string;
  nodeConfig?: Record<string, unknown>;
};

export type UpdateNodeTemplateRequest = SaveNodeTemplateRequest;
