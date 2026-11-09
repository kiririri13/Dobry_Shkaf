"use client";

import { useEffect, useState } from "react";
import { api, errorMessage } from "./api";

export function useData<T>(path: string) {
  const [version, setVersion] = useState(0);
  const key = path + ":" + version;
  const [result, setResult] = useState<{ key: string; data?: T; error?: string }>();

  useEffect(() => {
    if (!path) return;
    const controller = new AbortController();
    api<T>(path, { signal: controller.signal }).then(
      (data) => { if (!controller.signal.aborted) setResult({ key, data }); },
      (error) => { if (!controller.signal.aborted) setResult({ key, error: errorMessage(error) }); },
    );
    return () => controller.abort();
  }, [path, key]);

  const current = result?.key === key ? result : undefined;
  return {
    data: current?.data,
    error: current?.error,
    loading: Boolean(path && !current),
    reload: () => setVersion((value) => value + 1),
  };
}
