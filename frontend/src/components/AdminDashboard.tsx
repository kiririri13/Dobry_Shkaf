"use client";

import Link from "next/link";
import { useState, type FormEvent } from "react";
import { api, errorMessage, query } from "@/lib/api";
import { useData } from "@/lib/useData";
import type { Item, ItemPage, Point, Profile } from "@/lib/types";
import AdminPoints from "./AdminPoints";
import RequestStatus from "./RequestStatus";
import { useSession } from "./Session";

const statuses = { PENDING: "На проверке", REVIEWED: "Одобрено", HIDDEN: "Отклонено" };
type ModerationStatus = keyof typeof statuses;
type QueueItem = Item & { moderationStatus: ModerationStatus };
type Queue = Omit<ItemPage, "items"> & { items: QueueItem[] };
type Complaint = {
  id: string;
  reporterId?: string;
  targetType: "ITEM" | "POINT" | "PROFILE" | "MESSAGE" | "CHAT";
  targetId: string;
  reason: string;
  status: "OPEN" | "CLOSED";
  outcome: "UPHELD" | "DISMISSED" | null;
  explanation: string | null;
  createdAt: string;
};

const targets = { ITEM: "Товар", POINT: "Точка обмена", PROFILE: "Профиль", MESSAGE: "Сообщение", CHAT: "Диалог" };
const complaintLinks = { ITEM: "/items/", POINT: "/points/", PROFILE: "/profiles/" };
const complaintActions = { ITEM: "Открыть карточку товара", POINT: "Открыть точку обмена", PROFILE: "Открыть профиль" };
const relativeTime = new Intl.RelativeTimeFormat("ru-RU");

function complaintAge(value: string) {
  const minutes = Math.max(0, Math.floor((Date.now() - Date.parse(value)) / 60_000));
  if (minutes < 1) return "только что";
  if (minutes < 60) return relativeTime.format(-minutes, "minute");
  if (minutes < 1440) return relativeTime.format(-Math.floor(minutes / 60), "hour");
  return relativeTime.format(-Math.floor(minutes / 1440), "day");
}

export default function AdminDashboard() {
  const { user } = useSession();
  const admin = user?.role === "ADMIN";
  const [section, setSection] = useState("Объявления");
  const [itemStatus, setItemStatus] = useState<ModerationStatus | "">("");
  const [changingItem, setChangingItem] = useState(false);
  const active = admin ? section : "Жалобы";
  const pending = useData<Queue>(admin ? "/admin/items?status=PENDING" : "");
  const reviewed = useData<Queue>(admin ? "/admin/items?status=REVIEWED" : "");
  const hidden = useData<Queue>(admin ? "/admin/items?status=HIDDEN" : "");

  if (!admin && user?.role !== "MODERATOR") return <RequestStatus error="Этот раздел доступен администратору или модератору." />;

  const viewClass = active === "Объявления" ? "admin-items-view" : active === "Жалобы" ? "admin-complaints-view" : "";
  return <section className={`admin-dashboard ${viewClass}`}>
    <Link className="admin-back" href="/">Вернуться как обычный пользователь</Link>
    <header className="admin-heading">
      <div><h1>Панель модератора</h1><p>Проверяйте объявления, следите за правилами и помогайте<br />делать платформу безопасной для всех пользователей.</p></div>
      {admin && <dl className="admin-stats">
        {([["PENDING", pending.data?.totalElements], ["REVIEWED", reviewed.data?.totalElements], ["HIDDEN", hidden.data?.totalElements]] as const)
          .map(([status, count]) => <div key={status}>
            <dt>{active === "Объявления" ? <button type="button" disabled={changingItem} aria-pressed={itemStatus === status}
              title={status === "HIDDEN" ? "Показать скрытые объявления" : undefined}
              onClick={() => setItemStatus(status)}>{statuses[status]}</button> : statuses[status]}</dt>
            <dd>{count == null ? "—" : String(count).padStart(2, "0")}</dd>
          </div>)}
      </dl>}
    </header>
    <nav className="profile-tabs" aria-label="Разделы модерации">
      {(admin ? ["Объявления", "Жалобы", "Точки обмена"] : ["Жалобы"]).map((label) =>
        <button type="button" key={label} disabled={changingItem} aria-pressed={active === label} onClick={() => setSection(label)}>{label}</button>)}
    </nav>
    {active === "Объявления" && <ItemQueue key={itemStatus} status={itemStatus} busy={changingItem} onBusy={setChangingItem} onFilter={setItemStatus}
      onChange={() => { pending.reload(); reviewed.reload(); hidden.reload(); }} />}
    {active === "Жалобы" && <ComplaintQueue />}
    {active === "Точки обмена" && <AdminPoints />}
  </section>;
}

