"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { useState } from "react";
import RequestStatus from "@/components/RequestStatus";
import { PrivatePage } from "@/components/Session";
import { api, errorMessage } from "@/lib/api";
import { useData } from "@/lib/useData";
import type { Chat, Review } from "@/lib/types";

const filters = ["Все", "Объявления", "Сообщения", "Система", "Отзывы"] as const;
type Filter = typeof filters[number];
type Kind = Exclude<Filter, "Все">;

const events = {
  ITEM_PUBLISHED: ["Объявления", "Вы успешно добавили новое объявление", "/items/"],
  ITEM_UNPUBLISHED: ["Объявления", "Ваше объявление снято с публикации", "/items/"],
  ITEM_FAVORITED: ["Объявления", "Ваше объявление добавили в избранное", "/items/"],
  ITEM_SUBMITTED: ["Система", "Ваше объявление отправлено на проверку", "/items/"],
  ITEM_REVIEWED: ["Система", "Ваше объявление одобрено", "/items/"],
  ITEM_HIDDEN: ["Система", "Ваше объявление скрыто", "/items/"],
  ITEM_RESTORED: ["Система", "Ваше объявление возвращено на проверку", "/items/"],
  ACCOUNT_BLOCKED: ["Система", "Ваш аккаунт заблокирован"],
  ITEM_REPORTED: ["Система", "На ваше объявление пожаловались", "/items/"],
  PROFILE_REPORTED: ["Система", "На вас пожаловались"],
  COMPLAINT_SUBMITTED: ["Система", "Ваша жалоба отправлена"],
  COMPLAINT_UPHELD: ["Система", "Ваша жалоба удовлетворена"],
  COMPLAINT_DISMISSED: ["Система", "Ваша жалоба отклонена"],
  COMPLAINT_REOPENED: ["Система", "Вашу жалобу снова рассматривают"],
  REVIEW_RECEIVED: ["Отзывы", "На ваш профиль добавлен новый отзыв"],
  REVIEW_HIDDEN: ["Отзывы", "Ваш отзыв скрыт"],
} as const satisfies Record<string, readonly [Kind, string, string?]>;

type NotificationFeed = { items: NotificationEntry[]; unreadCount: number; hasMore: boolean };
type NotificationEntry = {
  id: string;
  type: keyof typeof events;
  entityId: string;
  detail: string | null;
  createdAt: string;
  readAt: string | null;
};

type Notice = {
  id: string;
  kind: Kind;
  title: string;
  text: string | null;
  time: string;
  unread: boolean;
  href?: string;
  rating?: number;
  alert?: boolean;
};

const icons = {
  Объявления: ["listing", "box.svg"],
  Сообщения: ["message", "notice-message.svg"],
  Система: ["system", "notice-system.svg"],
  Отзывы: ["review", "notice-review.svg"],
} as const;

const dateFormat = new Intl.DateTimeFormat("ru-RU", { dateStyle: "short" });
const timeFormat = new Intl.DateTimeFormat("ru-RU", { timeStyle: "short" });

function noticeTime(value: string) {
  const date = new Date(value);
  const formatted = dateFormat.format(date);
  return (formatted === dateFormat.format(new Date()) ? "Сегодня" : formatted) + ", " + timeFormat.format(date);
}

function showEvent(entry: NotificationEntry, review?: Review): Notice {
  const [kind, title, path] = events[entry.type] ?? ["Система", "Уведомление"];
  return {
    id: entry.id,
    kind,
    title,
    text: entry.detail ?? review?.text ?? null,
    time: entry.createdAt,
    unread: !entry.readAt,
    href: path ? path + encodeURIComponent(entry.entityId) : entry.type === "REVIEW_RECEIVED" ? "/profile?section=reviews"
      : entry.type === "PROFILE_REPORTED" ? "/profile" : undefined,
    rating: review?.rating,
    alert: ["ITEM_HIDDEN", "ITEM_REPORTED", "PROFILE_REPORTED", "ACCOUNT_BLOCKED", "COMPLAINT_DISMISSED", "REVIEW_HIDDEN"].includes(entry.type),
  };
}

export default function NotificationsPage() {
  return <PrivatePage><Notifications /></PrivatePage>;
}

