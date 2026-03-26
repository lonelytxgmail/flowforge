import { FormEvent, useEffect, useState } from "react";
import { useParams } from "react-router-dom";
import { DslStructuredEditor } from "../components/DslStructuredEditor";
import { api, starterDslExamples } from "../lib/api";
import { validateWorkflowDsl } from "../lib/dsl";
import { formatDateTime, formatJson, prettyJson, tryParseJson } from "../lib/format";
import { useAsyncData } from "../lib/hooks";
import { useI18n } from "../lib/i18n";
import { EmptyState } from "../components/EmptyState";
import { Section } from "../components/Section";
import { StatePanel } from "../components/StatePanel";
import { StatusPill } from "../components/StatusPill";
import type { WorkflowDsl } from "../lib/types";

export function WorkflowDetailPage() {
  const { t } = useI18n();
  const { workflowId = "1" } = useParams();
  const workflow = useAsyncData(() => api.getWorkflow(workflowId), [workflowId]);
  const versions = useAsyncData(() => api.listVersions(workflowId), [workflowId]);
  const [dslText, setDslText] = useState(JSON.stringify(starterDslExamples.minimal, null, 2));
  const [inputPayloadText, setInputPayloadText] = useState(JSON.stringify({ trigger: "console" }, null, 2));
  const [publishMessage, setPublishMessage] = useState<string | null>(null);
  const [launchMessage, setLaunchMessage] = useState<string | null>(null);
  const [publishError, setPublishError] = useState<string | null>(null);
  const [launchError, setLaunchError] = useState<string | null>(null);
  const [publishing, setPublishing] = useState(false);
  const [starting, setStarting] = useState(false);
  const [dslCheckMessage, setDslCheckMessage] = useState<string | null>(null);
  const [dslCheckError, setDslCheckError] = useState<string | null>(null);
  const parsedDsl = tryParseJson<WorkflowDsl>(dslText);

  useEffect(() => {
    if (!versions.data?.length) {
      return;
    }

    const latestVersion = versions.data[0];
    setDslText(prettyJson(latestVersion.dsl));
  }, [versions.data]);

  function loadDslTemplate(template: "minimal" | "waitForFeedback") {
    setDslText(prettyJson(starterDslExamples[template]));
    setDslCheckMessage(null);
    setDslCheckError(null);
  }

  function formatDslText() {
    const parsed = tryParseJson<WorkflowDsl>(dslText);

    if (parsed.data) {
      setDslText(prettyJson(parsed.data));
      setDslCheckMessage(t("workflowDetail.validDsl"));
      setDslCheckError(null);
      return;
    }

    setDslCheckMessage(null);
    setDslCheckError(`${t("workflowDetail.invalidDsl")} ${parsed.error}`);
  }

  function validateDslText() {
    const parsed = tryParseJson<WorkflowDsl>(dslText);

    if (parsed.data) {
      const issues = validateWorkflowDsl(parsed.data).filter((issue) => issue.level === "error");
      if (issues.length === 0) {
        setDslCheckMessage(t("workflowDetail.validDsl"));
        setDslCheckError(null);
        return;
      }
      setDslCheckMessage(null);
      setDslCheckError(issues.map((issue) => issue.message).join("；"));
      return;
    }

    setDslCheckMessage(null);
    setDslCheckError(`${t("workflowDetail.invalidDsl")} ${parsed.error}`);
  }

  async function handlePublish(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setPublishing(true);
    setPublishMessage(null);
    setPublishError(null);

    try {
      const parsedDsl = tryParseJson<WorkflowDsl>(dslText);
      if (!parsedDsl.data) {
        throw new Error(`${t("workflowDetail.invalidDsl")} ${parsedDsl.error}`);
      }
      const issues = validateWorkflowDsl(parsedDsl.data).filter((issue) => issue.level === "error");
      if (issues.length > 0) {
        throw new Error(issues.map((issue) => issue.message).join("；"));
      }

      await api.publishVersion(workflowId, {
        dsl: parsedDsl.data,
      });
      setPublishMessage(t("workflowDetail.publishSuccess"));
      versions.reload();
    } catch (error) {
      setPublishError(error instanceof Error ? error.message : t("workflowDetail.publishFailed"));
    } finally {
      setPublishing(false);
    }
  }

  async function handleStart(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setStarting(true);
    setLaunchMessage(null);
    setLaunchError(null);

    try {
      if (!inputPayloadText.trim()) {
        throw new Error(t("workflowDetail.inputPayloadRequired"));
      }

      const parsedPayload = tryParseJson<Record<string, unknown>>(inputPayloadText);
      if (!parsedPayload.data) {
        throw new Error(`${t("common.invalidJson")} ${parsedPayload.error}`);
      }

      const payload = parsedPayload.data;
      const response = await api.startWorkflow(workflowId, { inputPayload: payload });
      setLaunchMessage(t("workflowDetail.launchSuccess", { id: response.instanceId }));
      workflow.reload();
    } catch (error) {
      setLaunchError(error instanceof Error ? error.message : t("workflowDetail.launchFailed"));
    } finally {
      setStarting(false);
    }
  }

  return (
    <div className="page-stack">
      <Section
        title={workflow.data?.name ?? t("workflowDetail.title")}
        description={workflow.data?.description ?? t("workflowDetail.descriptionFallback")}
        aside={workflow.data ? <StatusPill value={workflow.data.status} /> : null}
      >
        {workflow.loading ? <StatePanel detail={t("workflowDetail.loadingDetail")} title={t("workflowDetail.loading")} tone="loading" /> : null}
        {workflow.error ? <StatePanel detail={workflow.error} title={t("workflowDetail.errorTitle")} tone="error" /> : null}
        {workflow.data ? (
          <div className="definition-grid">
            <div>
              <span className="data-label">{t("workflowDetail.code")}</span>
              <strong>{workflow.data.code}</strong>
            </div>
            <div>
              <span className="data-label">{t("workflowDetail.updated")}</span>
              <strong>{formatDateTime(workflow.data.updatedAt)}</strong>
            </div>
            <div className="wide">
              <span className="data-label">{t("workflowDetail.description")}</span>
              <strong>{workflow.data.description ?? t("workflowDetail.noDescription")}</strong>
            </div>
          </div>
        ) : null}
      </Section>

      <div className="grid-two grid-two-asymmetric">
        <Section title={t("workflowDetail.launchTitle")} description={t("workflowDetail.launchDescription")}>
          <div className="hint-banner subtle">
            <strong>{t("workflowDetail.launchHintTitle")}</strong>
            <span>{t("workflowDetail.launchHintDetail")}</span>
          </div>
          <form className="inline-form" onSubmit={handleStart}>
            <label className="field-block">
              <span className="field-label">{t("workflowDetail.inputPayload")}</span>
              <textarea
                className="text-area text-area-code text-area-medium"
                onChange={(event) => setInputPayloadText(event.target.value)}
                value={inputPayloadText}
              />
            </label>
            <div className="editor-actions compact">
              <button className="primary-button" disabled={starting} type="submit">
                {starting ? t("workflowDetail.starting") : t("workflowDetail.start")}
              </button>
              {launchMessage ? <span className="inline-message success">{launchMessage}</span> : null}
              {launchError ? <span className="inline-message error">{launchError}</span> : null}
            </div>
          </form>
        </Section>

        <Section title={t("workflowDetail.currentShapeTitle")} description={t("workflowDetail.currentShapeDescription")}>
          {!versions.data?.length ? (
            <EmptyState title={t("workflowDetail.noPublishedVersionTitle")} detail={t("workflowDetail.noPublishedVersionDetail")} />
          ) : (
            <div className="section-content">
              <div className="node-chip-row">
                {versions.data[0].dsl.nodes.map((node) => (
                  <span className="node-chip" key={node.id}>
                    {node.type} · {node.name}
                  </span>
                ))}
              </div>
              <pre>{formatJson(versions.data[0].dsl)}</pre>
            </div>
          )}
        </Section>
      </div>

      <Section title={t("workflowDetail.versionLedgerTitle")} description={t("workflowDetail.versionLedgerDescription")}>
        {versions.loading ? (
          <StatePanel detail={t("workflowDetail.loadingVersionsDetail")} title={t("workflowDetail.loadingVersions")} tone="loading" />
        ) : versions.error ? (
          <StatePanel detail={versions.error} title={t("workflowDetail.versionErrorTitle")} tone="error" />
        ) : !versions.data?.length ? (
          <EmptyState title={t("workflowDetail.noVersionsTitle")} detail={t("workflowDetail.noVersionsDetail")} />
        ) : (
          <div className="version-stack">
            {versions.data.map((version) => (
              <div className="version-entry" key={version.id}>
                <div className="version-head">
                  <div>
                    <strong>v{version.versionNo}</strong>
                    <span>{formatDateTime(version.publishedAt ?? version.createdAt)}</span>
                  </div>
                  <StatusPill value={version.status} />
                </div>
                <div className="node-chip-row">
                  {version.dsl.nodes.map((node) => (
                    <span className="node-chip" key={node.id}>
                      {node.type} · {node.name}
                    </span>
                  ))}
                </div>
              </div>
            ))}
          </div>
        )}
      </Section>

      {parsedDsl.data ? (
        <Section title={t("workflowDetail.structuredTitle")} description={t("workflowDetail.structuredDescription")}>
          <DslStructuredEditor
            dsl={parsedDsl.data}
            onChange={(nextDsl) => {
              setDslText(prettyJson(nextDsl));
              setDslCheckMessage(null);
              setDslCheckError(null);
            }}
          />
        </Section>
      ) : null}

      <Section title={t("workflowDetail.publishTitle")} description={t("workflowDetail.publishDescription")}>
        <form className="inline-form" onSubmit={handlePublish}>
          <label className="field-block">
            <span className="field-label">{t("workflowDetail.dslJson")}</span>
            <div className="editor-toolbar">
              <button className="ghost-button compact" onClick={formatDslText} type="button">
                {t("workflowDetail.formatDsl")}
              </button>
              <button className="ghost-button compact" onClick={validateDslText} type="button">
                {t("workflowDetail.validateDsl")}
              </button>
              <button className="ghost-button compact" onClick={() => loadDslTemplate("minimal")} type="button">
                {t("workflowDetail.loadMinimal")}
              </button>
              <button className="ghost-button compact" onClick={() => loadDslTemplate("waitForFeedback")} type="button">
                {t("workflowDetail.loadWaitTemplate")}
              </button>
            </div>
            <textarea className="text-area text-area-code" onChange={(event) => setDslText(event.target.value)} value={dslText} />
            {dslCheckMessage ? <span className="inline-message success">{dslCheckMessage}</span> : null}
            {dslCheckError ? <span className="inline-message error">{dslCheckError}</span> : null}
          </label>
          <div className="editor-actions compact">
            <button className="primary-button" disabled={publishing} type="submit">
              {publishing ? t("workflowDetail.publishing") : t("workflowDetail.publish")}
            </button>
            {publishMessage ? <span className="inline-message success">{publishMessage}</span> : null}
            {publishError ? <span className="inline-message error">{publishError}</span> : null}
          </div>
        </form>
      </Section>
    </div>
  );
}
