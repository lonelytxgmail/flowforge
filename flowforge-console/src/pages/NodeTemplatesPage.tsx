import { useMemo, useState } from "react";
import { Section } from "../components/Section";
import { api } from "../lib/api";
import { prettyJson, tryParseJson } from "../lib/format";
import { useAsyncData } from "../lib/hooks";
import { useI18n } from "../lib/i18n";
import type { NodeTemplate, SaveNodeTemplateRequest } from "../lib/types";

type FormState = {
  id?: number;
  code: string;
  name: string;
  description: string;
  nodeType: string;
  nodeConfigText: string;
};

type EnvironmentPresetForm = {
  mysqlJdbcUrl: string;
  mysqlUsername: string;
  mysqlPassword: string;
  redisHost: string;
  redisPort: string;
  redisPassword: string;
  esUrl: string;
  esUsername: string;
  esPassword: string;
  ssoTokenUrl: string;
  ssoClientId: string;
  ssoClientSecret: string;
};

const emptyForm: FormState = {
  code: "",
  name: "",
  description: "",
  nodeType: "ATOMIC_ABILITY",
  nodeConfigText: "{\n  \"abilityType\": \"REST\",\n  \"url\": \"http://localhost:8080/api/mock/echo\",\n  \"method\": \"POST\"\n}",
};

const emptyPresetForm: EnvironmentPresetForm = {
  mysqlJdbcUrl: "",
  mysqlUsername: "",
  mysqlPassword: "",
  redisHost: "",
  redisPort: "6379",
  redisPassword: "",
  esUrl: "",
  esUsername: "",
  esPassword: "",
  ssoTokenUrl: "",
  ssoClientId: "",
  ssoClientSecret: "",
};

function mapTemplateToForm(template: NodeTemplate): FormState {
  return {
    id: template.id,
    code: template.code,
    name: template.name,
    description: template.description ?? "",
    nodeType: template.nodeType,
    nodeConfigText: prettyJson(template.nodeConfig),
  };
}

