export function formatDateTime(value?: string | null): string {
  if (!value) {
    return "—";
  }

  return new Date(value).toLocaleString("zh-CN", {
    hour12: false,
  });
}

export function formatJson(value?: unknown): string {
  if (value === null || value === undefined || value === "") {
    return "—";
  }

  try {
    if (typeof value === "string") {
      const parsed = JSON.parse(value);
      return JSON.stringify(parsed, null, 2);
    }

    return JSON.stringify(value, null, 2);
  } catch {
    return String(value);
  }
}

export function tryParseJson<T>(value: string): { data: T | null; error: string | null } {
  try {
    return {
      data: JSON.parse(value) as T,
      error: null,
    };
  } catch (error) {
    return {
      data: null,
      error: error instanceof Error ? error.message : "Invalid JSON",
    };
  }
}

export function prettyJson(value: unknown): string {
  return JSON.stringify(value, null, 2);
}
