"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useSession } from "./Session";

export default function Navigation() {
  const { user } = useSession();
  const path = usePathname();
  if (["/login", "/register", "/forgot-password", "/reset-password"].includes(path)) return null;

  const profile = user ? "/profile" : "/login";
  const isCatalog = path === "/items" || path.startsWith("/items/");
  const links: [string, string, string, boolean][] = [
    ["/", "Главная", "home", path === "/"],
    ["/points", "Пункты приёма", "map", path.startsWith("/points")],
    ["/items", "Вещи", "pin", isCatalog],
    ["/profile/items", "Мои объявления", "box", path === "/profile/items"],
    ["/messages", "Сообщения", "message", path.startsWith("/messages")],
  ];
  if (path.startsWith("/profile") && user) links.unshift(["/profile", "Профиль", "user", true]);

  return <>
    <aside className="site-sidebar">
      <Link href="/" className="brand"><img src="/design/brand.svg" alt="Добрый Шкаф" /></Link>
      <nav aria-label="Основная навигация">
        {links.map(([href, label, icon, active]) => <Link key={href} href={href} aria-current={active ? "page" : undefined}>
          <img src={`/design/${icon}.svg`} alt="" />{label}
        </Link>)}
      </nav>
    </aside>
    <header className="site-header">
      <form className="site-search" action="/items" role="search">
        <button aria-label="Искать"><img src="/design/search.svg" alt="" /></button>
        <input type="search" name="q" aria-label="Поиск вещей" placeholder="Поиск вещей…" />
      </form>
      <div className="actions">
        <Link className="account-link" href={profile} aria-label={user ? "Профиль" : "Войти"} aria-current={path === "/profile" ? "page" : undefined}>
          <img src="/design/user.svg" alt="" />
        </Link>
      </div>
    </header>
  </>;
}
