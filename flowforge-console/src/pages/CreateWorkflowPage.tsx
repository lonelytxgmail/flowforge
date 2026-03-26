import { FormEvent, useState } from "react";
import { useNavigate } from "react-router-dom";
import { DslStructuredEditor } from "../components/DslStructuredEditor";
import { Section } from "../components/Section";
import { api, starterDslExamples } from "../lib/api";
import { validateWorkflowDsl } from "../lib/dsl";
import { prettyJson, tryParseJson } from "../lib/format";
import { useI18n } from "../lib/i18n";
import type { WorkflowDsl } from "../lib/types";

const starterKeys = Object.keys(starterDslExamples);

export function CreateWorkflowPage() {
  const { t } = useI18n();
  const navigate = useNavigate();
  const [code, setCode] = useState("");
  const [name, setName] = useState("");
  const [description, setDescription] = useState("");
  const [publishImmediately, setPublishImmediately] = useState(true);
  const [dslKey, setDslKey] = useState(starterKeys[0] ?? "minimal");
  const [dslText, setDslText] = useState(JSON.stringify(starterDslExamples.minimal, null, 2));
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [success, setSuccess] = useState<string | null>(null);
  const [dslCheckMessage, setDslCheckMessage] = useState<string | null>(null);
  const [dslCheckError, setDslCheckError] = useState<string | null>(null);
  const parsedDsl = tryParseJson<WorkflowDsl>(dslText);

  function applyStarter(nextKey: string) {
    setDslKey(nextKey);
    setDslText(prettyJson(starterDslExamples[nextKey]));
    setDslCheckMessage(null);
    setDslCheckError(null);
  }

  function formatDslText() {
    const parsed = tryParseJson<WorkflowDsl>(dslText);

    if (parsed.data) {
      setDslText(prettyJson(parsed.data));
      setDslCheckMessage(t("createWorkflow.validDsl"));
      setDslCheckError(null);
      return;
    }

    setDslCheckMessage(null);
    setDslCheckError(`${t("createWorkflow.invalidDsl")} ${parsed.error}`);
  }

  function validateDslText() {
    const parsed = tryParseJson<WorkflowDsl>(dslText);

    if (parsed.data) {
      const issues = validateWorkflowDsl(parsed.data).filter((issue) => issue.level === "error");
      if (issues.length === 0) {
        setDslCheckMessage(t("createWorkflow.validDsl"));
        setDslCheckError(null);
        return;
      }
      setDslCheckMessage(null);
      setDslCheckError(issues.map((issue) => issue.message).join("；"));
      return;
    }

    setDslCheckMessage(null);
    setDslCheckError(`${t("createWorkflow.invalidDsl")} ${parsed.error}`);
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setSubmitting(true);
    setError(null);
    setSuccess(null);

    try {
      const workflow = await api.createWorkflow({
        code,
        name,
        description: description || undefined,
      });

      if (publishImmediately) {
        const parsedDsl = tryParseJson<WorkflowDsl>(dslText);
        if (!parsedDsl.data) {
          throw new Error(`${t("createWorkflow.invalidDsl")} ${parsedDsl.error}`);
        }
        const issues = validateWorkflowDsl(parsedDsl.data).filter((issue) => issue.level === "error");
        if (issues.length > 0) {
          throw new Error(issues.map((issue) => issue.message).join("；"));
        }

        const dsl = parsedDsl.data;
        await api.publishVersion(String(workflow.id), { dsl });
        setSuccess(t("createWorkflow.createPublishSuccess", { id: workflow.id }));
      } else {
        setSuccess(t("createWorkflow.createSuccess", { id: workflow.id }));
      }

      setTimeout(() => {
        navigate(`/workflows/${workflow.id}`);
      }, 500);
    } catch (submitError) {
      setError(submitError instanceof Error ? submitError.message : t("createWorkflow.submitFailed"));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <div className="page-stack">
      <section className="hero-strip hero-strip-compact">
        <div>
          <span className="eyebrow">{t("createWorkflow.eyebrow")}</span>
          <h1>{t("createWorkflow.title")}</h1>
        </div>
        <div className="hero-metrics">
          <div>
            <span>{t("createWorkflow.authoringMode")}</span>
            <strong>JSON DSL</strong>
          </div>
          <div>
            <span>{t("createWorkflow.recommended")}</span>
            <strong>{t("createWorkflow.recommendedValue")}</strong>
          </div>
        </div>
      </section>

      <Section
        title={t("createWorkflow.sectionTitle")}
        description={t("createWorkflow.sectionDescription")}
      >
        <form className="editor-shell" onSubmit={handleSubmit}>
          <div className="editor-panel">
            <label className="field-block">
              <span className="field-label">{t("createWorkflow.code")}</span>
              <input
                className="text-input"
                value={code}
                onChange={(event) => setCode(event.target.value)}
                placeholder={t("createWorkflow.codePlaceholder")}
                required
              />
              <small>{t("createWorkflow.codeHint")}</small>
            </label>

            <label className="field-block">
              <span className="field-label">{t("createWorkflow.name")}</span>
              <input
                className="text-input"
                value={name}
                onChange={(event) => setName(event.target.value)}
                placeholder={t("createWorkflow.namePlaceholder")}
                required
              />
            </label>

            <label className="field-block">
              <span className="field-label">{t("createWorkflow.description")}</span>
              <textarea
                className="text-area text-area-short"
                value={description}
                onChange={(event) => setDescription(event.target.value)}
                placeholder={t("createWorkflow.descriptionPlaceholder")}
              />
            </label>

            <label className="toggle-row">
              <input
                checked={publishImmediately}
                onChange={(event) => setPublishImmediately(event.target.checked)}
                type="checkbox"
              />
              <span>{t("createWorkflow.publishNow")}</span>
            </label>
          </div>

          <div className="editor-panel">
            <div className="field-block">
              <span className="field-label">{t("createWorkflow.starterDsl")}</span>
              <div className="preset-row">
                {starterKeys.map((key) => (
                  <button
                    className={`ghost-button ${dslKey === key ? "active" : ""}`}
                    key={key}
                    onClick={() => applyStarter(key)}
                    type="button"
                  >
                    {key}
                  </button>
                ))}
              </div>
              <small>{t("createWorkflow.starterHint")}</small>
            </div>

            {parsedDsl.data ? (
              <Section
                title={t("createWorkflow.structuredTitle")}
                description={t("createWorkflow.structuredDescription")}
              >
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

            <label className="field-block">
              <span className="field-label">{t("createWorkflow.versionDsl")}</span>
              <div className="editor-toolbar">
                <button className="ghost-button compact" onClick={formatDslText} type="button">
                  {t("createWorkflow.formatDsl")}
                </button>
                <button className="ghost-button compact" onClick={validateDslText} type="button">
                  {t("createWorkflow.validateDsl")}
                </button>
              </div>
              <textarea
                className="text-area text-area-code"
                onChange={(event) => setDslText(event.target.value)}
                value={dslText}
              />
              {dslCheckMessage ? <span className="inline-message success">{dslCheckMessage}</span> : null}
              {dslCheckError ? <span className="inline-message error">{dslCheckError}</span> : null}
            </label>
          </div>

          <div className="editor-actions">
            <button className="primary-button" disabled={submitting} type="submit">
              {submitting ? t("createWorkflow.submitting") : t("createWorkflow.submit")}
            </button>
            {success ? <span className="inline-message success">{success}</span> : null}
            {error ? <span className="inline-message error">{error}</span> : null}
          </div>
        </form>
      </Section>
    </div>
  );
}
