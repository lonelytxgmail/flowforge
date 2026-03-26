import { useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { EmptyState } from "../components/EmptyState";
import { Section } from "../components/Section";
import { StatusPill } from "../components/StatusPill";
import { formatDateTime } from "../lib/format";
import { useAsyncData } from "../lib/hooks";
import { useI18n } from "../lib/i18n";
import { api } from "../lib/api";

export function InstancesPage() {
  const { t } = useI18n();
  const instances = useAsyncData(() => api.listInstances(), []);
  const [statusFilter, setStatusFilter] = useState("ALL");
  const [query, setQuery] = useState("");

  const instanceList = instances.data ?? [];
  const statuses = useMemo(
    () => ["ALL", ...Array.from(new Set(instanceList.map((item) => item.status)))],
    [instanceList]
  );

  const filtered = useMemo(() => {
    return instanceList.filter((item) => {
      const matchesStatus = statusFilter === "ALL" || item.status === statusFilter;
      const keyword = query.trim().toLowerCase();
      const haystack = [item.id, item.workflowDefinitionId, item.workflowVersionId, item.currentNodeId ?? ""]
        .join(" ")
        .toLowerCase();

      return matchesStatus && (keyword.length === 0 || haystack.includes(keyword));
    });
  }, [instanceList, query, statusFilter]);

  return (
    <div className="page-stack">
      <section className="hero-strip hero-strip-compact">
        <div>
          <span className="eyebrow">{t("instances.eyebrow")}</span>
          <h1>{t("instances.title")}</h1>
        </div>
        <div className="hero-metrics">
          <div>
            <span>{t("instances.total")}</span>
            <strong>{instanceList.length}</strong>
          </div>
          <div>
            <span>{t("instances.running")}</span>
            <strong>{instanceList.filter((item) => item.status === "RUNNING").length}</strong>
          </div>
          <div>
            <span>{t("instances.paused")}</span>
            <strong>{instanceList.filter((item) => item.status === "PAUSED").length}</strong>
          </div>
        </div>
      </section>

      <Section title={t("instances.listTitle")} description={t("instances.listDescription")}>
        <div className="filter-row">
          <label className="field-block">
            <span className="field-label">{t("instances.search")}</span>
            <input
              className="text-input"
              onChange={(event) => setQuery(event.target.value)}
              placeholder={t("instances.searchPlaceholder")}
              value={query}
            />
          </label>

          <label className="field-block">
            <span className="field-label">{t("instances.statusFilter")}</span>
            <select className="text-input select-input" onChange={(event) => setStatusFilter(event.target.value)} value={statusFilter}>
              {statuses.map((status) => (
                <option key={status} value={status}>
                  {status === "ALL" ? t("instances.allStatuses") : t(`status.${status}`)}
                </option>
              ))}
            </select>
          </label>
        </div>

        {instances.loading ? (
          <p>{t("instances.loading")}</p>
        ) : instances.error ? (
          <p>{instances.error}</p>
        ) : filtered.length === 0 ? (
          <EmptyState title={t("instances.emptyTitle")} detail={t("instances.emptyDetail")} />
        ) : (
          <div className="list-table">
            {filtered.map((instance) => (
              <Link className="table-row table-row-dense" key={instance.id} to={`/instances/${instance.id}`}>
                <div>
                  <strong>{t("instances.instanceLabel", { id: instance.id })}</strong>
                  <span>{t("instances.workflowLabel", { id: instance.workflowDefinitionId })}</span>
                </div>
                <span>{instance.currentNodeId ?? t("instances.noCurrentNode")}</span>
                <StatusPill value={instance.status} />
                <span>{formatDateTime(instance.startedAt)}</span>
              </Link>
            ))}
          </div>
        )}
      </Section>
    </div>
  );
}
