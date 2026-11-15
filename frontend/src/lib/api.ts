export class ApiError extends Error {
  constructor(
    public status: number,
    message: string,
    public code?: string,
    public fields: Record<string, string> = {},
  ) {
    super(message);
  }
}

type Options = { method?: string; json?: unknown; signal?: AbortSignal };

function csrfToken() {
  const cookie = document.cookie.split("; ").find((value) => value.startsWith("XSRF-TOKEN="));
  return cookie ? decodeURIComponent(cookie.slice("XSRF-TOKEN=".length)) : "";
}

export async function api<T = void>(path: string, options: Options = {}): Promise<T> {
  const { method = "GET", json, signal } = options;
  const headers = new Headers();
  const publicPost = ["/auth/login", "/auth/register", "/auth/resend-confirmation",
    "/auth/password-reset", "/auth/password-reset/confirm"].includes(path);
  if (method !== "GET" && !publicPost) {
    if (!csrfToken()) await api("/auth/me");
    headers.set("X-XSRF-TOKEN", csrfToken());
  }
  if (json !== undefined) headers.set("Content-Type", "application/json");

  const response = await fetch("/api" + path, {
    method, headers, signal, credentials: "same-origin", cache: "no-store",
    body: json !== undefined ? JSON.stringify(json) : undefined,
  });
  const body = response.headers.get("content-type")?.includes("json")
    ? await response.json().catch(() => null)
    : null;

  if (!response.ok) {
    const messages: Record<number, string> = {
      401: "Войдите в аккаунт.", 403: "Действие недоступно.", 404: "Запись не найдена.",
      413: "Файл слишком большой.",
    };
    throw new ApiError(
      response.status,
      body?.message || messages[response.status] || "Не удалось выполнить запрос.",
      body?.code,
      body?.fields,
    );
  }
  return body as T;
}

export function errorMessage(error: unknown) {
  return error instanceof ApiError ? error.message : "Не удалось связаться с сервером.";
}

export function query(values: Record<string, string | number | boolean>) {
  const params = new URLSearchParams();
  for (const [key, value] of Object.entries(values)) {
    if (value !== "") params.set(key, String(value));
  }
  const search = params.toString();
  return search ? "?" + search : "";
}
