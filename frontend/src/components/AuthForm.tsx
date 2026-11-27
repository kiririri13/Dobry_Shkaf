"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useEffect, useRef, useState, type FormEvent } from "react";
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
  const [rememberLogin, setRememberLogin] = useState(false);
  const loginField = useRef<HTMLInputElement>(null);
  const { setUser } = useSession();
  const router = useRouter();

  useEffect(() => {
    if (register || !loginField.current) return;
    try {
      const saved = localStorage.getItem("dobry-login");
      if (saved) {
        loginField.current.value = saved;
        setRememberLogin(true);
      }
    } catch {}
  }, [register]);

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
        await api("/auth/register", {
          method: "POST",
          json: { name: String(data.get("name")).trim(), email: login, password },
        });
        setEmail(login);
        setNotice("Аккаунт создан. Подтвердите почту по ссылке из письма, затем войдите.");
      } else {
        await api<User>("/auth/login", { method: "POST", json: { login, password } });
        try {
          if (rememberLogin) localStorage.setItem("dobry-login", login);
          else localStorage.removeItem("dobry-login");
        } catch {}
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
      setNotice("Если почта ожидает подтверждения, на нее отправлено письмо.");
    } catch (failure) {
      setError(errorMessage(failure));
    } finally {
      setBusy(false);
    }
  }

  return <section className={register ? "auth-layout auth-register" : "auth-layout"}>
    {register && <p className="auth-register-signin">
      <span>Уже есть аккаунт?</span>
      <Link href="/login">Войти<img src="/design/register-signin-arrow.svg" alt="" /></Link>
    </p>}
    <div className="auth-content">
      <Link href="/" className="brand"><img src="/design/brand.svg" alt="Добрый Шкаф" /></Link>
      <nav className="auth-tabs" aria-label="Доступ к аккаунту">
        <Link href="/register" aria-current={register ? "page" : undefined}>Регистрация</Link>
        <Link href="/login" aria-current={register ? undefined : "page"}>Вход</Link>
      </nav>
      <h1>{register ? "Создайте аккаунт" : "С возвращением!"}</h1>
      <p className="auth-intro">{register ? <>Это займет всего пару минут.<br />Мы не передаем ваши данные третьим лицам.</> : <>Рады видеть вас снова.<br />Войдите в аккаунт, чтобы продолжить.</>}</p>
      <form onSubmit={submit}>
        <fieldset disabled={busy}>
          {register ? <RegisterFields /> : <>
            <div className="auth-field"><img src="/design/mail.svg" alt="" /><input ref={loginField} aria-label="Почта или телефон" placeholder="Электронная почта" name="login" autoComplete="username" required /></div>
            <PasswordField />
            <div className="auth-options">
              <label className="check"><input type="checkbox" checked={rememberLogin} onChange={(event) => setRememberLogin(event.target.checked)} />Запомнить логин</label>
              <Link className="auth-forgot" href="/forgot-password">Забыли пароль?</Link>
            </div>
          </>}
          <button className="auth-submit" aria-describedby={register ? "registration-terms" : undefined}>
            {busy ? "Подождите…" : register ? "Зарегистрироваться" : "Войти в аккаунт"}
          </button>
          {register && <p className="auth-register-terms" id="registration-terms">
            Нажимая «Зарегистрироваться», вы соглашаетесь с<br />
            <a href="/rules" target="_blank" rel="noreferrer">правилами сервиса</a>.
          </p>}
        </fieldset>
      </form>
      <RequestStatus error={error} />
      {Object.keys(fields).length > 0 && <ul className="error" aria-label="Ошибки полей">
        {Object.entries(fields).map(([field, message]) => <li key={field}>{message}</li>)}
      </ul>}
      {notice && <p role="status">{notice}</p>}
      {email && notice && typeof window !== "undefined" && ["localhost", "127.0.0.1"].includes(window.location.hostname) &&
        <p>При локальном запуске письма доступны в <a href="http://localhost:8025" target="_blank" rel="noreferrer">Mailpit</a>.</p>}
      {email && <button className="secondary" disabled={busy} onClick={resend}>Отправить письмо еще раз</button>}
      {!register && <p className="auth-switch">Еще нет аккаунта? <Link href="/register">Зарегистрироваться</Link></p>}
    </div>
    <aside className="auth-cover">
      <div>
        <p className="auth-slogan">Вещам<br /> нужна вторая<br /> <strong>жизнь</strong></p>
        <p>Отдавайте ненужное, находи<br /> нужное, меняй мир к лучшему</p>
      </div>
      <ul>
        <li><img src="/design/archive.svg" alt="" />Делись вещами с теми, кому они нужны</li>
        <li><img src="/design/auth-pin.svg" alt="" />Находи пункты приема рядом с тобой</li>
        <li><img src="/design/auth-heart.svg" alt="" />Помогай людям и заботься о природе</li>
      </ul>
    </aside>
  </section>;
}
