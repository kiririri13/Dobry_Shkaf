"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { useData } from "@/lib/useData";
import type { Chat } from "@/lib/types";
import { useSession } from "./Session";

export default function Navigation() {
  const { user } = useSession();
  const path = usePathname();
  const isAuth = ["/login", "/register", "/forgot-password", "/reset-password"].includes(path);
  const showUnread = user && !isAuth && path !== "/admin";
  const { data: chats } = useData<Chat[]>(showUnread ? "/chats" : "", 15000);
  const { data: notifications } = useData<{ unreadCount: number }>(showUnread ? "/notifications?unreadOnly=true" : "", 15000);
  const hasUnread = chats?.some((chat) => chat.unreadCount > 0);
  if (isAuth) return null;

  const profile = user ? "/profile" : "/login";
  const isCatalog = path === "/items" || (path.startsWith("/items/") && path !== "/items/new");
  const isItem = isCatalog && path !== "/items" && !path.endsWith("/edit");
  const isProfile = path === "/profile" || path.startsWith("/profiles/") || path.endsWith("/review");
  const isAdminPage = path === "/admin";
  const canModerate = user?.role === "ADMIN" || user?.role === "MODERATOR";
  const usesDesignBackground = isItem || isProfile || isAdminPage || ["/", "/favorites", "/notifications", "/points", "/items/new"].includes(path)
    || path.startsWith("/messages");
  const links: [string, string, string, boolean][] = [
    ["/", "Главная", "home", path === "/"],
    ["/items", "Вещи", "pin", isCatalog],
    ["/points", "Карта приемов", "map", path.startsWith("/points")],
    ["/favorites", "Избранное", "heart", path === "/favorites"],
    ["/profile/items", "Мои объявления", "box", path === "/profile/items"],
    ["/notifications", "Уведомления", "bell", path === "/notifications"],
    ["/messages", "Сообщения", "message", path.startsWith("/messages")],
    ["/items/new", "Создать", "add", path === "/items/new"],
  ];
  if (path.startsWith("/profiles/") || (path.startsWith("/profile") && user)) {
    links.unshift([path.startsWith("/profiles/") ? path : "/profile", "Профиль", "user", true]);
  }
  if (canModerate) links.push(["/admin", "Модерация", "moderation", isAdminPage]);
  return <>
    <aside className={usesDesignBackground ? "site-sidebar design-sidebar" : "site-sidebar"}>
      <Link href="/" className="brand"><img src="/design/brand.svg" alt="Добрый Шкаф" /></Link>
      <nav aria-label="Основная навигация">
        {links.map(([href, label, icon, active]) => {
          const unread = href === "/messages" ? hasUnread
            : href === "/notifications" && (hasUnread || Boolean(notifications?.unreadCount));
          return <Link key={href} href={href} aria-current={active ? "page" : undefined}>
            <span className={unread ? "nav-icon nav-icon-unread" : "nav-icon"}><img src={`/design/${icon}.svg`} alt="" /></span>
            {label}{unread && <span className="visually-hidden"> (есть непрочитанные)</span>}
          </Link>;
        })}
      </nav>
    </aside>
    <header className="site-header design-header">
      <form className="site-search" action="/items" role="search">
        <button aria-label="Искать"><img src="/design/search.svg" alt="" /></button>
        <input type="search" name="q" aria-label="Поиск вещей, категорий, районов" placeholder="Поиск вещей, категорий, районов..." />
        <button className="search-filters" aria-label="Открыть каталог с фильтрами"><img src="/design/filters.svg" alt="" /></button>
      </form>
      <div className="actions">
        <Link className="header-city" href="/points">
          <img src="/design/pin.svg" alt="" />Ваш город<span className="header-chevron" aria-hidden="true" />
        </Link>
        <Link className="header-account" href={profile} aria-label={user ? "Профиль" : "Войти"} aria-current={path === "/profile" ? "page" : undefined}>
          <span className="account-link"><img src="/design/user.svg" alt="" /></span>
          <span className="header-chevron" aria-hidden="true" />
        </Link>
        <Link className="button" href="/items/new">＋ Отдать вещь</Link>
      </div>
    </header>
  </>;
}
