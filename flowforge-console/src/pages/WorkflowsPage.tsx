import { useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { EmptyState } from "../components/EmptyState";
import { Section } from "../components/Section";
import { StatusPill } from "../components/StatusPill";
import { formatDateTime } from "../lib/format";
import { useAsyncData } from "../lib/hooks";
import { useI18n } from "../lib/i18n";
import { api } from "../lib/api";

export function WorkflowsPage() {
  const { t } = useI18n();
  const workflows = useAsyncData(() => api.listWorkflows(), []);
  const [query, setQuery] = useState("");
  const [statusFilter, setStatusFilter] = useState("ALL");

  const workflowList = workflows.data ?? [];
  const statuses = useMemo(
    () => ["ALL", ...Array.from(new Set(workflowList.map((item) => item.status)))],
    [workflowList]
  );

  const filtered = useMemo(() => {
    return workflowList.filter((item) => {
      const matchesStatus = statusFilter === "ALL" || item.status === statusFilter;
      const keyword = query.trim().toLowerCase();
      const matchesQuery =
        keyword.length === 0 ||
        item.name.toLowerCase().includes(keyword) ||
        item.code.toLowerCase().includes(keyword) ||
        (item.description ?? "").toLowerCase().includes(keyword);

      return matchesStatus && matchesQuery;
    });
  }, [query, statusFilter, workflowList]);

  return (
    <div className="page-stack">
      <section className="hero-strip hero-strip-compact">
        <div>
          <span className="eyebrow">{t("workflows.eyebrow")}</span>
          <h1>{t("workflows.title")}</h1>
          <div className="hero-actions">
            <Link className="primary-button" to="/workflows/new">
              {t("workflows.create")}
            </Link>
          </div>
        </div>
        <div className="hero-metrics">
          <div>
            <span>{t("workflows.total")}</span>
            <strong>{workflowList.length}</strong>
          </div>
          <div>
            <span>{t("workflows.filtered")}</span>
            <strong>{filtered.length}</strong>
          </div>
        </div>
      </section>

      <Section title={t("workflows.listTitle")} description={t("workflows.listDescription")}>
        <div className="filter-row">
          <label className="field-block">
            <span className="field-label">{t("workflows.search")}</span>
            <input
              className="text-input"
              onChange={(event) => setQuery(event.target.value)}
              placeholder={t("workflows.searchPlaceholder")}
              value={query}
            />
          </label>

          <label className="field-block">
            <span className="field-label">{t("workflows.statusFilter")}</span>
            <select className="text-input select-input" onChange={(event) => setStatusFilter(event.target.value)} value={statusFilter}>
              {statuses.map((status) => (
                <option key={status} value={status}>
                  {status === "ALL" ? t("workflows.allStatuses") : t(`status.${status}`)}
                </option>
              ))}
            </select>
          </label>
        </div>

        {workflows.loading ? (
          <p>{t("workflows.loading")}</p>
        ) : workflows.error ? (
          <p>{workflows.error}</p>
        ) : filtered.length === 0 ? (
          <EmptyState title={t("workflows.emptyTitle")} detail={t("workflows.emptyDetail")} />
        ) : (
          <div className="list-table">
            {filtered.map((workflow) => (
              <Link className="table-row table-row-dense" key={workflow.id} to={`/workflows/${workflow.id}`}>
                <div>
                  <strong>{workflow.name}</strong>
                  <span>{workflow.code}</span>
                </div>
                <span>{workflow.description ?? t("workflows.noDescription")}</span>
                <StatusPill value={workflow.status} />
                <span>{formatDateTime(workflow.updatedAt)}</span>
              </Link>
            ))}
          </div>
        )}
      </Section>
    </div>
  );
}