function Notifications() {
  const router = useRouter();
  const feed = useData<NotificationFeed>("/notifications");
  const chats = useData<Chat[]>("/chats", 5000);
  const [older, setOlder] = useState<{ items: NotificationEntry[]; page: number; hasMore: boolean }>();
  const [marked, setMarked] = useState<string[]>([]);
  const [filter, setFilter] = useState<Filter>("Все");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const unreadChats = (chats.data ?? []).flatMap((chat) => chat.unreadCount && chat.lastMessage
    ? [{ id: chat.id, name: chat.counterpart.name, message: chat.lastMessage }] : []);
  const entries = Array.from(new Map([...(feed.data?.items ?? []), ...(older?.items ?? [])].map((entry) => [entry.id, entry])).values());
  const reviews = useData<Review[]>(entries.some((entry) => entry.type === "REVIEW_RECEIVED") ? "/profile/me/reviews" : "");
  const notices: Notice[] = [
    ...entries.map((entry) => {
      const review = entry.type === "REVIEW_RECEIVED" ? reviews.data?.find(({ id }) => id === entry.entityId) : undefined;
      const notice = showEvent(entry, review);
      return marked.includes(entry.id) ? { ...notice, unread: false } : notice;
    }),
    ...unreadChats.map(({ id, name, message }): Notice => ({
      id, kind: "Сообщения", title: "Вам написали новое сообщение", unread: true,
      text: name + ": " + (message.text || message.attachment?.name || "Вложение"),
      time: message.sentAt, href: "/messages/" + encodeURIComponent(id),
    })),
  ];
  const total = Math.max(0, (feed.data?.unreadCount ?? 0) - marked.length) + unreadChats.length;
  const visible = notices.filter((notice) => filter === "Все" || notice.kind === filter)
    .sort((a, b) => Date.parse(b.time) - Date.parse(a.time));
  const loading = (filter !== "Сообщения" && feed.loading) || ((filter === "Все" || filter === "Сообщения") && chats.loading);
  const loadError = (filter !== "Сообщения" && feed.error) || ((filter === "Все" || filter === "Сообщения") && chats.error) || undefined;
  const hasMore = filter !== "Сообщения" && (older?.hasMore ?? feed.data?.hasMore);

  function refresh() {
    setOlder(undefined);
    setMarked([]);
    feed.reload();
    chats.reload();
  }

  async function loadMore() {
    setBusy(true);
    setError("");
    try {
      const page = (older?.page ?? 0) + 1;
      const result = await api<NotificationFeed>("/notifications?page=" + page);
      setOlder({ page, hasMore: result.hasMore, items: [...(older?.items ?? []), ...result.items] });
    } catch (failure) {
      setError(errorMessage(failure));
    } finally {
      setBusy(false);
    }
  }

  async function openNotice(notice: Notice) {
    if (busy) return;
    setBusy(true);
    setError("");
    try {
      await api("/notifications/" + encodeURIComponent(notice.id) + "/read", { method: "PUT" });
      setMarked((previous) => [...previous, notice.id]);
      if (notice.href) router.push(notice.href);
    } catch (failure) {
      setError(errorMessage(failure));
    } finally {
      setBusy(false);
    }
  }

  async function markAll() {
    setBusy(true);
    setError("");
    try {
      const results = await Promise.allSettled([
        api("/notifications/read-all", { method: "PUT" }),
        api<Chat[]>("/chats").then(async (latestChats) => {
          const chatResults = await Promise.allSettled(latestChats.flatMap(({ id, unreadCount, lastMessage }) => unreadCount && lastMessage
            ? [api("/chats/" + encodeURIComponent(id) + "/read", { method: "POST", json: { upToSeq: lastMessage.seq } })] : []));
          const failure = chatResults.find((result) => result.status === "rejected");
          if (failure) throw failure.reason;
        }),
      ]);
      const failure = results.find((result) => result.status === "rejected");
      if (failure) setError(errorMessage(failure.reason));
      refresh();
    } finally {
      setBusy(false);
    }
  }

  return <section className="notifications">
    <h1>Уведомления</h1>
    <p>Следите за важными событиями на платформе</p>
    <div className="actions notice-filters">
      {filters.map((name) => <button type="button" key={name} className={filter === name ? "" : "secondary"}
        aria-pressed={filter === name} onClick={() => setFilter(name)}>
        {name} <span>{name === "Все" ? total : notices.filter((notice) => notice.kind === name && notice.unread).length}</span>
      </button>)}
    </div>
    <RequestStatus loading={loading} error={loadError} retry={refresh} />
    <RequestStatus error={error} />
    {!loading && !loadError && visible.length === 0 && <p>{hasMore ? "В загруженных уведомлениях этой категории пока нет." : "Уведомлений пока нет."}</p>}
    <ul className="activity-list notice-list">
      {visible.map((notice) => {
        const [icon, asset] = icons[notice.kind];
        const title = <>{notice.title}{notice.href && <img className="notice-arrow" src="/design/notice-arrow.svg" alt="" />}</>;
        return <li className={"notice" + (notice.unread ? " notice-unread" : "") + (notice.alert ? " notice-alert" : "")} key={notice.kind + notice.id}>
          <span className={"notice-kind notice-kind-" + icon}>{notice.kind}</span>
          <span className={"notice-icon notice-" + icon}><img src={"/design/" + asset} alt="" /></span>
          <div className="notice-content">
            <div className="notice-heading">
              <h2>
                {notice.href ? <Link href={notice.href} onNavigate={notice.kind !== "Сообщения" && notice.unread ? (event) => {
                  event.preventDefault();
                  void openNotice(notice);
                } : undefined}>{title}</Link>
                  : notice.unread ? <button type="button" disabled={busy} onClick={() => openNotice(notice)}>
                    {title}<span className="visually-hidden"> — отметить прочитанным</span>
                  </button> : title}
              </h2>
              {notice.rating !== undefined && <span className="notice-rating">
                <span className="visually-hidden">{notice.rating} из 5</span>
                {Array.from({ length: notice.rating }, (_, index) => <img key={index} src="/design/notice-rating-star.svg" alt="" />)}
              </span>}
            </div>
            {notice.text && <p>{notice.text}</p>}
          </div>
          <time dateTime={notice.time}>{noticeTime(notice.time)}</time>
        </li>;
      })}
    </ul>
    <div className="actions notice-actions">
      {total > 0 && <button className="secondary notice-mark-read" type="button" disabled={busy} onClick={markAll}>Отметить все как прочитанные</button>}
      {hasMore && <button className="secondary notice-mark-read" type="button" disabled={busy} onClick={loadMore}>Показать еще</button>}
    </div>
  </section>;
}
