import type { ReactNode } from "react";

type StatePanelProps = {
  title: string;
  detail: string;
  tone?: "empty" | "loading" | "error" | "info";
  action?: ReactNode;
};

export function StatePanel({ title, detail, tone = "info", action }: StatePanelProps) {
  return (
    <div className={`state-panel state-panel-${tone}`}>
      <div>
        <strong>{title}</strong>
        <p>{detail}</p>
      </div>
      {action ? <div className="state-panel-action">{action}</div> : null}
    </div>
  );
}
