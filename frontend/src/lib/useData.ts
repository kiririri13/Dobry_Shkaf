"use client";

import { useEffect, useState } from "react";
import { api, errorMessage } from "./api";

export function useData<T>(path: string, interval = 0) {
  const [version, setVersion] = useState(0);
  const key = path + ":" + version;
  const [result, setResult] = useState<{ key: string; data?: T; error?: string }>();

  useEffect(() => {
    if (!path) return;
    const controller = new AbortController();
    let timer: ReturnType<typeof setTimeout> | undefined;
    async function load() {
      try {
        const data = await api<T>(path, { signal: controller.signal });
        if (!controller.signal.aborted) setResult({ key, data });
      } catch (error) {
        if (!controller.signal.aborted) setResult((previous) => ({
          key, data: previous?.key === key ? previous.data : undefined, error: errorMessage(error),
        }));
      } finally {
        if (interval && !controller.signal.aborted) timer = setTimeout(load, interval);
      }
    }
    void load();
    return () => { clearTimeout(timer); controller.abort(); };
  }, [path, key, interval]);

  const current = result?.key === key ? result : undefined;
  return {
    data: current?.data,
    error: current?.error,
    loading: Boolean(path && !current),
    reload: () => setVersion((value) => value + 1),
  };
}