function ItemQueue({ status, busy, onBusy, onFilter, onChange }: {
  status: ModerationStatus | "";
  busy: boolean;
  onBusy: (busy: boolean) => void;
  onFilter: (status: ModerationStatus | "") => void;
  onChange: () => void;
}) {
  const [page, setPage] = useState(0);
  const [reasonFor, setReasonFor] = useState<string>();
  const [error, setError] = useState("");
  const queue = useData<Queue>("/admin/items" + query({ status, page }));

  async function change(id: string, action: "review" | "hide" | "restore", reason?: string) {
    if (busy) return;
    onBusy(true);
    setError("");
    try {
      await api("/admin/items/" + encodeURIComponent(id) + "/" + action, {
        method: "POST", json: reason ? { reason } : undefined,
      });
      setReasonFor(undefined);
      if (page > 0 && queue.data?.items.length === 1 && status) setPage(page - 1);
      else queue.reload();
      onChange();
    } catch (failure) {
      setError(errorMessage(failure));
    } finally {
      onBusy(false);
    }
  }

  return <section className="admin-tab" aria-label="Объявления">
    <div className="actions admin-filters">
      {([["", "Все"], ["PENDING", "Новые"]] as const).map(([value, label]) =>
        <button type="button" className={status === value ? "" : "secondary"} key={value} disabled={busy}
          onClick={() => onFilter(value)} aria-pressed={status === value}>{label}</button>)}
    </div>
    <RequestStatus loading={queue.loading} error={queue.error} retry={queue.reload} />
    <RequestStatus error={error} />
    {queue.data?.items.length === 0 && <p>Объявлений с таким статусом нет.</p>}
    <div className="cards moderation-cards">
      {queue.data?.items.map((item) => <article className="card" key={item.id}>
        <Link className="card-image" href={"/items/" + item.id} aria-label={item.title}>
          {item.photos[0] && <img src={item.photos[0].url} alt="" />}
        </Link>
        {item.moderationStatus !== "HIDDEN" && <button type="button" className="moderation-hide" disabled={busy}
          aria-label={`Скрыть объявление «${item.title}»`} title="Скрыть объявление"
          onClick={() => setReasonFor(item.id)}>×</button>}
        {item.moderationStatus === "PENDING"
          ? <button type="button" className="moderation-approve" disabled={busy} onClick={() => change(item.id, "review")}>Одобрить</button>
          : item.moderationStatus === "HIDDEN"
            ? <button type="button" className="moderation-approve" disabled={busy} onClick={() => change(item.id, "restore")}>Восстановить</button>
            : <span className="badge moderation-status">{statuses[item.moderationStatus]}</span>}
        <div className="card-body">
          <h2>{item.title}</h2>
          {item.district && <p><img src="/design/pin.svg" alt="" />{item.district.name}</p>}
          {item.size && <p>Размер: {item.size}</p>}
          {reasonFor === item.id && <form onSubmit={(event: FormEvent<HTMLFormElement>) => {
            event.preventDefault();
            const reason = String(new FormData(event.currentTarget).get("reason")).trim();
            if (reason) void change(item.id, "hide", reason);
          }}>
            <label>Причина<textarea name="reason" required maxLength={1000} rows={2} /></label>
            <div className="actions"><button disabled={busy}>Подтвердить</button><button type="button" className="secondary" disabled={busy} onClick={() => setReasonFor(undefined)}>Отмена</button></div>
          </form>}
        </div>
      </article>)}
    </div>
    {queue.data && queue.data.totalPages > 1 && <div className="actions pagination">
      <button className="secondary" disabled={page === 0 || busy} onClick={() => setPage(page - 1)}>Назад</button>
      <span>{page + 1} / {queue.data.totalPages}</span>
      <button className="secondary" disabled={page + 1 >= queue.data.totalPages || busy} onClick={() => setPage(page + 1)}>Далее</button>
    </div>}
  </section>;
}