export function NodeTemplatesPage() {
  const { t } = useI18n();
  const templates = useAsyncData(() => api.listNodeTemplates(), []);
  const [selectedType, setSelectedType] = useState("ALL");
  const [searchText, setSearchText] = useState("");
  const [form, setForm] = useState<FormState>(emptyForm);
  const [presetForm, setPresetForm] = useState<EnvironmentPresetForm>(emptyPresetForm);
  const [message, setMessage] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [importingPresets, setImportingPresets] = useState(false);

  const filteredTemplates = useMemo(() => {
    const data = templates.data ?? [];
    return data.filter((item) => {
      const matchesType = selectedType === "ALL" || item.nodeType === selectedType;
      const keyword = searchText.trim().toLowerCase();
      const matchesSearch =
        keyword.length === 0 ||
        item.name.toLowerCase().includes(keyword) ||
        item.code.toLowerCase().includes(keyword) ||
        (item.description ?? "").toLowerCase().includes(keyword);
      return matchesType && matchesSearch;
    });
  }, [searchText, selectedType, templates.data]);

  function loadTemplate(template: NodeTemplate) {
    setForm(mapTemplateToForm(template));
    setMessage(null);
  }

  function resetForm() {
    setForm(emptyForm);
    setMessage(null);
  }

  async function submitForm() {
    const parsed = tryParseJson<Record<string, unknown>>(form.nodeConfigText);
    if (!parsed.data) {
      setMessage(t("templates.invalidConfig"));
      return;
    }

    const payload: SaveNodeTemplateRequest = {
      code: form.code,
      name: form.name,
      description: form.description || undefined,
      nodeType: form.nodeType,
      nodeConfig: parsed.data,
    };

    setSaving(true);
    try {
      if (form.id) {
        await api.updateNodeTemplate(String(form.id), payload);
        setMessage(t("templates.updated"));
      } else {
        await api.saveNodeTemplate(payload);
        setMessage(t("templates.created"));
      }
      templates.reload();
      resetForm();
    } catch (error) {
      setMessage(error instanceof Error ? error.message : t("templates.saveFailed"));
    } finally {
      setSaving(false);
    }
  }

  async function deleteTemplate(templateId: number) {
    try {
      await api.deleteNodeTemplate(String(templateId));
      if (form.id === templateId) {
        resetForm();
      }
      setMessage(t("templates.deleted"));
      templates.reload();
    } catch (error) {
      setMessage(error instanceof Error ? error.message : t("templates.deleteFailed"));
    }
  }

  async function importPresetPack() {
    const templatesToCreate: SaveNodeTemplateRequest[] = [];

    if (presetForm.mysqlJdbcUrl.trim()) {
      templatesToCreate.push({
        code: "preset_mysql_query_runtime",
        name: "MySQL Query Preset",
        description: "Query MySQL and support runtime variable substitution in SQL.",
        nodeType: "ATOMIC_ABILITY",
        nodeConfig: {
          abilityType: "DATABASE",
          jdbcUrl: presetForm.mysqlJdbcUrl,
          username: presetForm.mysqlUsername,
          password: presetForm.mysqlPassword,
          operation: "QUERY",
          sql: "SELECT '{{ input.orderId }}' AS order_id",
        },
      });
    }

    if (presetForm.redisHost.trim()) {
      templatesToCreate.push(
        {
          code: "preset_redis_get_runtime",
          name: "Redis Get Preset",
          description: "Read Redis values with runtime variable keys.",
          nodeType: "ATOMIC_ABILITY",
          nodeConfig: {
            abilityType: "REDIS",
            host: presetForm.redisHost,
            port: Number(presetForm.redisPort || "6379"),
            password: presetForm.redisPassword,
            operation: "GET",
            key: "flowforge:test:{{ input.orderId }}",
          },
        },
        {
          code: "preset_redis_set_runtime",
          name: "Redis Set Preset",
          description: "Write Redis values with runtime variable keys and values.",
          nodeType: "ATOMIC_ABILITY",
          nodeConfig: {
            abilityType: "REDIS",
            host: presetForm.redisHost,
            port: Number(presetForm.redisPort || "6379"),
            password: presetForm.redisPassword,
            operation: "SET",
            key: "flowforge:test:{{ input.orderId }}",
            value: "{{ input.orderId }}",
          },
        }
      );
    }

    if (presetForm.esUrl.trim()) {
      templatesToCreate.push({
        code: "preset_es_health_runtime",
        name: "Elasticsearch Health Preset",
        description: "Call Elasticsearch health endpoint with reusable credentials.",
        nodeType: "ATOMIC_ABILITY",
        nodeConfig: {
          abilityType: "ELASTICSEARCH",
          url: presetForm.esUrl,
          method: "GET",
          username: presetForm.esUsername,
          password: presetForm.esPassword,
          body: {},
        },
      });
    }

    if (presetForm.ssoTokenUrl.trim()) {
      templatesToCreate.push({
        code: "preset_sso_login_session",
        name: "SSO Login Session Preset",
        description: "Login session REST template for chained operations.",
        nodeType: "ATOMIC_ABILITY",
        nodeConfig: {
          abilityType: "REST",
          url: "http://replace-with-business-endpoint",
          method: "POST",
          headers: {},
          body: {
            payload: "{{ input }}",
          },
          auth: {
            type: "login_session",
            loginUrl: presetForm.ssoTokenUrl,
            loginMethod: "POST",
            tokenPath: "token",
            headerName: "Authorization",
            prefix: "Bearer",
            sessionContextKey: "authSession",
            loginBody: {
              clientId: presetForm.ssoClientId,
              clientSecret: presetForm.ssoClientSecret,
            },
          },
        },
      });
    }

    if (!templatesToCreate.length) {
      setMessage(t("templates.importNothing"));
      return;
    }

    setImportingPresets(true);
    try {
      for (const template of templatesToCreate) {
        await api.saveNodeTemplate(template);
      }
      setMessage(t("templates.importSuccess", { count: templatesToCreate.length }));
      templates.reload();
    } catch (error) {
      setMessage(error instanceof Error ? error.message : t("templates.importFailed"));
    } finally {
      setImportingPresets(false);
    }
  }

  return (
    <Section
      title={t("templates.title")}
      description={t("templates.description")}
      aside={
        <button className="ghost-button" onClick={resetForm} type="button">
          {t("templates.newTemplate")}
        </button>
      }
    >
      <div className="data-grid data-grid-wide">
        <div className="data-card">
          <div className="card-head">
            <div>
              <h3>{t("templates.registryTitle")}</h3>
              <p>{t("templates.registryDescription")}</p>
            </div>
          </div>

          <div className="field-inline-title">
            <label className="field-block">
              <span className="field-label">{t("templates.search")}</span>
              <input
                className="text-input"
                onChange={(event) => setSearchText(event.target.value)}
                placeholder={t("templates.searchPlaceholder")}
                value={searchText}
              />
            </label>
            <label className="field-block">
              <span className="field-label">{t("templates.typeFilter")}</span>
              <select className="text-input select-input" onChange={(event) => setSelectedType(event.target.value)} value={selectedType}>
                <option value="ALL">{t("templates.allTypes")}</option>
                <option value="START">START</option>
                <option value="END">END</option>
                <option value="ATOMIC_ABILITY">ATOMIC_ABILITY</option>
                <option value="DIGITAL_EMPLOYEE">DIGITAL_EMPLOYEE</option>
                <option value="WAIT_FOR_FEEDBACK">WAIT_FOR_FEEDBACK</option>
                <option value="CONDITION">CONDITION</option>
              </select>
            </label>
          </div>

          <div className="registry-list">
            {templates.loading ? <p>{t("templates.loading")}</p> : null}
            {!templates.loading && !filteredTemplates.length ? <p>{t("templates.empty")}</p> : null}
            {filteredTemplates.map((template) => (
              <article className="registry-item" key={template.id}>
                <div>
                  <strong>{template.name}</strong>
                  <p>{template.code}</p>
                  <p>{template.nodeType}</p>
                </div>
                <div className="preset-row">
                  <button className="ghost-button compact" onClick={() => loadTemplate(template)} type="button">
                    {t("templates.edit")}
                  </button>
                  <button className="ghost-button compact" onClick={() => deleteTemplate(template.id)} type="button">
                    {t("templates.delete")}
                  </button>
                </div>
              </article>
            ))}
          </div>
        </div>

        <div className="data-card">
          <div className="card-head">
            <div>
              <h3>{form.id ? t("templates.editTitle") : t("templates.createTitle")}</h3>
              <p>{t("templates.formDescription")}</p>
            </div>
          </div>

          {message ? <p className="structured-editor-note">{message}</p> : null}

          <div className="structured-card-stack">
            <label className="field-block">
              <span className="field-label">{t("templates.code")}</span>
              <input className="text-input" onChange={(event) => setForm((current) => ({ ...current, code: event.target.value }))} value={form.code} />
            </label>
            <label className="field-block">
              <span className="field-label">{t("templates.name")}</span>
              <input className="text-input" onChange={(event) => setForm((current) => ({ ...current, name: event.target.value }))} value={form.name} />
            </label>
            <label className="field-block">
              <span className="field-label">{t("templates.descriptionField")}</span>
              <input className="text-input" onChange={(event) => setForm((current) => ({ ...current, description: event.target.value }))} value={form.description} />
            </label>
            <label className="field-block">
              <span className="field-label">{t("templates.nodeType")}</span>
              <select className="text-input select-input" onChange={(event) => setForm((current) => ({ ...current, nodeType: event.target.value }))} value={form.nodeType}>
                <option value="START">START</option>
                <option value="END">END</option>
                <option value="ATOMIC_ABILITY">ATOMIC_ABILITY</option>
                <option value="DIGITAL_EMPLOYEE">DIGITAL_EMPLOYEE</option>
                <option value="WAIT_FOR_FEEDBACK">WAIT_FOR_FEEDBACK</option>
                <option value="CONDITION">CONDITION</option>
              </select>
            </label>
            <label className="field-block">
              <span className="field-label">{t("templates.nodeConfig")}</span>
              <textarea
                className="text-area text-area-code text-area-large"
                onChange={(event) => setForm((current) => ({ ...current, nodeConfigText: event.target.value }))}
                value={form.nodeConfigText}
              />
            </label>
            <div className="preset-row">
              <button className="ghost-button" disabled={saving} onClick={submitForm} type="button">
                {saving ? t("templates.saving") : form.id ? t("templates.update") : t("templates.create")}
              </button>
            </div>
          </div>
        </div>

        <div className="data-card field-span-two">
          <div className="card-head">
            <div>
              <h3>{t("templates.importTitle")}</h3>
              <p>{t("templates.importDescription")}</p>
            </div>
          </div>

          <div className="form-grid-two">
            <label className="field-block field-span-two">
              <span className="field-label">{t("templates.mysqlJdbcUrl")}</span>
              <input className="text-input" onChange={(event) => setPresetForm((current) => ({ ...current, mysqlJdbcUrl: event.target.value }))} value={presetForm.mysqlJdbcUrl} />
            </label>
            <label className="field-block">
              <span className="field-label">{t("templates.mysqlUsername")}</span>
              <input className="text-input" onChange={(event) => setPresetForm((current) => ({ ...current, mysqlUsername: event.target.value }))} value={presetForm.mysqlUsername} />
            </label>
            <label className="field-block">
              <span className="field-label">{t("templates.mysqlPassword")}</span>
              <input className="text-input" onChange={(event) => setPresetForm((current) => ({ ...current, mysqlPassword: event.target.value }))} value={presetForm.mysqlPassword} />
            </label>
            <label className="field-block">
              <span className="field-label">{t("templates.redisHost")}</span>
              <input className="text-input" onChange={(event) => setPresetForm((current) => ({ ...current, redisHost: event.target.value }))} value={presetForm.redisHost} />
            </label>
            <label className="field-block">
              <span className="field-label">{t("templates.redisPort")}</span>
              <input className="text-input" onChange={(event) => setPresetForm((current) => ({ ...current, redisPort: event.target.value }))} value={presetForm.redisPort} />
            </label>
            <label className="field-block field-span-two">
              <span className="field-label">{t("templates.redisPassword")}</span>
              <input className="text-input" onChange={(event) => setPresetForm((current) => ({ ...current, redisPassword: event.target.value }))} value={presetForm.redisPassword} />
            </label>
            <label className="field-block field-span-two">
              <span className="field-label">{t("templates.esUrl")}</span>
              <input className="text-input" onChange={(event) => setPresetForm((current) => ({ ...current, esUrl: event.target.value }))} value={presetForm.esUrl} />
            </label>
            <label className="field-block">
              <span className="field-label">{t("templates.esUsername")}</span>
              <input className="text-input" onChange={(event) => setPresetForm((current) => ({ ...current, esUsername: event.target.value }))} value={presetForm.esUsername} />
            </label>
            <label className="field-block">
              <span className="field-label">{t("templates.esPassword")}</span>
              <input className="text-input" onChange={(event) => setPresetForm((current) => ({ ...current, esPassword: event.target.value }))} value={presetForm.esPassword} />
            </label>
            <label className="field-block field-span-two">
              <span className="field-label">{t("templates.ssoTokenUrl")}</span>
              <input className="text-input" onChange={(event) => setPresetForm((current) => ({ ...current, ssoTokenUrl: event.target.value }))} value={presetForm.ssoTokenUrl} />
            </label>
            <label className="field-block">
              <span className="field-label">{t("templates.ssoClientId")}</span>
              <input className="text-input" onChange={(event) => setPresetForm((current) => ({ ...current, ssoClientId: event.target.value }))} value={presetForm.ssoClientId} />
            </label>
            <label className="field-block">
              <span className="field-label">{t("templates.ssoClientSecret")}</span>
              <input className="text-input" onChange={(event) => setPresetForm((current) => ({ ...current, ssoClientSecret: event.target.value }))} value={presetForm.ssoClientSecret} />
            </label>
          </div>

          <div className="preset-row">
            <button className="ghost-button" disabled={importingPresets} onClick={importPresetPack} type="button">
              {importingPresets ? t("templates.importing") : t("templates.importButton")}
            </button>
          </div>
        </div>
      </div>
    </Section>
  );
}
