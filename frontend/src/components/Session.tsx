"use client";

import { createContext, useContext, useEffect, useState, type ReactNode } from "react";
import { usePathname, useRouter } from "next/navigation";
import { api, ApiError, errorMessage } from "@/lib/api";
import type { User } from "@/lib/types";
import RequestStatus from "./RequestStatus";

type SessionState = { user?: User | null; error?: string; refresh: () => void; setUser: (user: User | null) => void };
const SessionContext = createContext<SessionState>({ refresh: () => {}, setUser: () => {} });

export function Session({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>();
  const [error, setError] = useState<string>();
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    api<User>("/auth/me", { signal: controller.signal }).then(
      (account) => { if (!controller.signal.aborted) setUser(account); },
      (failure) => {
        if (controller.signal.aborted) return;
        if (failure instanceof ApiError && failure.status === 401) setUser(null);
        else setError(errorMessage(failure));
      },
    );
    return () => controller.abort();
  }, [attempt]);

  function refresh() {
    setError(undefined);
    setUser(undefined);
    setAttempt((value) => value + 1);
  }

  return <SessionContext.Provider value={{ user, error, refresh, setUser: (account) => {
    setError(undefined);
    setUser(account);
  } }}>{children}</SessionContext.Provider>;
}

export function useSession() {
  return useContext(SessionContext);
}

export function PrivatePage({ children }: { children: ReactNode }) {
  const { user, error, refresh } = useSession();
  const router = useRouter();
  const path = usePathname();

  useEffect(() => {
    if (user === null) router.replace("/login?next=" + encodeURIComponent(path + window.location.search));
  }, [user, router, path]);

  if (error) return <RequestStatus error={error} retry={refresh} />;
  if (!user) return <RequestStatus loading />;
  return children;
}
