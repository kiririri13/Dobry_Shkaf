"use client";

import Link from "next/link";
import { useState, type FormEvent } from "react";
import { api, ApiError, errorMessage } from "@/lib/api";
import { PasswordField } from "./RegisterFields";
import RequestStatus from "./RequestStatus";

export default function PasswordRecovery({ token }: { token?: string }) {
  const reset = token !== undefined;
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [success, setSuccess] = useState(false);
  const [invalid, setInvalid] = useState(reset && !token);

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (busy) return;
    setError("");
    const data = new FormData(event.currentTarget);
    const newPassword = String(data.get("password") || "");
    if (reset && newPassword !== data.get("confirmation")) return setError("Пароли не совпадают.");
    if (reset && new TextEncoder().encode(newPassword).length > 72) return setError("Пароль слишком длинный. Максимум 72 байта.");
    setBusy(true);
    try {
      await api(reset ? "/auth/password-reset/confirm" : "/auth/password-reset", {
        method: "POST",
        json: reset ? { token, newPassword } : { email: String(data.get("email")).trim() },
      });
      setSuccess(true);
    } catch (failure) {
      if (failure instanceof ApiError && failure.code === "INVALID_RESET_TOKEN") setInvalid(true);
      else setError(errorMessage(failure));
    } finally {
      setBusy(false);
    }
  }

  const content = <section className={reset ? "panel narrow recovery" : "panel recovery recovery-request"}>
    <Link href="/" className="brand"><img src="/design/brand.svg" alt="Добрый Шкаф" /></Link>
    <h1>{reset ? "Новый пароль" : "Забыли пароль?"}</h1>
    {invalid && <p role="alert">Ссылка недействительна или устарела. <Link href="/forgot-password">Запросить новую ссылку</Link></p>}
    {success && <p role="status">{reset ? "Пароль изменён. Теперь можно войти." : "Если такая почта зарегистрирована, мы отправили на неё письмо со ссылкой для сброса пароля."}</p>}
    {!invalid && !success && <form onSubmit={submit}>
      <fieldset disabled={busy}>
        {reset ? <>
          <PasswordField label="Новый пароль" register />
          <PasswordField label="Повторите пароль" name="confirmation" register />
        </> : <label>Электронная почта<input name="email" type="email" autoComplete="email" required maxLength={254} /></label>}
        <button className={reset ? undefined : "recovery-submit"}>{busy ? "Подождите…" : reset ? "Сохранить пароль" : "Отправить ссылку"}</button>
      </fieldset>
    </form>}
    <RequestStatus error={error} />
    <p className="auth-help"><Link href="/login">Вернуться ко входу</Link></p>
  </section>;
  return reset ? content : <div className="recovery-screen">{content}</div>;
}
