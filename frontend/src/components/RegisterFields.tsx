import { useState } from "react";

export function PasswordField({ label = "Пароль", name = "password", register = false }) {
  const [visible, setVisible] = useState(false);
  return <div className="auth-field">
    <img src="/design/lock.svg" alt="" />
    <input aria-label={label} placeholder={label} name={name} type={visible ? "text" : "password"}
      minLength={register ? 8 : undefined} autoComplete={register ? "new-password" : "current-password"} required />
    <button type="button" className="password-toggle" aria-label={visible ? "Скрыть пароль" : "Показать пароль"}
      aria-pressed={visible} onClick={() => setVisible(!visible)}><img src="/design/eye.svg" alt="" /></button>
  </div>;
}

export default function RegisterFields() {
  return <>
    <div className="auth-field"><img src="/design/user.svg" alt="" /><input aria-label="Имя" placeholder="Имя пользователя" name="name" autoComplete="name" required maxLength={100} /></div>
    <div className="auth-field"><img src="/design/mail.svg" alt="" /><input aria-label="Электронная почта" placeholder="Электронная почта" name="email" type="email" autoComplete="email" required maxLength={254} /></div>
    <div className="auth-field"><input aria-label="Телефон, если есть" placeholder="Телефон, если есть" name="phone" type="tel" autoComplete="tel" /></div>
    <PasswordField register />
    <PasswordField label="Повторите пароль" name="confirmation" register />
    <label className="check"><input name="rulesAccepted" type="checkbox" required /> <span>Принимаю <a href="/rules" target="_blank" rel="noreferrer">правила сервиса</a></span></label>
  </>;
}
