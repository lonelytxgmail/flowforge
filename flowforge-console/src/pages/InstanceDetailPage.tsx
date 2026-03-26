import { FormEvent, useState } from "react";
import { useParams } from "react-router-dom";
import { api } from "../lib/api";
import { formatDateTime, formatJson, tryParseJson } from "../lib/format";
import { useAsyncData } from "../lib/hooks";
import { useI18n } from "../lib/i18n";
import { EmptyState } from "../components/EmptyState";
import { Section } from "../components/Section";
import { StatusPill } from "../components/StatusPill";

export function InstanceDetailPage() {
  const { t } = useI18n();
  const { instanceId = "1" } = useParams();
  const instance = useAsyncData(() => api.getInstance(instanceId), [instanceId]);
  const nodeExecutions = useAsyncData(() => api.getNodeExecutions(instanceId), [instanceId]);
  const events = useAsyncData(() => api.getEvents(instanceId), [instanceId]);
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

  return (
    <div className="page-stack">
      <Section
        title={t("instanceDetail.title", { id: instanceId })}
        description={t("instanceDetail.description")}
        aside={instance.data ? <StatusPill value={instance.data.status} /> : null}
      >
        {instance.loading ? <p>{t("instanceDetail.loading")}</p> : null}
        {instance.error ? <p>{instance.error}</p> : null}
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
        <Section title={t("instanceDetail.nodeExecutionsTitle")} description={t("instanceDetail.nodeExecutionsDescription")}>
          {nodeExecutions.loading ? (
            <p>{t("instanceDetail.loadingNodeExecutions")}</p>
          ) : nodeExecutions.error ? (
            <p>{nodeExecutions.error}</p>
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
                  <pre>{formatJson(item.outputJson ?? item.inputJson)}</pre>
                </div>
              ))}
            </div>
          )}
        </Section>

        <Section title={t("instanceDetail.eventsTitle")} description={t("instanceDetail.eventsDescription")}>
          {events.loading ? (
            <p>{t("instanceDetail.loadingEvents")}</p>
          ) : events.error ? (
            <p>{events.error}</p>
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
            <p>{t("instanceDetail.loadingFeedback")}</p>
          ) : feedbackRecords.error ? (
            <p>{feedbackRecords.error}</p>
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
