import type { ReactNode } from "react";
import { StatePanel } from "./StatePanel";

type EmptyStateProps = {
  title: string;
  detail: string;
  action?: ReactNode;
};

export function EmptyState({ title, detail, action }: EmptyStateProps) {
  return <StatePanel action={action} detail={detail} title={title} tone="empty" />;
}
