"use client";

import Link from "next/link";
import { useState } from "react";
import RequestStatus from "@/components/RequestStatus";
import { PrivatePage, useSession } from "@/components/Session";
import { api, errorMessage } from "@/lib/api";
import { useData } from "@/lib/useData";
import type { Chat, Review } from "@/lib/types";

const filters = ["Все", "Объявления", "Сообщения", "Система", "Отзывы"] as const;
type Filter = typeof filters[number];

export default function NotificationsPage() {
  return <PrivatePage><Notifications /></PrivatePage>;
}

function Notifications() {
  const { user } = useSession();
  const chats = useData<Chat[]>("/chats", 5000);
  const reviews = useData<Review[]>(user ? "/profiles/" + encodeURIComponent(user.id) + "/reviews" : "");
  const [filter, setFilter] = useState<Filter>("Все");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const unread = (chats.data ?? []).filter((chat) => chat.unreadCount > 0 && chat.lastMessage);
  const recentReviews = reviews.data ?? [];
  const counts = { Все: unread.length + recentReviews.length, Объявления: 0, Сообщения: unread.length, Система: 0, Отзывы: recentReviews.length };
  const notices = [
    ...unread.map((chat) => ({
      id: chat.id, kind: "Сообщения", title: "Новое сообщение · " + chat.counterpart.name,
      text: chat.lastMessage!.text, time: chat.lastMessage!.sentAt, href: "/messages/" + chat.id,
    })),
    ...recentReviews.map((review) => ({
      id: review.id, kind: "Отзывы", title: "Новый отзыв · " + review.rating + " из 5",
      text: review.text, time: review.createdAt, href: null,
    })),
  ].filter((notice) => filter === "Все" || notice.kind === filter)
    .sort((a, b) => Date.parse(b.time) - Date.parse(a.time));
  const loading = filter === "Сообщения" ? chats.loading : filter === "Отзывы" ? reviews.loading : chats.loading || reviews.loading;
  const loadError = filter === "Сообщения" ? chats.error : filter === "Отзывы" ? reviews.error : chats.error || reviews.error;

  async function markRead() {
    setBusy(true);
    setError("");
    const results = await Promise.allSettled(unread.map((chat) => api("/chats/" + chat.id + "/read", {
      method: "POST", json: { upToSeq: chat.lastMessage!.seq },
    })));
    const failure = results.find((result) => result.status === "rejected");
    if (failure) setError(errorMessage(failure.reason));
    chats.reload();
    setBusy(false);
  }

  return <section className="notifications">
    <h1>Уведомления</h1>
    <p>Следите за важными событиями на платформе</p>
    <div className="actions notice-filters">
      {filters.map((name) => <button type="button" key={name} className={filter === name ? "" : "secondary"}
        aria-pressed={filter === name} onClick={() => setFilter(name)}>
        {name} <span>{name === "Система" || name === "Объявления" ? "—" : counts[name]}</span>
      </button>)}
    </div>
    {filter === "Система" || filter === "Объявления" ? <p>Эти уведомления пока недоступны.</p> : <>
    <RequestStatus loading={loading} error={loadError} retry={() => {
      if (filter !== "Отзывы") chats.reload();
      if (filter !== "Сообщения") reviews.reload();
    }} />
    <RequestStatus error={error} />
    {!loading && !loadError && notices.length === 0 && <p>Уведомлений пока нет.</p>}
    <ul className="activity-list notice-list">
      {notices.map((notice) => <li className={notice.href ? "notice notice-unread" : "notice"} key={notice.kind + notice.id}>
        <span className={notice.href ? "notice-kind notice-kind-message" : "notice-kind notice-kind-review"}>{notice.kind}</span>
        <span className={notice.href ? "notice-icon notice-message" : "notice-icon notice-review"}>
          <img src={notice.href ? "/design/notice-message.svg" : "/design/notice-review.svg"} alt="" />
        </span>
        <div>
          <h2>{notice.href ? <Link href={notice.href}>{notice.title}</Link> : notice.title}</h2>
          {notice.text && <p>{notice.text}</p>}
        </div>
        <time dateTime={notice.time}>{new Date(notice.time).toLocaleString("ru-RU", { dateStyle: "short", timeStyle: "short" })}</time>
      </li>)}
    </ul>
    {unread.length > 0 && filter !== "Отзывы" && <button className="secondary" type="button" disabled={busy} onClick={markRead}>
      Отметить сообщения как прочитанные
    </button>}
    </>}
  </section>;
}
