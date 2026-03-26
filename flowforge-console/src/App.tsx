import { AnimatePresence, motion } from "framer-motion";
import { NavLink, Route, Routes, useLocation } from "react-router-dom";
import { useI18n } from "./lib/i18n";
import { CreateWorkflowPage } from "./pages/CreateWorkflowPage";
import { DashboardPage } from "./pages/DashboardPage";
import { InstancesPage } from "./pages/InstancesPage";
import { NodeTemplatesPage } from "./pages/NodeTemplatesPage";
import { WorkflowDetailPage } from "./pages/WorkflowDetailPage";
import { InstanceDetailPage } from "./pages/InstanceDetailPage";
import { WorkflowsPage } from "./pages/WorkflowsPage";

export function App() {
  const location = useLocation();
  const { locale, setLocale, t } = useI18n();
  const navigationItems = [
    { to: "/", label: t("app.controlTower") },
    { to: "/workflows", label: t("app.workflows") },
    { to: "/instances", label: t("app.instances") },
    { to: "/node-templates", label: t("app.nodeTemplates") },
    { to: "/workflows/new", label: t("app.newWorkflow") },
  ];

  return (
    <div className="console-shell">
      <aside className="console-sidebar">
        <div className="brand-block">
          <span className="brand-kicker">FlowForge</span>
          <h1>{t("app.brandTitle")}</h1>
          <p>{t("app.brandDescription")}</p>
        </div>

        <nav className="sidebar-nav">
          {navigationItems.map((item) => (
            <NavLink
              className={({ isActive }) => `nav-link ${isActive ? "active" : ""}`}
              key={item.to}
              to={item.to}
            >
              {item.label}
            </NavLink>
          ))}
        </nav>

        <div className="sidebar-note">
          <span>{t("app.designDirection")}</span>
          <p>{t("app.designNote")}</p>
        </div>
      </aside>

      <main className="console-main">
        <header className="topbar">
          <div>
            <span className="eyebrow">{t("app.platform")}</span>
            <h2>{t("app.phaseOne")}</h2>
          </div>
          <div className="topbar-actions">
            <div className="locale-switch" role="group" aria-label={t("app.language")}>
              <button
                className={`ghost-button compact ${locale === "zh-CN" ? "active" : ""}`}
                onClick={() => setLocale("zh-CN")}
                type="button"
              >
                {t("app.chinese")}
              </button>
              <button
                className={`ghost-button compact ${locale === "en-US" ? "active" : ""}`}
                onClick={() => setLocale("en-US")}
                type="button"
              >
                {t("app.english")}
              </button>
            </div>
            <div className="status-chip">
              {location.pathname === "/workflows/new" ? t("app.authoring") : t("app.apiDriven")}
            </div>
          </div>
        </header>

        <AnimatePresence mode="wait">
          <motion.div
            key={location.pathname}
            initial={{ opacity: 0, y: 20 }}
            animate={{ opacity: 1, y: 0 }}
            exit={{ opacity: 0, y: -12 }}
            transition={{ duration: 0.28, ease: "easeOut" }}
          >
            <Routes>
              <Route path="/" element={<DashboardPage />} />
              <Route path="/workflows" element={<WorkflowsPage />} />
              <Route path="/workflows/new" element={<CreateWorkflowPage />} />
              <Route path="/workflows/:workflowId" element={<WorkflowDetailPage />} />
              <Route path="/instances" element={<InstancesPage />} />
              <Route path="/instances/:instanceId" element={<InstanceDetailPage />} />
              <Route path="/node-templates" element={<NodeTemplatesPage />} />
            </Routes>
          </motion.div>
        </AnimatePresence>
      </main>
    </div>
  );
}
