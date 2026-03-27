import { FormEvent, useState } from "react";
import { useParams } from "react-router-dom";
import { api } from "../lib/api";
import { formatDateTime, formatJson, tryParseJson } from "../lib/format";
import { useAsyncData } from "../lib/hooks";
import { useI18n } from "../lib/i18n";
import { EmptyState } from "../components/EmptyState";
import { Section } from "../components/Section";
import { StatePanel } from "../components/StatePanel";
import { StatusPill } from "../components/StatusPill";

export function InstanceDetailPage() {
  const { t } = useI18n();
  const { instanceId = "1" } = useParams();
  const instance = useAsyncData(() => api.getInstance(instanceId), [instanceId]);
  const nodeExecutions = useAsyncData(() => api.getNodeExecutions(instanceId), [instanceId]);
  const events = useAsyncData(() => api.getEvents(instanceId), [instanceId]);
  const tasks = useAsyncData(() => api.getTasks(instanceId), [instanceId]);
  const feedbackRecords = useAsyncData(() => api.getFeedbackRecords(instanceId), [instanceId]);
  const [feedbackPayloadText, setFeedbackPayloadText] = useState(JSON.stringify({ approved: true }, null, 2));
  const [feedbackCreatedBy, setFeedbackCreatedBy] = useState("console-user");
  const [commandState, setCommandState] = useState<{
    loading: boolean;
    error: string | null;
    success: string | null;
  }>({
    loading: false,
    error: null,
    success: null,
  });

  async function runCommand(action: () => Promise<unknown>, successMessage: string) {
    setCommandState({
      loading: true,
      error: null,
      success: null,
    });

    try {
      await action();
      setCommandState({
        loading: false,
        error: null,
        success: successMessage,
      });
      instance.reload();
      nodeExecutions.reload();
      tasks.reload();
      events.reload();
      feedbackRecords.reload();
    } catch (error) {
      setCommandState({
        loading: false,
        error: error instanceof Error ? error.message : "Command failed.",
        success: null,
      });
    }
  }

  async function handleFeedback(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const parsedFeedback = tryParseJson<Record<string, unknown>>(feedbackPayloadText);

    if (!parsedFeedback.data) {
      setCommandState({
        loading: false,
        error: `${t("instanceDetail.invalidFeedbackJson")} ${parsedFeedback.error}`,
        success: null,
      });
      return;
    }

    await runCommand(
      () =>
        api.submitFeedback(instanceId, {
          createdBy: feedbackCreatedBy || undefined,
          feedbackPayload: parsedFeedback.data ?? undefined,
        }),
      t("instanceDetail.feedbackSuccess")
    );
  }

  const isWaitingForFeedback = instance.data?.status === "PAUSED" || instance.data?.status === "WAITING";
  const retryableTaskIds = new Set((tasks.data ?? []).filter((task) => task.status === "FAILED").map((task) => task.id));

  async function retryTask(taskId: number) {
    await runCommand(
      () => api.retryTask(instanceId, String(taskId), { reason: "manual_task_retry" }),
      t("instanceDetail.taskRetriedSuccess", { id: taskId })
    );
  }

  function renderAbilityOutput(jsonString: string | null | undefined) {
    if (!jsonString) return null;
    const parsed = tryParseJson<Record<string, unknown>>(jsonString);
    if (!parsed.data || !parsed.data.abilityType) {
      return <pre>{formatJson(jsonString)}</pre>;
    }
    const data = parsed.data;
    const abilityType = data.abilityType;

    const renderHeader = (title: string, meta?: string | number) => (
      <div style={{ display: 'flex', justifyContent: 'space-between', marginBottom: '8px', borderBottom: '1px solid var(--border-color)', paddingBottom: '4px' }}>
        <strong>{title}</strong>
        {meta && <span className="timeline-meta">{meta}</span>}
      </div>
    );

    if (abilityType === "REST") {
      return (
        <div style={{ background: 'var(--bg-subtle)', padding: '12px', borderRadius: '6px', marginTop: '8px' }}>
          {renderHeader("REST Response", `Status: ${String(data.statusCode)}`)}
          {Boolean(data.streaming) && <span className="timeline-meta" style={{display: 'block', marginBottom: '8px'}}>Streaming Response</span>}
          <pre style={{ margin: 0, background: 'transparent', padding: 0 }}>{formatJson(data.chunks ?? data.body ?? data)}</pre>
        </div>
      );
    }

    if (abilityType === "DATABASE") {
      const operation = data.operation as string;
      if (operation === "QUERY" && Array.isArray(data.rows)) {
        const rows = data.rows as Record<string, unknown>[];
        if (rows.length === 0) {
          return <div style={{ background: 'var(--bg-subtle)', padding: '12px', borderRadius: '6px', marginTop: '8px' }}><p>0 rows returned.</p></div>;
        }
        const columns = Object.keys(rows[0]);
        return (
          <div style={{ background: 'var(--bg-subtle)', padding: '12px', borderRadius: '6px', marginTop: '8px', overflowX: 'auto' }}>
            {renderHeader(`DATABASE ${operation}`, `${rows.length} rows`)}
            <table style={{ width: '100%', textAlign: 'left', borderCollapse: 'collapse', fontSize: '13px' }}>
              <thead><tr>{columns.map(c => <th key={c} style={{ borderBottom: '1px solid var(--border-color)', padding: '4px' }}>{c}</th>)}</tr></thead>
              <tbody>
                {rows.slice(0, 5).map((row, i) => (
                  <tr key={i}>{columns.map(c => <td key={c} style={{ padding: '4px', borderBottom: '1px solid var(--border-color)' }}>{String(row[c] ?? "")}</td>)}</tr>
                ))}
                {rows.length > 5 && <tr><td colSpan={columns.length} style={{ padding: '4px', textAlign: 'center' }}>... ({rows.length - 5} more rows)</td></tr>}
              </tbody>
            </table>
          </div>
        );
      } else {
        return (
          <div style={{ background: 'var(--bg-subtle)', padding: '12px', borderRadius: '6px', marginTop: '8px' }}>
            {renderHeader(`DATABASE ${operation}`, data.updatedRows != null ? `${data.updatedRows} rows updated` : undefined)}
            <pre style={{ margin: 0, background: 'transparent', padding: 0 }}>{formatJson(data)}</pre>
          </div>
        );
      }
    }

    if (abilityType === "REDIS") {
      return (
        <div style={{ background: 'var(--bg-subtle)', padding: '12px', borderRadius: '6px', marginTop: '8px' }}>
          {renderHeader(`REDIS ${data.operation}`)}
          <pre style={{ margin: 0, background: 'transparent', padding: 0 }}>{formatJson(data.value ?? data.result ?? data.receivers ?? data)}</pre>
        </div>
      );
    }

    if (abilityType === "ELASTICSEARCH") {
      const bodyStr = typeof data.body === 'string' ? data.body : JSON.stringify(data.body);
      const bodyParsed = tryParseJson<any>(bodyStr);
      const isSearch = bodyParsed.data && bodyParsed.data.hits && bodyParsed.data.hits.hits;
      const total = bodyParsed.data && bodyParsed.data.hits && bodyParsed.data.hits.total ? bodyParsed.data.hits.total.value : 0;
      return (
        <div style={{ background: 'var(--bg-subtle)', padding: '12px', borderRadius: '6px', marginTop: '8px' }}>
          {renderHeader("ELASTICSEARCH", `Status: ${String(data.statusCode)}` + (isSearch ? ` | Hits: ${total}` : ''))}
          <pre style={{ margin: 0, background: 'transparent', padding: 0 }}>{formatJson(isSearch ? bodyParsed.data.hits.hits : (bodyParsed.data ?? data.body))}</pre>
        </div>
      );
    }

    if (abilityType === "KAFKA") {
      return (
        <div style={{ background: 'var(--bg-subtle)', padding: '12px', borderRadius: '6px', marginTop: '8px' }}>
          {renderHeader("KAFKA Publish", `Topic: ${data.topic}`)}
          <span style={{ fontSize: '13px' }}>Partition: {data.partition as number} | Offset: {data.offset as number}</span>
        </div>
      );
    }

    return <pre>{formatJson(jsonString)}</pre>;
  }

  return (
    <div className="page-stack">
      <Section
        title={t("instanceDetail.title", { id: instanceId })}
        description={t("instanceDetail.description")}
        aside={instance.data ? <StatusPill value={instance.data.status} /> : null}
      >
        {instance.loading ? <StatePanel detail={t("instanceDetail.loadingDetail")} title={t("instanceDetail.loading")} tone="loading" /> : null}
        {instance.error ? <StatePanel detail={instance.error} title={t("instanceDetail.errorTitle")} tone="error" /> : null}
        {instance.data ? (
          <div className="definition-grid">
            <div>
              <span className="data-label">{t("instanceDetail.currentNode")}</span>
              <strong>{instance.data.currentNodeId ?? "—"}</strong>
            </div>
            <div>
              <span className="data-label">{t("instanceDetail.started")}</span>
              <strong>{formatDateTime(instance.data.startedAt)}</strong>
            </div>
            <div>
              <span className="data-label">{t("instanceDetail.version")}</span>
              <strong>#{instance.data.workflowVersionId}</strong>
            </div>
            <div className="wide">
              <span className="data-label">{t("instanceDetail.context")}</span>
              <pre>{formatJson(instance.data.contextJson)}</pre>
            </div>
          </div>
        ) : null}
      </Section>

      <Section title={t("instanceDetail.runtimeTitle")} description={t("instanceDetail.runtimeDescription")}>
        {isWaitingForFeedback ? (
          <div className="hint-banner">
            <strong>{t("instanceDetail.waitingHintTitle")}</strong>
            <span>{t("instanceDetail.waitingHintDetail")}</span>
          </div>
        ) : (
          <div className="hint-banner subtle">
            <strong>{t("instanceDetail.runtimeTitle")}</strong>
            <span>{t("instanceDetail.submitFeedbackOnlyWhenWaiting")}</span>
          </div>
        )}
        <div className="control-cluster">
          <button
            className="secondary-button"
            disabled={commandState.loading}
            onClick={() => runCommand(() => api.pauseInstance(instanceId), t("instanceDetail.pausedSuccess"))}
            type="button"
          >
            {t("instanceDetail.pause")}
          </button>
          <button
            className="secondary-button"
            disabled={commandState.loading}
            onClick={() => runCommand(() => api.resumeInstance(instanceId), t("instanceDetail.resumedSuccess"))}
            type="button"
          >
            {t("instanceDetail.resume")}
          </button>
          <button
            className="secondary-button"
            disabled={commandState.loading}
            onClick={() => runCommand(() => api.retryInstance(instanceId), t("instanceDetail.retriedSuccess"))}
            type="button"
          >
            {t("instanceDetail.retry")}
          </button>
          {commandState.success ? <span className="inline-message success">{commandState.success}</span> : null}
          {commandState.error ? <span className="inline-message error">{commandState.error}</span> : null}
        </div>

        <form className="inline-form" onSubmit={handleFeedback}>
          <div className="grid-two">
            <label className="field-block">
              <span className="field-label">{t("instanceDetail.feedbackBy")}</span>
              <input
                className="text-input"
                onChange={(event) => setFeedbackCreatedBy(event.target.value)}
                value={feedbackCreatedBy}
              />
            </label>

            <label className="field-block">
              <span className="field-label">{t("instanceDetail.feedbackPayload")}</span>
              <textarea
                className="text-area text-area-code text-area-medium"
                onChange={(event) => setFeedbackPayloadText(event.target.value)}
                value={feedbackPayloadText}
              />
            </label>
          </div>

          <div className="editor-actions compact">
            <button className="primary-button" disabled={commandState.loading} type="submit">
              {commandState.loading ? t("instanceDetail.submitting") : t("instanceDetail.submitFeedback")}
            </button>
          </div>
        </form>
      </Section>

      <div className="grid-two">
        <Section title={t("instanceDetail.tasksTitle")} description={t("instanceDetail.tasksDescription")}>
          {tasks.loading ? (
            <StatePanel detail={t("instanceDetail.loadingTasksDetail")} title={t("instanceDetail.loadingTasks")} tone="loading" />
          ) : tasks.error ? (
            <StatePanel detail={tasks.error} title={t("instanceDetail.tasksErrorTitle")} tone="error" />
          ) : !tasks.data?.length ? (
            <EmptyState title={t("instanceDetail.noTasksTitle")} detail={t("instanceDetail.noTasksDetail")} />
          ) : (
            <div className="timeline">
              {tasks.data.map((task) => (
                <div className="timeline-item" key={task.id}>
                  <div className="timeline-top">
                    <strong>{t("instanceDetail.taskLabel", { id: task.id })}</strong>
                    <StatusPill value={task.status} />
                  </div>
                  <span className="timeline-meta">
                    {task.nodeId} · {t("instanceDetail.taskAttempt", { current: task.attemptNo, max: task.maxAttempts })}
                  </span>
                  <pre>{formatJson(task.inputJson)}</pre>
                  {task.retryReason ? <span>{t("instanceDetail.taskRetryReason", { reason: task.retryReason })}</span> : null}
                  {task.sourceTaskId ? <span>{t("instanceDetail.taskSource", { id: task.sourceTaskId })}</span> : null}
                  {task.lockOwner ? <span>{t("instanceDetail.taskLockOwner", { owner: task.lockOwner })}</span> : null}
                  {task.errorMessage ? <span className="inline-message error">{task.errorMessage}</span> : null}
                  {retryableTaskIds.has(task.id) ? (
                    <div className="editor-actions compact">
                      <button className="secondary-button" disabled={commandState.loading} onClick={() => retryTask(task.id)} type="button">
                        {t("instanceDetail.retryTask")}
                      </button>
                    </div>
                  ) : null}
                </div>
              ))}
            </div>
          )}
        </Section>

        <Section title={t("instanceDetail.nodeExecutionsTitle")} description={t("instanceDetail.nodeExecutionsDescription")}>
          {nodeExecutions.loading ? (
            <StatePanel detail={t("instanceDetail.loadingNodeExecutionsDetail")} title={t("instanceDetail.loadingNodeExecutions")} tone="loading" />
          ) : nodeExecutions.error ? (
            <StatePanel detail={nodeExecutions.error} title={t("instanceDetail.nodeExecutionsErrorTitle")} tone="error" />
          ) : !nodeExecutions.data?.length ? (
            <EmptyState title={t("instanceDetail.noNodeExecutionsTitle")} detail={t("instanceDetail.noNodeExecutionsDetail")} />
          ) : (
            <div className="timeline">
              {nodeExecutions.data.map((item) => (
                <div className="timeline-item" key={item.id}>
                  <div className="timeline-top">
                    <strong>{item.nodeName}</strong>
                    <StatusPill value={item.status} />
                  </div>
                  <span className="timeline-meta">{item.nodeType} · {formatDateTime(item.startedAt)}</span>
                  {item.errorMessage ? <span className="inline-message error">{item.errorMessage}</span> : null}
                  {renderAbilityOutput(item.outputJson ?? item.inputJson)}
                </div>
              ))}
            </div>
          )}
        </Section>

        <Section title={t("instanceDetail.eventsTitle")} description={t("instanceDetail.eventsDescription")}>
          {events.loading ? (
            <StatePanel detail={t("instanceDetail.loadingEventsDetail")} title={t("instanceDetail.loadingEvents")} tone="loading" />
          ) : events.error ? (
            <StatePanel detail={events.error} title={t("instanceDetail.eventsErrorTitle")} tone="error" />
          ) : (
            <div className="event-stream">
              {(events.data ?? []).length === 0 ? (
                <EmptyState title={t("instanceDetail.noEventsTitle")} detail={t("instanceDetail.noEventsDetail")} />
              ) : (
                (events.data ?? []).map((event) => (
                  <div className="event-row" key={event.id}>
                    <div>
                      <strong>{event.eventType}</strong>
                      <span>{event.eventMessage}</span>
                      {event.eventDetail ? <pre>{formatJson(event.eventDetail)}</pre> : null}
                    </div>
                    <span>{formatDateTime(event.createdAt)}</span>
                  </div>
                ))
              )}
            </div>
          )}

          <div className="separator" />

          {feedbackRecords.loading ? (
            <StatePanel detail={t("instanceDetail.loadingFeedbackDetail")} title={t("instanceDetail.loadingFeedback")} tone="loading" />
          ) : feedbackRecords.error ? (
            <StatePanel detail={feedbackRecords.error} title={t("instanceDetail.feedbackErrorTitle")} tone="error" />
          ) : !feedbackRecords.data?.length ? (
            <EmptyState title={t("instanceDetail.noFeedbackTitle")} detail={t("instanceDetail.noFeedbackDetail")} />
          ) : (
            <div className="event-stream">
              {feedbackRecords.data.map((record) => (
                <div className="event-row" key={record.id}>
                  <div>
                    <strong>{record.feedbackType}</strong>
                    <span>{record.createdBy ?? t("instanceDetail.system")}</span>
                    <pre>{formatJson(record.feedbackPayload)}</pre>
                  </div>
                  <span>{formatDateTime(record.createdAt)}</span>
                </div>
              ))}
            </div>
          )}
        </Section>
      </div>
    </div>
  );
}
