import { useI18n } from "../lib/i18n";

type StatusPillProps = {
  value: string;
};

export function StatusPill({ value }: StatusPillProps) {
  const { t } = useI18n();

  return <span className={`status-pill status-${value.toLowerCase()}`}>{t(`status.${value}`)}</span>;
}
