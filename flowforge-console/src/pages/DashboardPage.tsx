import { Link } from "react-router-dom";
import { api } from "../lib/api";
import { formatDateTime } from "../lib/format";
import { useAsyncData } from "../lib/hooks";
import { useI18n } from "../lib/i18n";
import { EmptyState } from "../components/EmptyState";
import { Section } from "../components/Section";
import { StatePanel } from "../components/StatePanel";
import { StatusPill } from "../components/StatusPill";

export function DashboardPage() {
  const { t } = useI18n();
  const workflows = useAsyncData(() => api.listWorkflows(), []);
  const instances = useAsyncData(() => api.listInstances(), []);

  const workflowList = workflows.data ?? [];
  const instanceList = instances.data ?? [];

  return (
    <div className="page-stack">
      <section className="hero-strip">
        <div>
          <span className="eyebrow">{t("dashboard.eyebrow")}</span>
          <h1>{t("dashboard.title")}</h1>
          <div className="hero-actions">
            <Link className="primary-button" to="/workflows/new">
              {t("dashboard.createWorkflow")}
            </Link>
          </div>
        </div>
        <div className="hero-metrics">
          <div>
            <span>{t("dashboard.definitions")}</span>
            <strong>{workflowList.length}</strong>
          </div>
          <div>
            <span>{t("dashboard.instances")}</span>
            <strong>{instanceList.length}</strong>
          </div>
          <div>
            <span>{t("dashboard.paused")}</span>
            <strong>{instanceList.filter((item) => item.status === "PAUSED").length}</strong>
          </div>
        </div>
      </section>

      <div className="grid-two">
        <Section title={t("dashboard.registryTitle")} description={t("dashboard.registryDescription")}>
          <div className="section-actions">
            <Link className="ghost-button compact" to="/workflows">
              {t("dashboard.viewAllWorkflows")}
            </Link>
          </div>
          {workflows.loading ? (
            <StatePanel detail={t("dashboard.loadingWorkflowsDetail")} title={t("dashboard.loadingWorkflows")} tone="loading" />
          ) : workflows.error ? (
            <StatePanel detail={workflows.error} title={t("dashboard.workflowsErrorTitle")} tone="error" />
          ) : workflowList.length === 0 ? (
            <EmptyState title={t("dashboard.noWorkflowsTitle")} detail={t("dashboard.noWorkflowsDetail")} />
          ) : (
            <div className="list-table">
              {workflowList.map((workflow) => (
                <Link className="table-row" key={workflow.id} to={`/workflows/${workflow.id}`}>
                  <div>
                    <strong>{workflow.name}</strong>
                    <span>{workflow.code}</span>
                  </div>
                  <StatusPill value={workflow.status} />
                  <span>{formatDateTime(workflow.updatedAt)}</span>
                </Link>
              ))}
            </div>
          )}
        </Section>

        <Section title={t("dashboard.runtimeTitle")} description={t("dashboard.runtimeDescription")}>
          <div className="section-actions">
            <Link className="ghost-button compact" to="/instances">
              {t("dashboard.viewAllInstances")}
            </Link>
          </div>
          {instances.loading ? (
            <StatePanel detail={t("dashboard.loadingInstancesDetail")} title={t("dashboard.loadingInstances")} tone="loading" />
          ) : instances.error ? (
            <StatePanel detail={instances.error} title={t("dashboard.instancesErrorTitle")} tone="error" />
          ) : instanceList.length === 0 ? (
            <EmptyState title={t("dashboard.noInstancesTitle")} detail={t("dashboard.noInstancesDetail")} />
          ) : (
            <div className="list-table">
              {instanceList.slice(0, 10).map((instance) => (
                <Link className="table-row" key={instance.id} to={`/instances/${instance.id}`}>
                  <div>
                    <strong>{t("dashboard.instanceLabel", { id: instance.id })}</strong>
                    <span>{t("dashboard.workflowLabel", { id: instance.workflowDefinitionId })}</span>
                  </div>
                  <StatusPill value={instance.status} />
                  <span>{formatDateTime(instance.startedAt)}</span>
                </Link>
              ))}
            </div>
          )}
        </Section>
      </div>
    </div>
  );
}
