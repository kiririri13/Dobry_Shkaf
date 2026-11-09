"use client";

import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";
import RequestStatus from "@/components/RequestStatus";
import { useSession } from "@/components/Session";
import { api, errorMessage } from "@/lib/api";
import type { User } from "@/lib/types";

export default function Login() {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const { setUser } = useSession();
  const router = useRouter();

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (busy) return;
    setBusy(true);
    setError("");
    const data = new FormData(event.currentTarget);
    try {
      await api("/auth/login", {
        method: "POST",
        json: { login: String(data.get("login")).trim(), password: String(data.get("password")) },
      });
      setUser(await api<User>("/auth/me"));
      const next = new URLSearchParams(window.location.search).get("next");
      router.replace(next && /^\/(?!\/)[^\x00-\x20\\]*$/.test(next) ? next : "/profile");
    } catch (failure) {
      setError(errorMessage(failure));
    } finally {
      setBusy(false);
    }
  }

  return <section className="panel narrow">
    <h1>Вход</h1>
    <form onSubmit={submit}>
      <fieldset disabled={busy}>
        <label>Почта или телефон<input name="login" autoComplete="username" required /></label>
        <label>Пароль<input name="password" type="password" autoComplete="current-password" required /></label>
        <button>{busy ? "Входим..." : "Войти"}</button>
      </fieldset>
    </form>
    <RequestStatus error={error} />
  </section>;
}