function ComplaintQueue() {
  const [openOnly, setOpenOnly] = useState(false);
  const [page, setPage] = useState(0);
  const [selected, setSelected] = useState<{ id: string; outcome: "UPHELD" | "DISMISSED" }>();
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState("");
  const list = useData<Complaint[]>("/admin/complaints" + query({ status: openOnly ? "OPEN" : "", page }));

  async function decide(event: FormEvent<HTMLFormElement>, id: string) {
    event.preventDefault();
    if (busy || selected?.id !== id) return;
    const values = new FormData(event.currentTarget);
    const explanation = String(values.get("explanation")).trim();
    if (!explanation) return;
    setBusy(true);
    setError("");
    try {
      await api("/admin/complaints/" + encodeURIComponent(id) + "/close", {
        method: "POST", json: { outcome: selected.outcome, explanation },
      });
      setSelected(undefined);
      if (openOnly && page > 0 && list.data?.length === 1) setPage(page - 1);
      else list.reload();
    } catch (failure) {
      setError(errorMessage(failure));
    } finally {
      setBusy(false);
    }
  }

  return <section className="admin-tab" aria-label="Жалобы">
    <div className="actions admin-filters">
      {[false, true].map((value) => <button type="button" className={openOnly === value ? "" : "secondary"} key={String(value)}
        aria-pressed={openOnly === value} disabled={busy} onClick={() => { setOpenOnly(value); setPage(0); setSelected(undefined); setError(""); }}>
        {value ? "Новые" : "Все"}
      </button>)}
    </div>
    <RequestStatus loading={list.loading} error={list.error} retry={list.reload} />
    <RequestStatus error={error} />
    {list.data?.length === 0 && <p>Жалоб пока нет.</p>}
    <ul className="activity-list admin-complaints">
      {list.data?.map((complaint) => <li className="panel admin-complaint" key={complaint.id}>
        <ComplaintSubject complaint={complaint} />
        <ComplaintReporter complaint={complaint} />
        <div className="admin-complaint-actions">
          {(complaint.targetType === "ITEM" || complaint.targetType === "POINT" || complaint.targetType === "PROFILE") &&
            <Link href={complaintLinks[complaint.targetType] + encodeURIComponent(complaint.targetId)}>{complaintActions[complaint.targetType]}</Link>}
          {complaint.status === "OPEN" && <div className="actions">
            <button type="button" disabled={busy} onClick={() => setSelected({ id: complaint.id, outcome: "UPHELD" })}>Одобрить</button>
            <button type="button" disabled={busy} onClick={() => setSelected({ id: complaint.id, outcome: "DISMISSED" })}>Отклонить</button>
          </div>}
        </div>
        {selected?.id === complaint.id && <form onSubmit={(event) => decide(event, complaint.id)}>
          <label>Комментарий к решению<textarea name="explanation" required maxLength={1000} rows={2} /></label>
          <div className="actions">
            <button disabled={busy}>{selected.outcome === "UPHELD" ? "Подтвердить одобрение" : "Подтвердить отклонение"}</button>
            <button type="button" className="secondary" disabled={busy} onClick={() => setSelected(undefined)}>Отмена</button>
          </div>
        </form>}
      </li>)}
    </ul>
    {list.data && (page > 0 || list.data.length === 20) && <div className="actions pagination">
      <button className="secondary" disabled={page === 0 || busy} onClick={() => { setSelected(undefined); setPage(page - 1); }}>Назад</button>
      <button className="secondary" disabled={list.data.length < 20 || busy} onClick={() => { setSelected(undefined); setPage(page + 1); }}>Далее</button>
    </div>}
  </section>;
}

function ComplaintSubject({ complaint }: { complaint: Complaint }) {
  const { targetType, targetId, createdAt } = complaint;
  const path = targetType === "ITEM" || targetType === "POINT" || targetType === "PROFILE"
    ? complaintLinks[targetType] + encodeURIComponent(targetId) : "";
  const { data } = useData<Item | Point | Profile>(path);
  const title = data && ("title" in data ? data.title : data.name);
  const photo = data && ("photos" in data ? data.photos[0]?.url : "address" in data ? data.photoUrl : data.avatarUrl);
  const address = data && ("address" in data ? data.address : "title" in data ? data.district?.name : undefined);
  const rating = data && "completedTransfers" in data ? data.rating : null;

  return <>
    <div className="admin-complaint-target" aria-hidden="true">
      {targetType === "PROFILE" ? <span>{photo && <img src={photo} alt="" />}</span> : photo && <img src={photo} alt="" />}
    </div>
    <div className="admin-complaint-details">
      <h2>{targets[targetType]}</h2>
      {title && <p className="admin-complaint-name">{title}</p>}
      {address && <p><img src="/design/pin.svg" alt="" />{address}</p>}
      {rating != null && <p><img src="/design/profile-rating.svg" alt="Рейтинг" />{rating.toLocaleString("ru-RU", { minimumFractionDigits: 1, maximumFractionDigits: 1 })}</p>}
      <p><img src="/design/clock.svg" alt="" />Жалоба поступила <time dateTime={createdAt}
        title={new Date(createdAt).toLocaleString("ru-RU")}>{complaintAge(createdAt)}</time></p>
      {complaint.status === "CLOSED" && <p>{complaint.outcome === "UPHELD" ? "Одобрено" : "Отклонено"} · {complaint.explanation}</p>}
    </div>
  </>;
}

function ComplaintReporter({ complaint }: { complaint: Complaint }) {
  const path = complaint.reporterId ? "/profiles/" + encodeURIComponent(complaint.reporterId) : "";
  const { data } = useData<Profile>(path);
  return <div className="admin-complaint-reporter">
    <div>
      <span aria-hidden="true">{data?.avatarUrl && <img src={data.avatarUrl} alt="" />}</span>
      {path ? <Link href={path} prefetch={false}>{data?.name || "Автор жалобы"}</Link> : "Автор жалобы"}
    </div>
    <p>{complaint.reason}</p>
  </div>;
}
