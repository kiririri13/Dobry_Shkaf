"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState, type FormEvent } from "react";
import { api, ApiError, errorMessage } from "@/lib/api";
import type { User } from "@/lib/types";
import RegisterFields, { PasswordField } from "./RegisterFields";
import RequestStatus from "./RequestStatus";
import { useSession } from "./Session";

export default function AuthForm({ register = false }: { register?: boolean }) {
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const [fields, setFields] = useState<Record<string, string>>({});
  const [email, setEmail] = useState("");
  const [notice, setNotice] = useState("");
  const { setUser } = useSession();
  const router = useRouter();

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (busy) return;
    setBusy(true);
    setError("");
    setFields({});
    setEmail("");
    setNotice("");
    const data = new FormData(event.currentTarget);
    const password = String(data.get("password"));
    const login = String(data.get(register ? "email" : "login")).trim();
    try {
      if (register) {
        if (password !== data.get("confirmation")) {
          setError("Пароли не совпадают.");
          return;
        }
        if (new TextEncoder().encode(password).length > 72) {
          setError("Пароль слишком длинный. Максимум — 72 байта.");
          return;
        }
        const phone = String(data.get("phone")).trim();
        await api("/auth/register", {
          method: "POST",
          json: { name: String(data.get("name")).trim(), email: login, password, ...(phone ? { phone } : {}) },
        });
        setEmail(login);
        setNotice("Аккаунт создан. Подтвердите почту по ссылке из письма, затем войдите.");
      } else {
        await api<User>("/auth/login", { method: "POST", json: { login, password } });
        setUser(await api<User>("/auth/me"));
        const next = new URLSearchParams(window.location.search).get("next");
        router.replace(next && /^\/(?!\/)[^\x00-\x20\\]*$/.test(next) ? next : "/profile");
      }
    } catch (failure) {
      setError(errorMessage(failure));
      if (failure instanceof ApiError) {
        setFields(failure.fields);
        if (failure.code === "EMAIL_NOT_CONFIRMED") setEmail(login.includes("@") ? login : "");
      }
    } finally {
      setBusy(false);
    }
  }

  async function resend() {
    setBusy(true);
    setError("");
    try {
      await api("/auth/resend-confirmation", { method: "POST", json: { email } });
      setNotice("Если почта ожидает подтверждения, на неё отправлено письмо.");
    } catch (failure) {
      setError(errorMessage(failure));
    } finally {
      setBusy(false);
    }
  }

  return <section className={register ? "auth-layout auth-register" : "auth-layout"}>
    <div className="auth-content">
      <Link href="/" className="brand"><img src="/design/brand.svg" alt="Добрый Шкаф" /></Link>
      <nav className="auth-tabs" aria-label="Доступ к аккаунту">
        <Link href="/register" aria-current={register ? "page" : undefined}>Регистрация</Link>
        <Link href="/login" aria-current={register ? undefined : "page"}>Вход</Link>
      </nav>
      <h1>{register ? "Создайте аккаунт" : "С возвращением!"}</h1>
      <p className="auth-intro">{register ? "Это займёт всего пару минут." : <>Рады видеть вас снова.<br />Войдите в аккаунт, чтобы продолжить.</>}</p>
      <form onSubmit={submit}>
        <fieldset disabled={busy}>
          {register ? <RegisterFields /> : <>
            <div className="auth-field"><img src="/design/mail.svg" alt="" /><input aria-label="Почта или телефон" placeholder="Почта или телефон" name="login" autoComplete="username" required /></div>
            <PasswordField />
          </>}
          <button className="auth-submit">{busy ? "Подождите…" : register ? "Зарегистрироваться" : "Войти в аккаунт →"}</button>
        </fieldset>
      </form>
      <RequestStatus error={error} />
      {Object.keys(fields).length > 0 && <ul className="error" aria-label="Ошибки полей">
        {Object.entries(fields).map(([field, message]) => <li key={field}>{message}</li>)}
      </ul>}
      {notice && <p role="status">{notice}</p>}
      {email && <button className="secondary" disabled={busy} onClick={resend}>Отправить письмо ещё раз</button>}
      {!register && <p className="auth-help"><Link href="/forgot-password">Забыли пароль?</Link></p>}
      <p className="auth-switch">{register ? "Уже есть аккаунт?" : "Ещё нет аккаунта?"} <Link href={register ? "/login" : "/register"}>{register ? "Войти" : "Зарегистрироваться"}</Link></p>
    </div>
    <aside className="auth-cover">
      <div>
        <p className="auth-slogan">Вещам<br /> нужна вторая<br /> <strong>жизнь</strong></p>
        <p>Отдавайте ненужное, находите<br /> нужное, меняйте мир к лучшему</p>
      </div>
      <ul>
        <li><img src="/design/archive.svg" alt="" />Делись вещами с теми, кому они нужны</li>
        <li><img src="/design/auth-pin.svg" alt="" />Находи пункты приёма рядом с тобой</li>
        <li><img src="/design/auth-heart.svg" alt="" />Помогай людям и заботься о природе</li>
      </ul>
    </aside>
  </section>;
}
